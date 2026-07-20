package io.kestra.plugin.clevercloud.addons;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
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

import java.time.Instant;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get details of a Clever Cloud add-on",
    description = """
        Retrieves a single add-on by its ID: plan, provider, region, and provisioning date.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch an add-on in an organisation",
            full = true,
            code = """
                id: get_addon
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.clevercloud.addons.Get
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    addonId: "addon_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class Get extends AbstractCleverCloudConnection implements RunnableTask<Get.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "Add-on ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> addonId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAddonId = runContext.render(addonId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("addonId is required")
        );

        var url = resourceUrl(baseUrl(), rOrgId, "addons/" + encodeSegment(rAddonId));

        logger.info("Fetching add-on {}", rAddonId);
        var body = makeCall(runContext, buildGetRequest(url));
        var addon = MAPPER.readValue(body, Addon.class);

        var provider = addon.getProvider();
        var plan = addon.getPlan();
        return Output.builder()
            .id(addon.getId())
            .name(addon.getName())
            .realId(addon.getRealId())
            .region(addon.getRegion())
            .providerId(provider != null ? provider.getId() : null)
            .providerName(provider != null ? provider.getName() : null)
            .planId(plan != null ? plan.getId() : null)
            .planName(plan != null ? plan.getName() : null)
            .creationDate(addon.getCreationDate() != null ? Instant.ofEpochMilli(addon.getCreationDate()) : null)
            .configKeys(addon.getConfigKeys())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Add-on ID")
        private final String id;

        @Schema(title = "Add-on name")
        private final String name;

        @Schema(title = "Underlying real resource ID of the add-on")
        private final String realId;

        @Schema(title = "Deployment region, e.g. par, rbx")
        private final String region;

        @Schema(title = "ID of the add-on provider, e.g. postgresql-addon")
        private final String providerId;

        @Schema(title = "Display name of the add-on provider")
        private final String providerName;

        @Schema(title = "ID of the subscribed price plan")
        private final String planId;

        @Schema(title = "Display name of the subscribed price plan")
        private final String planName;

        @Schema(title = "Date the add-on was provisioned")
        private final Instant creationDate;

        @Schema(title = "Environment variable names exposed by this add-on, without their values")
        private final List<String> configKeys;
    }
}
