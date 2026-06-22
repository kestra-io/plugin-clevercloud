package io.kestra.plugin.clevercloud.deployments;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import jakarta.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class DeploymentTriggerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    MockWebServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    private DeploymentTrigger buildTrigger() {
        return DeploymentTrigger.builder()
            .id("trigger-test")
            .type(DeploymentTrigger.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .targetState(Property.of("OK"))
            .interval(Duration.ofMinutes(1))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();
    }

    private ConditionContext conditionContext(DeploymentTrigger trigger, TriggerContext triggerContext) throws Exception {
        var flow = Flow.builder()
            .id("test-flow")
            .namespace("company.team")
            .build();
        var baseRunContext = (DefaultRunContext) runContextFactory.of(flow, trigger);
        var runContext = runContextInitializer.forScheduler(baseRunContext, triggerContext, trigger);
        return ConditionContext.builder()
            .runContext(runContext)
            .flow(flow)
            .build();
    }

    private TriggerContext triggerContext(ZonedDateTime date) {
        return TriggerContext.builder()
            .namespace("company.team")
            .flowId("test-flow")
            .triggerId("trigger-test")
            .date(date)
            .build();
    }

    @Test
    void firesWhenNewDeploymentMatchesTargetState() throws Exception {
        // Deployment epoch 5 minutes ago, cutoff is 10 minutes ago: deployment is newer, trigger fires.
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var deploymentEpochMillis = Instant.now().minusSeconds(300).toEpochMilli();

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deployment_new-001",
                    "state": "OK",
                    "commit": "abc123",
                    "date": "%d",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """.formatted(deploymentEpochMillis)));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), notNullValue());
    }

    @Test
    void doesNotRefireOnAlreadySeenDeployment() throws Exception {
        // Deployment epoch 15 minutes ago, cutoff is 5 minutes ago: deployment predates cutoff, no fire.
        var lastPoll = ZonedDateTime.now().minusMinutes(5);
        var oldDeploymentEpochMillis = Instant.now().minusSeconds(900).toEpochMilli();

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deployment_old-002",
                    "state": "OK",
                    "commit": "def456",
                    "date": "%d",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """.formatted(oldDeploymentEpochMillis)));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire on a deployment seen before last poll", result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireWhenNoDeploymentMatchesTargetState() throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var deploymentEpochMillis = Instant.now().minusSeconds(120).toEpochMilli();

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deployment_wip-003",
                    "state": "WIP",
                    "commit": "ghi789",
                    "date": "%d",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """.formatted(deploymentEpochMillis)));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireOnDeploymentWithMissingDate() throws Exception {
        // Deployments with no date field are skipped to avoid risk of re-firing.
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deployment_nodate-004",
                    "state": "OK",
                    "commit": "jkl012",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("deployment with no date must be skipped", result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireOnUndeployRecord() throws Exception {
        // UNDEPLOY records (e.g. scaling/moderation) must not fire the trigger even when their state matches.
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var deploymentEpochMillis = Instant.now().minusSeconds(60).toEpochMilli();

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deployment_undeploy-005",
                    "state": "OK",
                    "date": "%d",
                    "action": "UNDEPLOY",
                    "cause": "Killed/Moderated"
                  }
                ]
                """.formatted(deploymentEpochMillis)));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("UNDEPLOY records must not fire the trigger", result.isEmpty(), is(true));
    }
}
