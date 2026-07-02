package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class MemberChangeTriggerTest extends AbstractClevercloudTest {

    @Inject
    RunContextInitializer runContextInitializer;

    // Each test gets a unique trigger ID to prevent KV store state leaking between tests.
    private String triggerId;

    private String buildTriggerId() {
        return "trigger-member-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private MemberChangeTrigger buildTrigger(MemberChangeTrigger.MemberEvent event, String baseUrl) {
        return TestTasks.TestableMemberChangeTrigger.builder()
            .id(triggerId)
            .type(MemberChangeTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .event(Property.ofValue(event))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private ConditionContext conditionContext(MemberChangeTrigger trigger, TriggerContext triggerContext, String flowId) throws Exception {
        // tenantId must be non-null so namespaceKv can build valid storage paths.
        // The local storage backend uses tenant as a path segment and fails with NPE on null.
        var flow = Flow.builder()
            .id(flowId)
            .namespace("company.team")
            .tenantId("test-tenant")
            .build();
        var baseRunContext = (DefaultRunContext) runContextFactory.of(flow, trigger);
        var runContext = runContextInitializer.forScheduler(baseRunContext, triggerContext, trigger);
        return ConditionContext.builder()
            .runContext(runContext)
            .flow(flow)
            .build();
    }

    private TriggerContext triggerContext(String flowId) {
        return TriggerContext.builder()
            .tenantId("test-tenant")
            .namespace("company.team")
            .flowId(flowId)
            .triggerId(triggerId)
            .date(ZonedDateTime.now())
            .build();
    }

    private static final String MEMBER_A_JSON = """
        [
          {
            "member": {"id": "user_aaa", "email": "alice@example.com", "name": "Alice"},
            "role": "ADMIN",
            "job": "owner"
          }
        ]
        """;

    private static final String MEMBER_AB_JSON = """
        [
          {
            "member": {"id": "user_aaa", "email": "alice@example.com", "name": "Alice"},
            "role": "ADMIN",
            "job": "owner"
          },
          {
            "member": {"id": "user_bbb", "email": "bob@example.com", "name": "Bob"},
            "role": "DEVELOPER",
            "job": null
          }
        ]
        """;

    @Test
    void firstEvaluationStoresBaselineAndDoesNotFire(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/organisations/orga_test/members", MEMBER_A_JSON);

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("first evaluation must not fire", result.isEmpty(), is(true));
    }

    @Test
    void doesNotRefireWhenMemberSetUnchanged(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/organisations/orga_test/members", MEMBER_A_JSON);

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire on an unchanged member set", result.isEmpty(), is(true));
    }

    @Test
    void firesWhenMemberIsAdded(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("member-added")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(MEMBER_A_JSON))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("member-added")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson(MEMBER_AB_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_ADDED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when a member is added", result.isPresent(), is(true));
        @SuppressWarnings("unchecked")
        var triggerVars = (java.util.Map<String, Object>) result.get().getTrigger().getVariables();
        @SuppressWarnings("unchecked")
        var addedMembers = (java.util.List<String>) triggerVars.get("addedMembers");
        @SuppressWarnings("unchecked")
        var removedMembers = (java.util.List<String>) triggerVars.get("removedMembers");
        assertThat(addedMembers, contains("user_bbb"));
        assertThat(removedMembers, is(empty()));
    }

    @Test
    void firesWhenMemberIsRemoved(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("member-removed")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(MEMBER_AB_JSON))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("member-removed")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_REMOVED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when a member is removed", result.isPresent(), is(true));
        @SuppressWarnings("unchecked")
        var triggerVars = (java.util.Map<String, Object>) result.get().getTrigger().getVariables();
        @SuppressWarnings("unchecked")
        var removedMembers = (java.util.List<String>) triggerVars.get("removedMembers");
        @SuppressWarnings("unchecked")
        var addedMembers = (java.util.List<String>) triggerVars.get("addedMembers");
        assertThat(removedMembers, contains("user_bbb"));
        assertThat(addedMembers, is(empty()));
    }

    @Test
    void memberAddedEventDoesNotFireOnRemoval(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("added-vs-removal")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(MEMBER_AB_JSON))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("added-vs-removal")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_ADDED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_ADDED event must not fire when only a removal occurred", result.isEmpty(), is(true));
    }

    @Test
    void memberRemovedEventDoesNotFireOnAddition(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("removed-vs-addition")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(MEMBER_A_JSON))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("removed-vs-addition")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson(MEMBER_AB_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_REMOVED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_REMOVED event must not fire when only an addition occurred", result.isEmpty(), is(true));
    }

    @Test
    void memberChangedEventFiresOnBothAdditionAndRemoval(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("changed-fires")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(MEMBER_A_JSON))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("changed-fires")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson(MEMBER_AB_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_CHANGED event must fire on any change", result.isPresent(), is(true));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/organisations/orga_test/members", MEMBER_A_JSON);

        var trigger = TestTasks.TestableMemberChangeTrigger.builder()
            .id(triggerId)
            .type(MemberChangeTrigger.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .event(Property.ofValue(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");
        trigger.evaluate(condCtx, trigCtx);

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/members")), "my-secret-token");
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl() + "/");
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/members")));
    }

    @Test
    void sameTriggerIdInDifferentFlowsDoNotShareKvBaseline(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // Regression test: the KV key must include flowId, otherwise two flows using the same
        // trigger id on the same organisation would clobber each other's baseline.
        //
        // Both triggers below share the same triggerId on purpose: before the fix, the KV key was
        // "member-trigger-<triggerId>-<orgId>" with no flowId, so triggerB's poll would read and
        // overwrite triggerA's baseline entry.
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("flow-isolation")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(MEMBER_A_JSON))
            .willSetStateTo("flow-a-baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("flow-isolation")
            .whenScenarioStateIs("flow-a-baseline-set")
            .willReturn(okJson(MEMBER_AB_JSON))
            .willSetStateTo("flow-b-baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .inScenario("flow-isolation")
            .whenScenarioStateIs("flow-b-baseline-set")
            .willReturn(okJson(MEMBER_A_JSON)));

        var triggerA = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtxA = triggerContext("flow-a");
        var condCtxA = conditionContext(triggerA, trigCtxA, "flow-a");

        // Establish baseline for flow-a: {user_aaa}.
        var resultA1 = triggerA.evaluate(condCtxA, trigCtxA);
        assertThat("flow-a first evaluation must not fire", resultA1.isEmpty(), is(true));

        var triggerB = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtxB = triggerContext("flow-b");
        var condCtxB = conditionContext(triggerB, trigCtxB, "flow-b");

        // flow-b's first evaluation sees {user_aaa, user_bbb} but must still establish its own
        // baseline and not fire, since it is its own first evaluation. If the KV key collided on
        // trigger id alone, this would instead compare against flow-a's stored baseline and fire.
        var resultB1 = triggerB.evaluate(condCtxB, trigCtxB);
        assertThat("flow-b first evaluation must not fire, since it establishes its own baseline",
            resultB1.isEmpty(), is(true));

        // flow-a polls again and sees {user_aaa} again, unchanged from its own baseline. If flow-b's
        // poll had clobbered the shared KV entry, this would incorrectly detect user_bbb as removed.
        var resultA2 = triggerA.evaluate(condCtxA, trigCtxA);
        assertThat("flow-a must not fire on its own unchanged member set", resultA2.isEmpty(), is(true));
    }
}
