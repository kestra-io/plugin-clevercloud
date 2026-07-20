package io.kestra.plugin.clevercloud.addons;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.addons.model.Addon;
import io.kestra.plugin.clevercloud.addons.model.AddonProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create (provision) a Clever Cloud add-on",
    description = """
        Provisions a new add-on (database, cache, or other managed service) in the given
        organisation or personal account. providerId, plan, and region are required by the
        Clever Cloud API: find valid values for your account with the Clever Cloud console or
        the public products catalog at https://api.clever-cloud.com/v2/products/addonproviders.
        Provisioning is synchronous: the task returns once the add-on is created and usable.
        Use addons.LinkToApplication afterwards to attach it to an application.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Provision a PostgreSQL add-on in an organisation",
            full = true,
            code = """
                id: create_addon
                namespace: company.team

                tasks:
                  - id: create
                    type: io.kestra.plugin.clevercloud.addons.Create
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    providerId: postgresql-addon
                    plan: dev
                    region: par
                    name: my-postgres
                """
        )
    }
)
public class Create extends AbstractCleverCloudConnection implements RunnableTask<Create.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(
        title = "Add-on provider ID",
        description = "E.g. postgresql-addon, redis-addon, es-addon. List available providers with the Clever Cloud console."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> providerId;

    @Schema(
        title = "Price plan slug or ID",
        description = """
            Accepts a plan slug (e.g. free, dev, xs_sml) or a raw plan ID (plan_...). Slugs are
            resolved against the provider's price plan catalog. When omitted, the cheapest plan
            available for the chosen provider is used, matching the behavior of the Clever Cloud
            CLI (clever addon create --plan free).
            """
    )
    @PluginProperty(group = "main")
    private Property<String> plan;

    @Schema(
        title = "Region to provision the add-on in",
        description = "E.g. par, rbx, mtl. Valid regions depend on the chosen provider."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> region;

    @Schema(
        title = "Add-on name",
        description = "Display name of the add-on. When omitted, the API assigns a default name."
    )
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "Add-on version",
        description = "Version identifier of the underlying service, e.g. a PostgreSQL major version. When omitted, the provider's default version is used."
    )
    @PluginProperty(group = "execution")
    private Property<String> addonVersion;

    private static final String PLAN_ID_PREFIX = "plan_";
    private static final String PROVIDERS_CATALOG_PATH = "products/addonproviders";
    // The catalog is public data hosted directly on the Clever Cloud API; used as a fallback when
    // the plugin's configured base URL (e.g. api-bridge) does not expose it.
    private static final String PROVIDERS_CATALOG_FALLBACK_URL = "https://api.clever-cloud.com/v2/products/addonproviders";

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rProviderId = runContext.render(providerId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("providerId is required")
        );
        var rRegion = runContext.render(region).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("region is required")
        );
        var rPlanInput = runContext.render(plan).as(String.class).orElse(null);

        var rPlanId = resolvePlanId(runContext, rProviderId, rPlanInput);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("providerId", rProviderId);
        payload.put("plan", rPlanId);
        payload.put("region", rRegion);
        runContext.render(name).as(String.class).ifPresent(v -> payload.put("name", v));
        runContext.render(addonVersion).as(String.class).ifPresent(v -> payload.put("version", v));

        var url = resourceUrl(baseUrl(), rOrgId, "addons");

        logger.info("Provisioning {} add-on ({} plan) in {} for {}", rProviderId, rPlanId, rRegion,
            rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildPostRequest(url, payload));
        var addon = MAPPER.readValue(body, Addon.class);

        logger.info("Provisioned add-on {}", addon.getId());

        var provider = addon.getProvider();
        var addonPlan = addon.getPlan();
        return Output.builder()
            .id(addon.getId())
            .name(addon.getName())
            .region(addon.getRegion())
            .providerId(provider != null ? provider.getId() : rProviderId)
            .planId(addonPlan != null ? addonPlan.getId() : null)
            .creationDate(addon.getCreationDate() != null ? Instant.ofEpochMilli(addon.getCreationDate()) : null)
            .build();
    }

    /**
     * Resolves a user-facing plan slug (or omitted plan) to the plan_... id required by the API.
     * A value already looking like a raw plan id is passed through as-is, without a catalog lookup.
     */
    private String resolvePlanId(RunContext runContext, String rProviderId, String rPlanInput) throws Exception {
        if (rPlanInput != null && rPlanInput.startsWith(PLAN_ID_PREFIX)) {
            return rPlanInput;
        }

        var provider = fetchProviderCatalog(runContext).stream()
            .filter(p -> rProviderId.equals(p.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown add-on provider '" + rProviderId + "': check providerId against the Clever Cloud "
                    + "console or " + PROVIDERS_CATALOG_FALLBACK_URL
            ));
        var plans = provider.getPlans() != null ? provider.getPlans() : List.<AddonProvider.Plan>of();

        if (rPlanInput == null || rPlanInput.isBlank()) {
            var cheapest = plans.stream()
                .min(Comparator.comparing(p -> p.getPrice() != null ? p.getPrice() : Double.MAX_VALUE))
                .orElseThrow(() -> new IllegalArgumentException(
                    "No price plan is available for provider '" + rProviderId + "'"
                ));
            runContext.logger().info("No plan specified, defaulting to cheapest plan '{}' ({}) for provider '{}'",
                cheapest.getSlug(), cheapest.getId(), rProviderId);
            return cheapest.getId();
        }

        return plans.stream()
            .filter(p -> rPlanInput.equalsIgnoreCase(p.getSlug()) || rPlanInput.equalsIgnoreCase(p.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown plan '" + rPlanInput + "' for provider '" + rProviderId + "'. Available plans: "
                    + plans.stream().map(AddonProvider.Plan::getSlug).collect(Collectors.joining(", "))
            ))
            .getId();
    }

    private List<AddonProvider> fetchProviderCatalog(RunContext runContext) throws Exception {
        try {
            var body = makeCall(runContext, buildGetRequest(join(baseUrl(), PROVIDERS_CATALOG_PATH)));
            if (body != null && !body.isBlank()) {
                return List.of(MAPPER.readValue(body, AddonProvider[].class));
            }
        } catch (Exception e) {
            runContext.logger().debug("Add-on provider catalog not reachable via {}: {}, falling back to {}",
                baseUrl(), e.getMessage(), PROVIDERS_CATALOG_FALLBACK_URL);
        }

        try (var client = new HttpClient(runContext, getOptions())) {
            var response = client.request(
                HttpRequest.builder().uri(URI.create(PROVIDERS_CATALOG_FALLBACK_URL)).method("GET").build(),
                String.class
            );
            return List.of(MAPPER.readValue(response.getBody(), AddonProvider[].class));
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "ID of the created add-on")
        private final String id;

        @Schema(title = "Add-on name")
        private final String name;

        @Schema(title = "Deployment region")
        private final String region;

        @Schema(title = "ID of the add-on provider")
        private final String providerId;

        @Schema(title = "ID of the subscribed price plan")
        private final String planId;

        @Schema(title = "Date the add-on was provisioned")
        private final Instant creationDate;
    }
}
