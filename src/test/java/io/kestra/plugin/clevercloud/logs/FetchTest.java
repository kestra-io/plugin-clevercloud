package io.kestra.plugin.clevercloud.logs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.logs.model.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FetchTest extends AbstractClevercloudTest {

    // Timestamps intentionally avoid exact midnight UTC: Kestra's ION serialization collapses a
    // midnight-UTC Instant into a LocalDate on read-back (see IonParser#getEmbeddedObject), which
    // would break deserialization of the stored LogEntry.date field in the STORE fetchType test.
    private static final String SSE_BODY = """
        data: {"id":"log_1","applicationId":"app_test","date":"2024-01-01T10:00:00Z","severity":"info","service":"my-app","message":"Server started"}

        data: {"id":"log_2","applicationId":"app_test","date":"2024-01-01T10:00:05Z","severity":"error","service":"my-app","message":"Connection refused"}

        """;

    private TestableFetch.TestableFetchBuilder<?, ?> baseBuilder(String baseUrl) {
        return TestableFetch.builder()
            .id("fetch-test-" + System.nanoTime())
            .type(Fetch.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .since(Property.ofValue(Instant.parse("2024-01-01T00:00:00Z")))
            .testBaseUrl(baseUrl);
    }

    @Test
    void fetchesLogsWithinBoundedWindow(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getLogs(), hasSize(2));
        assertThat(output.getLogs().getFirst().getId(), is("log_1"));
        assertThat(output.getLogs().getFirst().getMessage(), is("Server started"));
        assertThat(output.getLogs().get(1).getSeverity(), is("error"));
        assertThat(output.getLogs().get(1).getDate(), is(Instant.parse("2024-01-01T10:00:05Z")));
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
    void appliesQueryParameters(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .until(Property.ofValue(Instant.parse("2024-01-01T01:00:00Z")))
            .limit(Property.ofValue(50))
            .filter(Property.ofValue("error"))
            .build();
        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .withQueryParam("since", equalTo("2024-01-01T00:00:00Z"))
            .withQueryParam("until", equalTo("2024-01-01T01:00:00Z"))
            .withQueryParam("limit", equalTo("50"))
            .withQueryParam("filter", equalTo("error")));
    }

    @Test
    void fetchTypeFetchOneReturnsFirstLogLine(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getLog(), is(notNullValue()));
        assertThat(output.getLog().getId(), is("log_1"));
        assertThat(output.getLogs(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();
        var runContext = runContext();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getLogs(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, LogEntry.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getId(), is("log_1"));
        }
    }

    @Test
    void fetchTypeNoneReturnsOnlyCount(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .fetchType(Property.ofValue(FetchType.NONE))
            .build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getLogs(), is(nullValue()));
        assertThat(output.getLog(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void throwsWhenLimitOutOfBounds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .limit(Property.ofValue(20000))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("limit"));
    }

    @Test
    void throwsCleanExceptionOnErrorResponseWithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/logs/organisations/orga_test/applications/app_test/logs"))
            .willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"invalid-token\",\"message\":\"super-secret-internal-detail\"}")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();

        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("401"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-detail")));
    }
}
