package io.kestra.plugin.clevercloud.logs;

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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class LogPatternTriggerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    private LogPatternTrigger buildTrigger(String baseUrl) {
        return TestableLogPatternTrigger.builder()
            .id("log-pattern-trigger-test")
            .type(LogPatternTrigger.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .pattern(Property.ofValue("FATAL|OutOfMemoryError"))
            .interval(Duration.ofMinutes(1))
            .testBaseUrl(baseUrl)
            .build();
    }

    private ConditionContext conditionContext(LogPatternTrigger trigger, TriggerContext triggerContext) throws Exception {
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
            .triggerId("log-pattern-trigger-test")
            .date(date)
            .build();
    }

    @Test
    void firesWhenNewLogLineMatchesPattern(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(5);

        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("""
                    data: {"id":"log_1","date":"%s","severity":"fatal","service":"my-app","message":"FATAL error, shutting down"}

                    """.formatted(java.time.Instant.now().toString()))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isPresent(), is(true));
        verify(1, getRequestedFor(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs")));
    }

    @Test
    void doesNotFireWhenNoLineMatchesPattern(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(5);

        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("""
                    data: {"id":"log_1","date":"%s","severity":"info","service":"my-app","message":"all good"}

                    """.formatted(java.time.Instant.now().toString()))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void doesNotRefireOnLogLineSeenBeforeLastPoll(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(5);
        var oldLogDate = lastPoll.minusMinutes(10).toInstant();

        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("""
                    data: {"id":"log_1","date":"%s","severity":"fatal","service":"my-app","message":"FATAL old error"}

                    """.formatted(oldLogDate.toString()))));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        Optional<Execution> result = trigger.evaluate(condCtx, trigCtx);

        assertThat("trigger must not fire on a log line seen before the last poll", result.isEmpty(), is(true));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var lastPoll = ZonedDateTime.now().minusMinutes(5);

        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("")));

        var trigger = buildTrigger(wireMockRuntimeInfo.getHttpBaseUrl());
        var trigCtx = triggerContext(lastPoll);
        var condCtx = conditionContext(trigger, trigCtx);

        trigger.evaluate(condCtx, trigCtx);

        verify(getRequestedFor(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .withHeader("Authorization", equalTo("Bearer test-api-token")));
    }
}
