package io.kestra.plugin.clevercloud.logs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
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
    title = "Delete a Clever Cloud log drain",
    description = """
        Deletes a log drain via DELETE /v4/drains/organisations/{organisationId}/applications/{applicationId}/drains/{drainId}.

        organisationId is always required here: unlike the rest of this plugin, the v4 drains API
        has no /self shortcut for personal accounts.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a log drain",
            full = true,
            code = """
                id: teardown_log_drain
                namespace: company.team

                tasks:
                  - id: delete_drain
                    type: io.kestra.plugin.clevercloud.logs.DeleteDrain
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    drainId: "{{ outputs.create_drain.id }}"
                """
        )
    }
)
public class DeleteDrain extends AbstractLogsConnection implements RunnableTask<VoidOutput> {

    @NotNull
    @Schema(title = "Log drain ID to delete")
    @PluginProperty(group = "main")
    private Property<String> drainId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(getOrganisationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required")
        );
        var rAppId = runContext.render(getApplicationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );
        var rDrainId = runContext.render(drainId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("drainId is required")
        );

        var url = join(drainsUrl(baseUrlV4(), rOrgId, rAppId), encodeSegment(rDrainId));

        logger.info("Deleting log drain {} for application {}", rDrainId, rAppId);
        makeCall(runContext, buildDeleteRequest(url));

        return null;
    }
}
