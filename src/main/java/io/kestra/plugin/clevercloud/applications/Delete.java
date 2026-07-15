package io.kestra.plugin.clevercloud.applications;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.applications.model.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
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
    title = "Delete a Clever Cloud application",
    description = """
        Permanently deletes the application. This does not delete linked add-ons.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an application",
            full = true,
            code = """
                id: delete_application
                namespace: company.team

                tasks:
                  - id: delete
                    type: io.kestra.plugin.clevercloud.applications.Delete
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class Delete extends AbstractCleverCloudConnection implements RunnableTask<Delete.Output> {

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

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var url = resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId));

        logger.info("Deleting application {}", rAppId);
        var body = makeCall(runContext, buildDeleteRequest(url));
        var message = MAPPER.readValue(body, Message.class);

        return Output.builder().message(message.getMessage()).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Confirmation message returned by the API")
        private final String message;
    }
}
