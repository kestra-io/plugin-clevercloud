package io.kestra.plugin.clevercloud.deployments;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
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
                    type: io.kestra.plugin.clevercloud.deployments.DeploymentTrigger
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
                    type: io.kestra.plugin.clevercloud.deployments.DeploymentTrigger
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
public class DeploymentTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<DeploymentTrigger.Output> {

    @NotNull
    @Schema(title = "API token", description = "Bearer token for the Clever Cloud API. Store as a Kestra secret and reference with {{ secret('CC_API_TOKEN') }}.")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> apiToken;

    @Schema(title = "HTTP client options", description = "Optional HttpConfiguration applied to every Clever Cloud API call, including timeouts and proxy settings.")
    HttpConfiguration options;

    @Schema(title = "Override the Clever Cloud API base URL", description = "Used in tests to point at a mock server. Do not set in production flows.")
    @PluginProperty(group = "advanced", hidden = true)
    private Property<String> apiBaseUrl;

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

        var rBaseUrl = resolveBaseUrl(runContext);
        var url = rBaseUrl
            + "/" + AbstractCleverCloudConnection.resourceBase(rOrgId)
            + "/applications/" + rAppId
            + "/deployments?limit=" + rMaxDeployments;

        logger.debug("Polling deployments for application {}", rAppId);

        String body;
        try (var client = new HttpClient(runContext, options)) {
            var request = HttpRequest.builder()
                .uri(URI.create(url))
                .method("GET")
                .addHeader("Authorization", "Bearer " + rApiToken)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .build();
            var response = client.request(request, String.class);
            body = response.getBody() != null ? response.getBody() : "[]";
        } catch (HttpClientResponseException e) {
            var status = e.getResponse() != null ? e.getResponse().getStatus().getCode() : -1;
            logger.debug("Clever Cloud API returned {} on GET {}", status, url);
            throw new HttpClientResponseException(
                "Clever Cloud API error " + status + " polling deployments for " + rAppId
                    + ": check apiToken and that the application exists",
                e.getResponse()
            );
        }

        var deployments = AbstractCleverCloudConnection.MAPPER.readValue(
            body, new TypeReference<ArrayList<Deployment>>() {});

        // Timestamp-based dedup: only consider DEPLOY records that started after the last evaluation.
        // context.getDate() is the ZonedDateTime of the previous evaluation (always present).
        // UNDEPLOY records (scaling, moderation) are excluded so the trigger only fires on code deploys.
        Instant cutoff = context.getDate().toInstant();

        var matched = deployments.stream()
            .filter(d -> "DEPLOY".equals(d.getAction()))
            .filter(d -> rTargetState.name().equals(d.getState()))
            .filter(d -> isAfterCutoff(d.getDate(), cutoff))
            .findFirst();

        if (matched.isEmpty()) {
            return Optional.empty();
        }

        var deployment = matched.get();
        logger.info("Deployment {} reached target state {}", deployment.getUuid(), rTargetState);

        var output = Output.builder()
            .deploymentId(deployment.getUuid())
            .state(deployment.getState())
            .commit(deployment.getCommit())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private String resolveBaseUrl(io.kestra.core.runners.RunContext runContext) throws Exception {
        var override = System.getProperty("clevercloud.api.base.url");
        String raw = override != null
            ? override
            : runContext.render(apiBaseUrl).as(String.class).orElse(AbstractCleverCloudConnection.DEFAULT_BASE_URL);
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    /**
     * Returns true when the deployment's date (epoch milliseconds string) is strictly after the cutoff.
     * Returns false when the date is absent or unparseable so we skip rather than re-fire.
     */
    private static boolean isAfterCutoff(String epochMillisStr, Instant cutoff) {
        if (epochMillisStr == null || epochMillisStr.isBlank()) {
            return false;
        }
        try {
            var deploymentInstant = Instant.ofEpochMilli(Long.parseLong(epochMillisStr));
            return deploymentInstant.isAfter(cutoff);
        } catch (Exception e) {
            return false;
        }
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
