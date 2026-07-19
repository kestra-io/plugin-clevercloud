package io.kestra.plugin.clevercloud.addons;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Unlink a Clever Cloud add-on from an application",
    description = """
        Detaches an add-on from an application. The add-on itself is not deleted and remains
        available to link to another application; use addons.Delete to remove it entirely.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Unlink an add-on from an application",
            full = true,
            code = """
                id: unlink_addon
                namespace: company.team

                tasks:
                  - id: unlink
                    type: io.kestra.plugin.clevercloud.addons.UnlinkFromApplication
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    addonId: "addon_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class UnlinkFromApplication extends AbstractCleverCloudConnection implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Schema(title = "Add-on ID to unlink from the application")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> addonId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );
        var rAddonId = runContext.render(addonId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("addonId is required")
        );

        var url = resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId) + "/addons/" + encodeSegment(rAddonId));

        logger.info("Unlinking add-on {} from application {}", rAddonId, rAppId);
        makeCall(runContext, buildDeleteRequest(url));

        return null;
    }
}
