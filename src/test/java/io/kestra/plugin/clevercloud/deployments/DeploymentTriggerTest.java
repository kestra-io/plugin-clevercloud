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
import io.kestra.plugin.clevercloud.deployments.model.DeploymentState;
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
        return TestableDeploymentTrigger.builder()
            .id("trigger-test")
            .type(DeploymentTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(mockServer.url("").toString())
            .build();
    }

    private DeploymentTrigger buildTriggerWithoutOrg() {
        return TestableDeploymentTrigger.builder()
            .id("trigger-self-test")
            .type(DeploymentTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_personal"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(mockServer.url("").toString())
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
        assertThat(mockServer.getRequestCount(), is(1));
    }

    @Test
    void doesNotRefireOnAlreadySeenDeployment() throws Exception {
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

    @Test
    void firesOnlyOnceWhenMultipleDeploymentsMatch() throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var epoch1 = Instant.now().minusSeconds(120).toEpochMilli();
        var epoch2 = Instant.now().minusSeconds(60).toEpochMilli();

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deployment_multi-006",
                    "state": "OK",
                    "commit": "aaa000",
                    "date": "%d",
                    "action": "DEPLOY",
                    "cause": "Git"
                  },
                  {
                    "uuid": "deployment_multi-007",
                    "state": "OK",
                    "commit": "bbb111",
                    "date": "%d",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """.formatted(epoch1, epoch2)));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when at least one deployment matches", result.isPresent(), is(true));
        assertThat(mockServer.getRequestCount(), is(1));
    }

    @Test
    void sendsBearerAuthorizationHeader() throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var trigger = buildTrigger();
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization"), is("Bearer test-api-token"));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted() throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var trigger = buildTriggerWithoutOrg();
        var trigCtx = TriggerContext.builder()
            .namespace("company.team")
            .flowId("test-flow")
            .triggerId("trigger-self-test")
            .date(lastPoll)
            .build();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), org.hamcrest.Matchers.containsString("/self/applications/app_personal/deployments"));
        assertThat(request.getPath(), org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/organisations/")));
    }
}
