package io.kestra.plugin.clevercloud.applications;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestartTest extends AbstractClevercloudTest {

    @Test
    void restartsWithoutCommitParam(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")).willReturn(ok()));

        var task = TestableRestart.builder()
            .id("restart-test")
            .type(Restart.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances"))
            .withQueryParam("commit", absent()));
    }

    @Test
    void restartsWithUseCacheParam(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")).willReturn(ok()));

        var task = TestableRestart.builder()
            .id("restart-cache-test")
            .type(Restart.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .useCache(Property.ofValue(false))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances"))
            .withQueryParam("useCache", matching("false"))
            .withQueryParam("commit", absent()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")).willReturn(ok()));

        var task = TestableRestart.builder()
            .id("restart-auth-test")
            .type(Restart.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableRestart.builder()
            .id("restart-missing-id-test")
            .type(Restart.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("applicationId is required"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableRestart extends Restart {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
