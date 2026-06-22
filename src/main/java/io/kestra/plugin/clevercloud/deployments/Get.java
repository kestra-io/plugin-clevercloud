package io.kestra.plugin.clevercloud.deployments;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get details of a specific Clever Cloud deployment.",
    description = """
        Retrieves a single deployment by its ID, returning its state, commit SHA,
        start date, and end date.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch a specific deployment",
            full = true,
            code = """
                id: get_deployment
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.clevercloud.deployments.Get
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    deploymentId: "{{ inputs.deploymentId }}"
                """
        )
    }
)
public class Get extends AbstractCleverCloudConnection implements RunnableTask<Get.Output> {

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal apps)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Schema(title = "Deployment ID (uuid)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> deploymentId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow();
        var rDeployId = runContext.render(deploymentId).as(String.class).orElseThrow();

        var url = baseUrl(runContext)
            + "organisations/" + rOrgId
            + "/applications/" + rAppId
            + "/deployments/" + rDeployId;

        logger.info("Fetching deployment {}", rDeployId);
        var body = client.get(url);
        var deployment = MAPPER.readValue(body, Deployment.class);

        return Output.builder()
            .deploymentId(deployment.getId())
            .state(deployment.getState())
            .commit(deployment.getCommit())
            .startDate(deployment.getStartDate())
            .endDate(deployment.getEndDate())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Deployment ID")
        private final String deploymentId;

        @Schema(title = "Current state of the deployment (e.g. DEPLOY_OK, DEPLOY_FAILED, WIP)")
        private final String state;

        @Schema(title = "Git commit SHA associated with this deployment")
        private final String commit;

        @Schema(title = "ISO-8601 timestamp when the deployment started")
        private final String startDate;

        @Schema(title = "ISO-8601 timestamp when the deployment ended, null if still in progress")
        private final String endDate;
    }
}
