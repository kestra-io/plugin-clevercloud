package io.kestra.plugin.clevercloud.addons;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.addons.model.Addon;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger when a new Clever Cloud add-on is provisioned",
    description = """
        Polls the add-on list for an organisation or personal account at each interval and fires
        an execution when a new add-on appears, detected by diffing the add-on ID set against the
        one observed on the previous evaluation.

        The first evaluation only stores the current add-on set as a baseline and does not fire,
        so pre-existing add-ons never trigger an execution on trigger startup.

        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fire when a new add-on is provisioned in an organisation",
            full = true,
            code = """
                id: on_addon_provisioned
                namespace: company.team

                triggers:
                  - id: watch_addons
                    type: io.kestra.plugin.clevercloud.addons.AddonProvisionedTrigger
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    interval: PT1M

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "New add-on provisioned: {{ trigger.addonId }} ({{ trigger.providerId }})"
                """
        )
    }
)
public class AddonProvisionedTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<AddonProvisionedTrigger.Output> {

    @NotNull
    @Schema(title = "API token", description = "Bearer token for the Clever Cloud API. Store as a Kestra secret and reference with {{ secret('CC_API_TOKEN') }}.")
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> apiToken;

    @Schema(title = "HTTP client options", description = "Optional HttpConfiguration applied to every Clever Cloud API call, including timeouts and proxy settings.")
    @PluginProperty(group = "advanced")
    HttpConfiguration options;

    protected String baseUrl() {
        return AbstractCleverCloudConnection.DEFAULT_BASE_URL;
    }

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(
        title = "How often to check for new add-ons",
        description = "ISO-8601 duration. Minimum PT30S is recommended to avoid overloading the API. Defaults to PT1M."
    )
    @PluginProperty(group = "reliability")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(1);

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rApiToken = runContext.render(apiToken).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);

        var url = AbstractCleverCloudConnection.resourceUrl(baseUrl(), rOrgId, "addons");

        logger.debug("Polling add-ons for {}", rOrgId != null ? "organisation " + rOrgId : "personal account");

        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("GET");
        var body = AbstractCleverCloudConnection.makeCall(runContext, options, requestBuilder, rApiToken, String.class);

        var addons = AbstractCleverCloudConnection.MAPPER.readValue(
            body != null ? body : "[]", new TypeReference<ArrayList<Addon>>() {});

        var currentIds = addons.stream()
            .filter(a -> a.getId() != null)
            .map(Addon::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // Key includes flowId so triggers sharing a triggerId across flows don't clobber each other's baseline.
        var kvKey = "addon-trigger-" + context.getFlowId() + "-" + context.getTriggerId() + "-" + (rOrgId != null ? rOrgId : "self");
        var kv = runContext.namespaceKv(context.getNamespace());

        // Refreshed every poll while the trigger is active, so a 10x-interval TTL never expires
        // a live baseline but ages out an orphaned entry a few polls after the trigger stops.
        var baselineTtl = interval.multipliedBy(10);

        var previousIdsOptional = kv.getValue(kvKey);

        if (previousIdsOptional.isEmpty()) {
            logger.info("Establishing add-on baseline for {} ({} add-on(s))",
                rOrgId != null ? "organisation " + rOrgId : "personal account", currentIds.size());
            persistIds(kv, kvKey, currentIds, baselineTtl);
            return Optional.empty();
        }

        var previousIds = deserializeIds(previousIdsOptional.get().value());

        var newIds = new LinkedHashSet<>(currentIds);
        newIds.removeAll(previousIds);

        // Always persist the latest add-on set so the next poll diffs against it.
        if (!currentIds.equals(previousIds)) {
            persistIds(kv, kvKey, currentIds, baselineTtl);
        }

        if (newIds.isEmpty()) {
            return Optional.empty();
        }

        var newAddons = addons.stream().filter(a -> newIds.contains(a.getId())).toList();
        var first = newAddons.getFirst();
        var provider = first.getProvider();
        var plan = first.getPlan();

        logger.info("New add-on(s) provisioned: {}", newIds);

        var output = Output.builder()
            .addonIds(List.copyOf(newIds))
            .addonId(first.getId())
            .name(first.getName())
            .providerId(provider != null ? provider.getId() : null)
            .planId(plan != null ? plan.getId() : null)
            .region(first.getRegion())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private static void persistIds(KVStore kv, String kvKey, Set<String> ids, Duration ttl) throws Exception {
        var value = AbstractCleverCloudConnection.MAPPER.writeValueAsString(ids);
        kv.put(kvKey, new KVValueAndMetadata(new KVMetadata(null, ttl), value));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> deserializeIds(Object value) throws Exception {
        if (value instanceof String s) {
            return AbstractCleverCloudConnection.MAPPER.readValue(s, new TypeReference<LinkedHashSet<String>>() {});
        }
        if (value instanceof List<?> list) {
            return new LinkedHashSet<>((List<String>) list);
        }
        return new LinkedHashSet<>();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "IDs of the add-ons that newly appeared since the last evaluation")
        private final List<String> addonIds;

        @Schema(title = "ID of the first newly provisioned add-on")
        private final String addonId;

        @Schema(title = "Name of the first newly provisioned add-on")
        private final String name;

        @Schema(title = "ID of the provider of the first newly provisioned add-on")
        private final String providerId;

        @Schema(title = "ID of the plan of the first newly provisioned add-on")
        private final String planId;

        @Schema(title = "Deployment region of the first newly provisioned add-on")
        private final String region;
    }
}
