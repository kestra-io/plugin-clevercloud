package io.kestra.plugin.clevercloud.logs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamTest extends AbstractClevercloudTest {

    private static final String SSE_BODY = """
        data: {"id":"log_1","applicationId":"app_test","date":"2024-01-01T00:00:00Z","severity":"info","service":"my-app","message":"Live line 1"}

        data: {"id":"log_2","applicationId":"app_test","date":"2024-01-01T00:00:05Z","severity":"info","service":"my-app","message":"Live line 2"}

        """;

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
