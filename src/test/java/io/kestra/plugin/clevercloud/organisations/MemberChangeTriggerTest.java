package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class MemberChangeTriggerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    // Each test gets a unique trigger ID to prevent KV store state leaking between tests.
    private String triggerId;

    private String buildTriggerId() {
        return "trigger-member-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private MemberChangeTrigger buildTrigger(MemberChangeTrigger.MemberEvent event, String baseUrl) {
        return TestableMemberChangeTrigger.builder()
            .id(triggerId)
            .type(MemberChangeTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .event(Property.ofValue(event))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private ConditionContext conditionContext(MemberChangeTrigger trigger, TriggerContext triggerContext) throws Exception {
        // tenantId must be non-null so namespaceKv can build valid storage paths.
        // The local storage backend uses tenant as a path segment and fails with NPE on null.
        var flow = Flow.builder()
            .id("test-flow")
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

    private TriggerContext triggerContext() {
        return TriggerContext.builder()
            .tenantId("test-tenant")
            .namespace("company.team")
            .flowId("test-flow")
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
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("first evaluation must not fire", result.isEmpty(), is(true));
    }

    @Test
    void doesNotRefireWhenMemberSetUnchanged(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

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
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

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
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

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
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

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
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

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
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_CHANGED event must fire on any change", result.isPresent(), is(true));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = TestableMemberChangeTrigger.builder()
            .id(triggerId)
            .type(MemberChangeTrigger.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .event(Property.ofValue(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);
        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/members"))
            .withHeader("Authorization", equalTo("Bearer my-secret-token")));
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson(MEMBER_A_JSON)));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED, wireMockRuntimeInfo.getHttpBaseUrl() + "/");
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/members")));
    }
}
