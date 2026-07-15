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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractClevercloudTest {

    @Test
    void fetchApplicationDetails(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications/app_abc123", """
            {
              "id": "app_abc123",
              "name": "kestra",
              "description": "kestra app",
              "zone": "par",
              "state": "SHOULD_BE_UP",
              "deployUrl": "https://push.clever-cloud.com/kestra.git",
              "instance": {
                "type": "node",
                "version": "20260617",
                "minInstances": 1,
                "maxInstances": 3,
                "minFlavor": {"name": "XS"},
                "maxFlavor": {"name": "M"}
              }
            }
            """);

        var task = TestableGet.builder()
            .id("get-app-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("app_abc123"));
        assertThat(output.getName(), is("kestra"));
        assertThat(output.getZone(), is("par"));
        assertThat(output.getState(), is("SHOULD_BE_UP"));
        assertThat(output.getDeployUrl(), is("https://push.clever-cloud.com/kestra.git"));
        assertThat(output.getInstanceType(), is("node"));
        assertThat(output.getInstanceVersion(), is("20260617"));
        assertThat(output.getMinInstances(), is(1));
        assertThat(output.getMaxInstances(), is(3));
        assertThat(output.getMinFlavor(), is("XS"));
        assertThat(output.getMaxFlavor(), is("M"));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications/app_abc123", "{\"id\":\"app_abc123\"}");

        var task = TestableGet.builder()
            .id("get-app-auth-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123")), "my-secret-token");
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/self/applications/app_abc123", "{\"id\":\"app_abc123\"}");

        var task = TestableGet.builder()
            .id("get-app-self-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_abc123")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableGet.builder()
            .id("get-app-missing-id-test")
            .type(Get.class.getName())
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
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableGet.builder()
            .id("get-app-500-test")
            .type(Get.class.getName())
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
    public static class TestableGet extends Get {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
