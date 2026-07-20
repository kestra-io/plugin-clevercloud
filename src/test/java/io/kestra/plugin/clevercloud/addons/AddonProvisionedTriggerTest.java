package io.kestra.plugin.clevercloud.addons;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class AddonProvisionedTriggerTest extends AbstractClevercloudTest {

    @Inject
    RunContextInitializer runContextInitializer;

    // Each test gets a unique trigger ID to prevent KV store state leaking between tests.
    private String triggerId;

    private String buildTriggerId() {
        return "trigger-addon-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private AddonProvisionedTrigger buildTrigger(String baseUrl) {
        return TestableTrigger.builder()
            .id(triggerId)
            .type(AddonProvisionedTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private AddonProvisionedTrigger buildTriggerWithoutOrg(String baseUrl) {
        return TestableTrigger.builder()
            .id(triggerId)
            .type(AddonProvisionedTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private ConditionContext conditionContext(AddonProvisionedTrigger trigger, TriggerContext triggerContext, String flowId) throws Exception {
        // tenantId must be non-null: the local storage backend uses it as a path segment and NPEs otherwise.
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

    private static final String ADDON_A_JSON = """
        [
          {"id": "addon_aaa", "name": "my-redis", "region": "par", "provider": {"id": "redis-addon"}}
        ]
        """;

    private static final String ADDON_AB_JSON = """
        [
          {"id": "addon_aaa", "name": "my-redis", "region": "par", "provider": {"id": "redis-addon"}},
          {"id": "addon_bbb", "name": "my-postgres", "region": "par", "provider": {"id": "postgresql-addon"}, "plan": {"id": "plan_bbb"}}
        ]
        """;

    @Test
    void firstEvaluationStoresBaselineAndDoesNotFire(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/organisations/orga_test/addons", ADDON_A_JSON);

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("first evaluation must not fire", result.isEmpty(), is(true));
    }

    @Test
    void doesNotRefireWhenAddonSetUnchanged(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/organisations/orga_test/addons", ADDON_A_JSON);

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire on an unchanged add-on set", result.isEmpty(), is(true));
    }

    @Test
    void firesWhenNewAddonAppears(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .inScenario("addon-added")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(ADDON_A_JSON))
            .willSetStateTo("baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .inScenario("addon-added")
            .whenScenarioStateIs("baseline-set")
            .willReturn(okJson(ADDON_AB_JSON)));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when a new add-on appears", result.isPresent(), is(true));
        @SuppressWarnings("unchecked")
        var triggerVars = (Map<String, Object>) result.get().getTrigger().getVariables();
        @SuppressWarnings("unchecked")
        var addonIds = (List<String>) triggerVars.get("addonIds");
        assertThat(addonIds, contains("addon_bbb"));
        assertThat(triggerVars.get("addonId"), is("addon_bbb"));
        assertThat(triggerVars.get("name"), is("my-postgres"));
        assertThat(triggerVars.get("providerId"), is("postgresql-addon"));
        assertThat(triggerVars.get("planId"), is("plan_bbb"));
        assertThat(triggerVars.get("region"), is("par"));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/organisations/orga_test/addons", ADDON_A_JSON);

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withHeader("Authorization", equalTo("Bearer test-api-token")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        triggerId = buildTriggerId();
        stubGetJson("/self/addons", "[]");

        var trigger = buildTriggerWithoutOrg(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext("test-flow");
        var condCtx = conditionContext(trigger, trigCtx, "test-flow");

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/self/addons")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void sameTriggerIdInDifferentFlowsDoNotShareKvBaseline(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // Regression test: the KV key must include flowId, or two flows sharing a triggerId on the
        // same org would clobber each other's baseline (both triggers below share one on purpose).
        triggerId = buildTriggerId();
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .inScenario("flow-isolation")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(ADDON_A_JSON))
            .willSetStateTo("flow-a-baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .inScenario("flow-isolation")
            .whenScenarioStateIs("flow-a-baseline-set")
            .willReturn(okJson(ADDON_AB_JSON))
            .willSetStateTo("flow-b-baseline-set"));
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .inScenario("flow-isolation")
            .whenScenarioStateIs("flow-b-baseline-set")
            .willReturn(okJson(ADDON_A_JSON)));

        var triggerA = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtxA = triggerContext("flow-a");
        var condCtxA = conditionContext(triggerA, trigCtxA, "flow-a");

        var resultA1 = triggerA.evaluate(condCtxA, trigCtxA);
        assertThat("flow-a first evaluation must not fire", resultA1.isEmpty(), is(true));

        var triggerB = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtxB = triggerContext("flow-b");
        var condCtxB = conditionContext(triggerB, trigCtxB, "flow-b");

        // If the KV key collided on trigger id alone, flow-b would compare against flow-a's baseline and fire here.
        var resultB1 = triggerB.evaluate(condCtxB, trigCtxB);
        assertThat("flow-b first evaluation must not fire, since it establishes its own baseline",
            resultB1.isEmpty(), is(true));

        // If flow-b's poll had clobbered the shared KV entry, this would incorrectly detect addon_bbb as new.
        var resultA2 = triggerA.evaluate(condCtxA, trigCtxA);
        assertThat("flow-a must not fire on its own unchanged add-on set", resultA2.isEmpty(), is(true));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableTrigger extends AddonProvisionedTrigger {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
