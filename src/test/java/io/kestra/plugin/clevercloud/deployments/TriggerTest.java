package io.kestra.plugin.clevercloud.deployments;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class TriggerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    private Trigger buildTrigger(String baseUrl) {
        return TestableTrigger.builder()
            .id("trigger-test")
            .type(Trigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private Trigger buildTriggerWithoutOrg(String baseUrl) {
        return TestableTrigger.builder()
            .id("trigger-self-test")
            .type(Trigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_personal"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private ConditionContext conditionContext(Trigger trigger, TriggerContext triggerContext) throws Exception {
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
    void firesWhenNewDeploymentMatchesTargetState(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var deploymentEpochMillis = Instant.now().minusSeconds(300).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
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
                """.formatted(deploymentEpochMillis))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), notNullValue());
        verify(1, getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments")));
    }

    @Test
    void doesNotRefireOnAlreadySeenDeployment(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(5);
        var oldDeploymentEpochMillis = Instant.now().minusSeconds(900).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
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
                """.formatted(oldDeploymentEpochMillis))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire on a deployment seen before last poll", result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireWhenNoDeploymentMatchesTargetState(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var deploymentEpochMillis = Instant.now().minusSeconds(120).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
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
                """.formatted(deploymentEpochMillis))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireOnDeploymentWithMissingDate(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {
                    "uuid": "deployment_nodate-004",
                    "state": "OK",
                    "commit": "jkl012",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """)));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("deployment with no date must be skipped", result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireOnUndeployRecord(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var deploymentEpochMillis = Instant.now().minusSeconds(60).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {
                    "uuid": "deployment_undeploy-005",
                    "state": "OK",
                    "date": "%d",
                    "action": "UNDEPLOY",
                    "cause": "Killed/Moderated"
                  }
                ]
                """.formatted(deploymentEpochMillis))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("UNDEPLOY records must not fire the trigger", result.isEmpty(), is(true));
    }

    @Test
    void firesOnlyOnceWhenMultipleDeploymentsMatch(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var epoch1 = Instant.now().minusSeconds(120).toEpochMilli();
        var epoch2 = Instant.now().minusSeconds(60).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
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
                """.formatted(epoch1, epoch2))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when at least one deployment matches", result.isPresent(), is(true));
        verify(1, getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("[]")));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .withHeader("Authorization", equalTo("Bearer test-api-token")));
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlEqualTo("/organisations/orga_test/applications/app_test/deployments?limit=25"))
            .willReturn(okJson("[]")));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl() + "/");
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/applications/app_test/deployments?limit=25")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlPathEqualTo("/self/applications/app_personal/deployments"))
            .willReturn(okJson("[]")));

        var trigger = buildTriggerWithoutOrg(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = TriggerContext.builder()
            .namespace("company.team")
            .flowId("test-flow")
            .triggerId("trigger-self-test")
            .date(lastPoll)
            .build();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_personal/deployments")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
    }
}
