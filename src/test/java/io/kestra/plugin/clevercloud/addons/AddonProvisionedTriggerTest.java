package io.kestra.plugin.clevercloud.addons;

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
import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
@WireMockTest
class AddonProvisionedTriggerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    private AddonProvisionedTrigger buildTrigger(String baseUrl) {
        return TestableTrigger.builder()
            .id("addon-provisioned-trigger-test")
            .type(AddonProvisionedTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private AddonProvisionedTrigger buildTriggerWithoutOrg(String baseUrl) {
        return TestableTrigger.builder()
            .id("addon-provisioned-trigger-self-test")
            .type(AddonProvisionedTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private ConditionContext conditionContext(AddonProvisionedTrigger trigger, TriggerContext triggerContext) throws Exception {
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
            .triggerId("addon-provisioned-trigger-test")
            .date(date)
            .build();
    }

    @Test
    void firesWhenNewAddonIsProvisioned(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var creationEpochMillis = Instant.now().minusSeconds(300).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("""
                [
                  {
                    "id": "addon_new-001",
                    "name": "my-postgres",
                    "region": "par",
                    "provider": {"id": "postgresql-addon"},
                    "creationDate": %d
                  }
                ]
                """.formatted(creationEpochMillis))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), notNullValue());
        verify(1, getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")));
    }

    @Test
    void doesNotRefireOnAlreadySeenAddon(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(5);
        var oldCreationEpochMillis = Instant.now().minusSeconds(900).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("""
                [
                  {
                    "id": "addon_old-002",
                    "name": "my-redis",
                    "region": "par",
                    "creationDate": %d
                  }
                ]
                """.formatted(oldCreationEpochMillis))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not re-fire on an add-on provisioned before the last poll", result.isEmpty(), is(true));
    }

    @Test
    void doesNotFireOnAddonWithMissingCreationDate(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("""
                [
                  {"id": "addon_nodate-003", "name": "my-mongo"}
                ]
                """)));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("add-on with no creationDate must be skipped", result.isEmpty(), is(true));
    }

    @Test
    void firesOnlyOnceWhenMultipleAddonsMatch(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);
        var epoch1 = Instant.now().minusSeconds(120).toEpochMilli();
        var epoch2 = Instant.now().minusSeconds(60).toEpochMilli();

        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("""
                [
                  {"id": "addon_multi-004", "creationDate": %d},
                  {"id": "addon_multi-005", "creationDate": %d}
                ]
                """.formatted(epoch1, epoch2))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must fire when at least one add-on is new", result.isPresent(), is(true));
        verify(1, getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("[]")));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withHeader("Authorization", equalTo("Bearer test-api-token")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(10);

        stubFor(get(urlPathEqualTo("/self/addons"))
            .willReturn(okJson("[]")));

        var trigger = buildTriggerWithoutOrg(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = TriggerContext.builder()
            .namespace("company.team")
            .flowId("test-flow")
            .triggerId("addon-provisioned-trigger-self-test")
            .date(lastPoll)
            .build();
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/self/addons")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
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
