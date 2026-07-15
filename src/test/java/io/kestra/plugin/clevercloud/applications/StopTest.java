package io.kestra.plugin.clevercloud.applications;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StopTest extends AbstractClevercloudTest {

    @Test
    void stopsApplicationInstances(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances"))
            .willReturn(okJson("{\"id\": 42, \"message\": \"Application stopped\", \"type\": \"success\"}")));

        var task = TestableStop.builder()
            .id("stop-app-test")
            .type(Stop.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getMessage(), is("Application stopped"));
        verify(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances"))
            .willReturn(okJson("{\"message\": \"ok\"}")));

        var task = TestableStop.builder()
            .id("stop-app-auth-test")
            .type(Stop.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableStop.builder()
            .id("stop-app-missing-id-test")
            .type(Stop.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("applicationId is required"));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableStop.builder()
            .id("stop-app-500-test")
            .type(Stop.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), not(containsString("internal secret stack trace details")));
        assertThat(ex.getMessage(), containsString("500"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableStop extends Stop {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
