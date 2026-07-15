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

class RedeployTest extends AbstractClevercloudTest {

    @Test
    void redeploysWithoutCommitWhenOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")).willReturn(ok()));

        var task = TestableRedeploy.builder()
            .id("redeploy-test")
            .type(Redeploy.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")));
    }

    @Test
    void redeploysWithCommitAndUseCacheAsQueryParams(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")).willReturn(ok()));

        var task = TestableRedeploy.builder()
            .id("redeploy-commit-test")
            .type(Redeploy.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .commit(Property.ofValue("a1b2c3d4"))
            .useCache(Property.ofValue(false))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances"))
            .withQueryParam("commit", matching("a1b2c3d4"))
            .withQueryParam("useCache", matching("false")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/instances")).willReturn(ok()));

        var task = TestableRedeploy.builder()
            .id("redeploy-auth-test")
            .type(Redeploy.class.getName())
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
        var task = TestableRedeploy.builder()
            .id("redeploy-missing-id-test")
            .type(Redeploy.class.getName())
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
    public static class TestableRedeploy extends Redeploy {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
