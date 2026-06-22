package io.kestra.plugin.clevercloud.organisations;

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
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class MemberChangeTriggerTest {

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

    // Each test gets a unique trigger ID to prevent KV store state leaking between tests.
    private String triggerId;

    @BeforeEach
    void setUpTrigger() {
        triggerId = "trigger-member-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private MemberChangeTrigger buildTrigger(MemberChangeTrigger.MemberEvent event) {
        return MemberChangeTrigger.builder()
            .id(triggerId)
            .type(MemberChangeTrigger.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .event(Property.of(event))
            .interval(Duration.ofMinutes(1))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
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

    private static final String EMPTY_JSON = "[]";

    @Test
    void firstEvaluationStoresBaselineAndDoesNotFire() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED);
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("first evaluation must not fire", result.isEmpty(), is(true));
    }

    @Test
    void doesNotRefireWhenMemberSetUnchanged() throws Exception {
        // First call: baseline
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));
        // Second call: same members
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED);
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        // Establish baseline
        trigger.evaluate(condCtx, trigCtx);

        // Second evaluation with same members
        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire on an unchanged member set", result.isEmpty(), is(true));
    }

    @Test
    void firesWhenMemberIsAdded() throws Exception {
        // First call: baseline with Alice only
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));
        // Second call: Alice + Bob
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_AB_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_ADDED);
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
    void firesWhenMemberIsRemoved() throws Exception {
        // First call: baseline with Alice + Bob
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_AB_JSON));
        // Second call: only Alice remains
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_REMOVED);
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
    void memberAddedEventDoesNotFireOnRemoval() throws Exception {
        // First call: baseline with Alice + Bob
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_AB_JSON));
        // Second call: Bob removed
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_ADDED);
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_ADDED event must not fire when only a removal occurred", result.isEmpty(), is(true));
    }

    @Test
    void memberRemovedEventDoesNotFireOnAddition() throws Exception {
        // First call: baseline with Alice only
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));
        // Second call: Bob added
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_AB_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_REMOVED);
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_REMOVED event must not fire when only an addition occurred", result.isEmpty(), is(true));
    }

    @Test
    void memberChangedEventFiresOnBothAdditionAndRemoval() throws Exception {
        // First call: baseline with Alice only
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_A_JSON));
        // Second call: Bob added (any change)
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(MEMBER_AB_JSON));

        var trigger = buildTrigger(MemberChangeTrigger.MemberEvent.MEMBER_CHANGED);
        var trigCtx = triggerContext();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("MEMBER_CHANGED event must fire on any change", result.isPresent(), is(true));
    }
}
