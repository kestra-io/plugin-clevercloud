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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteTest extends AbstractClevercloudTest {

    @Test
    void deletesApplication(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(okJson("{\"id\": 1, \"message\": \"Application deleted\", \"type\": \"success\"}")));

        var task = TestableDelete.builder()
            .id("delete-app-test")
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getMessage(), is("Application deleted"));
        verify(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/self/applications/app_abc123"))
            .willReturn(okJson("{\"message\": \"Application deleted\"}")));

        var task = TestableDelete.builder()
            .id("delete-app-self-path-test")
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(deleteRequestedFor(urlPathEqualTo("/self/applications/app_abc123")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(okJson("{\"message\": \"ok\"}")));

        var task = TestableDelete.builder()
            .id("delete-app-auth-test")
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123")), "my-secret-token");
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(okJson("{\"message\": \"ok\"}")));

        var task = TestableDelete.builder()
            .id("delete-app-trailing-slash-test")
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        task.run(runContext());

        verify(deleteRequestedFor(urlEqualTo("/organisations/orga_test/applications/app_abc123")));
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableDelete.builder()
            .id("delete-app-missing-id-test")
            .type(Delete.class.getName())
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
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableDelete.builder()
            .id("delete-app-500-test")
            .type(Delete.class.getName())
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
    public static class TestableDelete extends Delete {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
