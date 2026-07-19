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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger when a new Clever Cloud add-on is provisioned",
    description = """
        Polls the add-on list for an organisation or personal account at each interval and fires
        an execution for the most recently provisioned add-on.

        The Clever Cloud API provisions add-ons synchronously: there is no "pending" or "READY"
        status field on an add-on (unlike application deployments, which do have a WIP/OK/FAIL
        state). By the time an add-on appears in the list, it is already usable. This trigger
        therefore detects newly provisioned add-ons by their creationDate rather than by a status
        transition: only add-ons created strictly after the previous evaluation are considered.

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

        // Dedup: only add-ons created strictly after the previous evaluation are new.
        // context.getDate() is the ZonedDateTime of the previous evaluation (always present).
        Instant cutoff = context.getDate().toInstant();

        var newAddons = addons.stream()
            .filter(a -> a.getCreationDate() != null && Instant.ofEpochMilli(a.getCreationDate()).isAfter(cutoff))
            .sorted((a, b) -> Long.compare(b.getCreationDate(), a.getCreationDate()))
            .toList();

        if (newAddons.isEmpty()) {
            return Optional.empty();
        }

        if (newAddons.size() > 1) {
            logger.warn(
                "Found {} newly provisioned add-ons in this poll, only the most recent will fire the trigger, the others are skipped",
                newAddons.size()
            );
        }

        var addon = newAddons.getFirst();
        var provider = addon.getProvider();
        logger.info("Add-on {} was provisioned", addon.getId());

        var output = Output.builder()
            .addonId(addon.getId())
            .name(addon.getName())
            .providerId(provider != null ? provider.getId() : null)
            .region(addon.getRegion())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "ID of the newly provisioned add-on")
        private final String addonId;

        @Schema(title = "Add-on name")
        private final String name;

        @Schema(title = "ID of the add-on provider")
        private final String providerId;

        @Schema(title = "Deployment region")
        private final String region;
    }
}
