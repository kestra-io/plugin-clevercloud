package io.kestra.plugin.clevercloud.deployments;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import io.kestra.plugin.clevercloud.deployments.model.DeploymentState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Wait for a Clever Cloud deployment to reach a target state",
    description = """
        Polls a deployment at a configurable interval until it reaches one of the terminal
        states (OK, FAIL, CANCELLED) or the specified target state.

        WIP means the deployment is still in progress. A successful deployment has state OK
        and action DEPLOY. Throws when the deployment reaches a terminal state that is not
        the target, or when the timeout elapses.

        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Wait until a deployment succeeds or fails",
            full = true,
            code = """
                id: wait_deployment
                namespace: company.team

                tasks:
                  - id: wait
                    type: io.kestra.plugin.clevercloud.deployments.WaitForState
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    deploymentId: "{{ inputs.deploymentId }}"
                    targetState: OK
                    pollInterval: PT10S
                    timeout: PT10M
                """
        )
    }
)
public class WaitForState extends AbstractCleverCloudConnection implements RunnableTask<WaitForState.Output> {

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

    @Schema(title = "Deployment ID to watch")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> deploymentId;

    @Schema(
        title = "Target state to wait for",
        description = """
            Accepts: OK (deployment succeeded), FAIL (deployment errored), CANCELLED (deployment cancelled),
            WIP (still in progress, not a terminal state).
            Use OK to wait for a successful deploy.
            """
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<DeploymentState> targetState;

    @Schema(
        title = "How often to poll the deployment status",
        description = "ISO-8601 duration, e.g. PT10S for 10 seconds. Defaults to 15 seconds."
    )
    @PluginProperty(group = "reliability")
    @Builder.Default
    private Property<Duration> pollInterval = Property.ofValue(Duration.ofSeconds(15));

    @Schema(
        title = "Maximum time to wait before aborting",
        description = "ISO-8601 duration, e.g. PT10M for 10 minutes. Defaults to 30 minutes."
    )
    @PluginProperty(group = "reliability")
    @Builder.Default
    private Property<Duration> timeout = Property.ofValue(Duration.ofMinutes(30));

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow();
        var rDeployId = runContext.render(deploymentId).as(String.class).orElseThrow();
        var rTargetState = runContext.render(targetState).as(DeploymentState.class).orElseThrow();
        var rPollInterval = runContext.render(pollInterval).as(Duration.class).orElse(Duration.ofSeconds(15));
        var rTimeout = runContext.render(timeout).as(Duration.class).orElse(Duration.ofMinutes(30));

        var url = baseUrl()
            + "/" + resourceBase(rOrgId)
            + "/applications/" + rAppId
            + "/deployments/" + rDeployId;

        var deadline = Instant.now().plus(rTimeout);
        logger.info("Waiting for deployment {} to reach state {} (timeout: {})", rDeployId, rTargetState, rTimeout);

        while (true) {
            var body = makeCall(runContext, buildGetRequest(url));
            var deployment = MAPPER.readValue(body, Deployment.class);
            var rawState = deployment.getState();

            logger.debug("Deployment {} state: {}", rDeployId, rawState);

            if (rTargetState.name().equals(rawState)) {
                logger.info("Deployment {} reached target state {}", rDeployId, rawState);
                return Output.builder()
                    .deploymentId(rDeployId)
                    .state(rawState)
                    .build();
            }

            // Resolve the known enum to check terminality. An unrecognised state is treated as
            // non-terminal so we keep polling rather than throwing on unexpected API values.
            DeploymentState knownState;
            try {
                knownState = DeploymentState.valueOf(rawState);
            } catch (IllegalArgumentException e) {
                logger.warn("Deployment {} returned unrecognised state '{}', continuing to poll", rDeployId, rawState);
                knownState = null;
            }

            // A known terminal state that is not the target means the deployment ended unexpectedly.
            if (knownState != null && knownState.isTerminal()) {
                throw new IllegalStateException(
                    "Deployment " + rDeployId + " reached state " + rawState + " but expected " + rTargetState
                );
            }

            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException(
                    "Timed out waiting for deployment " + rDeployId + " to reach " + rTargetState
                        + " after " + rTimeout + ". Last state: " + rawState
                );
            }

            try {
                Thread.sleep(rPollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Deployment ID that was polled")
        private final String deploymentId;

        @Schema(title = "Final state when the task completed")
        private final String state;
    }
}
