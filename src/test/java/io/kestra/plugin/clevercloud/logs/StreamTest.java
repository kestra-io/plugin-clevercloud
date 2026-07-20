package io.kestra.plugin.clevercloud.logs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class StreamTest extends AbstractClevercloudTest {

    private static final String SSE_BODY = """
        data: {"id":"log_1","applicationId":"app_test","date":"2024-01-01T00:00:00Z","severity":"info","service":"my-app","message":"Live line 1"}

        data: {"id":"log_2","applicationId":"app_test","date":"2024-01-01T00:00:05Z","severity":"info","service":"my-app","message":"Live line 2"}

        """;

    // WireMock's chunked dribble delay sleeps totalDuration/numberOfChunks before writing each
    // chunk, including the first, and splits the raw body bytes at fixed offsets. A comment line
    // (SSE-legal, ignored by parsers) padded far larger than SSE_BODY guarantees both complete
    // events land inside that first byte chunk, so it arrives after a single ~1s delay while the
    // remaining 14 chunks keep the connection open for another ~14s, well past this test's bound.
    private static final String OPEN_CONNECTION_BODY = SSE_BODY + ":" + "x".repeat(30_000) + "\n\n";
    private static final int DRIBBLE_CHUNKS = 15;
    private static final int DRIBBLE_TOTAL_MILLIS = 15_000;

    private TestableStream.TestableStreamBuilder<?, ?> baseBuilder(String baseUrl) {
        return TestableStream.builder()
            .id("stream-test-" + System.nanoTime())
            .type(Stream.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(baseUrl);
    }

    @Test
    void collectsLiveLogLinesDuringBoundedWindow(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .duration(Property.ofValue(Duration.ofSeconds(5)))
            .build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getLogs(), hasSize(2));
        assertThat(output.getLogs().getFirst().getMessage(), is("Live line 1"));
    }

    @Test
    void sendsBearerAuthAndSseAcceptHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();
        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .withHeader("Authorization", equalTo("Bearer test-api-token"))
            .withHeader("Accept", equalTo("text/event-stream")));
    }

    // Reproduces the reported hang: the server accepts the request, sends a 200 SSE response and
    // then never closes the connection (a genuine live tail). Without client-side enforcement of
    // duration, this would block forever. The fix must force-close and return within duration.
    @Test
    void neverClosingStreamStillReturnsWithinDuration(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withChunkedDribbleDelay(2, 3_600_000)
                .withBody(SSE_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .duration(Property.ofValue(Duration.ofSeconds(2)))
            .build();

        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            var output = task.run(runContext());
            assertThat(output.getTotal(), greaterThanOrEqualTo(0));
        });
    }

    // Proves the SseStopSignal sentinel path: the connection stays OPEN (only 1 of 15 dribble
    // chunks has been sent) but the limit is satisfied by the first event, so the task must return
    // within a couple of seconds, well before duration (which also drives idleTimeout here) would
    // ever kick in.
    @Test
    void limitReachedMidStreamReturnsEarly(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withChunkedDribbleDelay(DRIBBLE_CHUNKS, DRIBBLE_TOTAL_MILLIS)
                .withBody(OPEN_CONNECTION_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .duration(Property.ofValue(Duration.ofSeconds(20)))
            .limit(Property.ofValue(1))
            .build();

        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            var output = task.run(runContext());
            assertThat(output.getTotal(), is(1));
            assertThat(output.getLogs().getFirst().getId(), is("log_1"));
        });
    }

    // Stream fixes idleTimeout to duration (see Stream#run), unlike Fetch, so a quiet SSE connection
    // must run the full duration instead of returning early once no new log line arrives.
    @Test
    void silenceDoesNotEndStreamEarly(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withChunkedDribbleDelay(DRIBBLE_CHUNKS, DRIBBLE_TOTAL_MILLIS)
                .withBody(OPEN_CONNECTION_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .duration(Property.ofValue(Duration.ofSeconds(3)))
            .limit(Property.ofValue(10000))
            .build();

        var start = Instant.now();
        var output = task.run(runContext());
        var elapsed = Duration.between(start, Instant.now());

        assertThat(output.getTotal(), is(2));
        assertThat("a quiet stream must run the full duration, not exit early on silence",
            elapsed.compareTo(Duration.ofSeconds(3)) >= 0, is(true));
    }

    @Test
    void throwsWhenDurationExceedsMaximum(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .duration(Property.ofValue(Duration.ofMinutes(30)))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("duration"));
    }

    @Test
    void throwsWhenLimitOutOfBounds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .limit(Property.ofValue(0))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("limit"));
    }

    @Test
    void throwsCleanExceptionOnErrorResponseWithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"super-secret-internal-error\"}")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();

        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
    }
}
