package io.kestra.plugin.clevercloud.deployments;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
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
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.CleverCloudApi;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger when a Clever Cloud application deployment reaches a target state.",
    description = """
        Polls the deployment list for a given application at each interval.
        Fires an execution when a deployment transitions to the configured target state.
        Only deployments discovered since the last successful poll are considered,
        preventing the trigger from re-firing on already-processed deployments.
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
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    targetState: DEPLOY_OK
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

    @Schema(
        title = "OAuth consumer key.",
        description = "Store as a Kestra secret and reference with {{ secret('CC_CONSUMER_KEY') }}."
    )
    @PluginProperty(group = "connection")
    @NotNull
    private Property<String> consumerKey;

    @Schema(
        title = "OAuth consumer secret.",
        description = "Store as a Kestra secret and reference with {{ secret('CC_CONSUMER_SECRET') }}."
    )
    @PluginProperty(group = "connection")
    @NotNull
    private Property<String> consumerSecret;

    @Schema(
        title = "OAuth access token.",
        description = "Store as a Kestra secret and reference with {{ secret('CC_TOKEN') }}."
    )
    @PluginProperty(group = "connection")
    @NotNull
    private Property<String> token;

    @Schema(
        title = "OAuth access token secret.",
        description = "Store as a Kestra secret and reference with {{ secret('CC_TOKEN_SECRET') }}."
    )
    @PluginProperty(group = "connection")
    @NotNull
    private Property<String> tokenSecret;

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal apps)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(title = "Application ID to watch")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Schema(
        title = "Target deployment state that causes the trigger to fire.",
        description = "Common values: DEPLOY_OK (successful deploy), DEPLOY_FAILED (failed deploy)."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> targetState;

    @Schema(
        title = "How often to check for new deployments.",
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

        var rConsumerKey = runContext.render(consumerKey).as(String.class).orElseThrow();
        var rConsumerSecret = runContext.render(consumerSecret).as(String.class).orElseThrow();
        var rToken = runContext.render(token).as(String.class).orElseThrow();
        var rTokenSecret = runContext.render(tokenSecret).as(String.class).orElseThrow();
        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow();
        var rTargetState = runContext.render(targetState).as(String.class).orElseThrow();

        var service = new ServiceBuilder(rConsumerKey)
            .apiSecret(rConsumerSecret)
            .build(new CleverCloudApi());
        var accessToken = new OAuth1AccessToken(rToken, rTokenSecret);
        var client = new AbstractCleverCloudConnection.SignedClient(service, accessToken,
            new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build());

        var url = AbstractCleverCloudConnection.BASE_URL
            + "organisations/" + rOrgId
            + "/applications/" + rAppId
            + "/deployments?limit=10";

        logger.debug("Polling deployments for application {}", rAppId);
        var body = client.get(url);
        var deployments = AbstractCleverCloudConnection.MAPPER.readValue(
            body, new TypeReference<ArrayList<Deployment>>() {});

        var matched = deployments.stream()
            .filter(d -> rTargetState.equals(d.getState()))
            .findFirst();

        if (matched.isEmpty()) {
            return Optional.empty();
        }

        var deployment = matched.get();
        logger.info("Deployment {} reached target state {}", deployment.getId(), rTargetState);

        var output = Output.builder()
            .deploymentId(deployment.getId())
            .state(deployment.getState())
            .commit(deployment.getCommit())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
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
