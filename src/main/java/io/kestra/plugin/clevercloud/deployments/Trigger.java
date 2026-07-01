package io.kestra.plugin.clevercloud.deployments;

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
    title = "Trigger when a Clever Cloud application deployment reaches a target state",
    description = """
        Polls the deployment list for a given application at each interval.
        Fires an execution when a DEPLOY action deployment transitions to the configured target state.

        Only DEPLOY action records are considered. UNDEPLOY records (e.g. from scaling or moderation)
        are intentionally ignored so the trigger does not fire for infrastructure events unrelated to
        a code deployment.

        Dedup is timestamp-based: only deployments whose date (epoch milliseconds) is strictly after
        the previous evaluation cutoff are considered, preventing re-fires on already-seen deployments.

        When organisationId is omitted, the personal account endpoint (/self) is used.

        Real state values from the Clever Cloud API: OK (success), FAIL (error), CANCELLED, WIP (in-progress).
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fire when a deployment succeeds",
            full = true,
            code = """
                id: on_deploy_ok
                namespace: company.team

                triggers:
                  - id: watch_deploy
                    type: io.kestra.plugin.clevercloud.deployments.Trigger
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    targetState: OK
                    interval: PT1M

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Deployment {{ trigger.deploymentId }} succeeded (commit {{ trigger.commit }})"
                """
        ),
        @Example(
            title = "Fire when a personal account deployment succeeds",
            full = true,
            code = """
                id: on_personal_deploy_ok
                namespace: company.team

                triggers:
                  - id: watch_deploy
                    type: io.kestra.plugin.clevercloud.deployments.Trigger
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    targetState: OK
                    interval: PT1M

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Deployment {{ trigger.deploymentId }} succeeded (commit {{ trigger.commit }})"
                """
        )
    }
)
public class Trigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {

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

    @Schema(title = "Application ID to watch")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Schema(
        title = "Target deployment state that causes the trigger to fire",
        description = """
            Accepts: OK (successful deploy), FAIL (failed deploy), CANCELLED.
            The API returns deployments newest-first. Bursts of more than maxDeployments
            deployments between two polls may be missed.
            """
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<DeploymentState> targetState;

    @Schema(
        title = "Maximum number of deployments to fetch per poll",
        description = """
            The API returns results newest-first. Increase this value if deployments may arrive
            faster than the poll interval. Bursts beyond this limit between polls may be missed.
            Defaults to 25.
            """
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<Integer> maxDeployments = Property.ofValue(25);

    @Schema(
        title = "How often to check for new deployments",
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
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow();
        var rTargetState = runContext.render(targetState).as(DeploymentState.class).orElseThrow();
        var rMaxDeployments = runContext.render(maxDeployments).as(Integer.class).orElse(25);

        var url = AbstractCleverCloudConnection.join(baseUrl(), AbstractCleverCloudConnection.resourceBase(rOrgId))
            + "/applications/" + rAppId
            + "/deployments?limit=" + rMaxDeployments;

        logger.debug("Polling deployments for application {}", rAppId);

        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("GET");
        var body = AbstractCleverCloudConnection.makeCall(runContext, options, requestBuilder, rApiToken, String.class);

        var deployments = AbstractCleverCloudConnection.MAPPER.readValue(
            body != null ? body : "[]", new TypeReference<ArrayList<Deployment>>() {});

        // Timestamp-based dedup: only consider DEPLOY records that started after the last evaluation.
        // context.getDate() is the ZonedDateTime of the previous evaluation (always present).
        // UNDEPLOY records (scaling, moderation) are excluded so the trigger only fires on code deploys.
        Instant cutoff = context.getDate().toInstant();

        var matches = deployments.stream()
            .filter(d -> "DEPLOY".equals(d.getAction()))
            .filter(d -> rTargetState.name().equals(d.getState()))
            .filter(d -> isAfterCutoff(d.getDate(), cutoff))
            .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        if (matches.size() > 1) {
            logger.warn(
                "Found {} new deployments matching target state {} in this poll, only the most recent will fire the trigger, the others are skipped",
                matches.size(), rTargetState
            );
        }

        var deployment = matches.getFirst();
        logger.info("Deployment {} reached target state {}", deployment.getUuid(), rTargetState);

        var output = Output.builder()
            .deploymentId(deployment.getUuid())
            .state(deployment.getState())
            .commit(deployment.getCommit())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    /**
     * Returns true when the deployment's date is strictly after the cutoff.
     * Returns false when the date is absent so we skip rather than re-fire.
     */
    private static boolean isAfterCutoff(Instant deploymentDate, Instant cutoff) {
        return deploymentDate != null && deploymentDate.isAfter(cutoff);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Deployment ID that matched the target state")
        private final String deploymentId;

        @Schema(title = "State of the deployment when the trigger fired")
        private final String state;

        @Schema(title = "Git commit SHA associated with this deployment")
        private final String commit;
    }
}
