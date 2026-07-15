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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetEnvTest extends AbstractClevercloudTest {

    @Test
    void sendsPutPerVariable(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env/NODE_ENV"))
            .willReturn(okJson("{}")));
        stubFor(put(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env/PORT"))
            .willReturn(okJson("{}")));

        var task = TestableSetEnv.builder()
            .id("set-env-test")
            .type(SetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .vars(Property.ofValue(Map.of("NODE_ENV", "production", "PORT", "8080")))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getUpdatedCount(), is(2));
        verify(putRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env/NODE_ENV"))
            .withRequestBody(containing("\"value\":\"production\"")));
        verify(putRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env/PORT"))
            .withRequestBody(containing("\"value\":\"8080\"")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env/KEY"))
            .willReturn(okJson("{}")));

        var task = TestableSetEnv.builder()
            .id("set-env-auth-test")
            .type(SetEnv.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .vars(Property.ofValue(Map.of("KEY", "value")))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(putRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env/KEY")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenVarsEmpty(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableSetEnv.builder()
            .id("set-env-missing-vars-test")
            .type(SetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .vars(Property.ofValue(Map.of()))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("vars must contain at least one"));
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableSetEnv.builder()
            .id("set-env-missing-app-test")
            .type(SetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .vars(Property.ofValue(Map.of("KEY", "value")))
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
    public static class TestableSetEnv extends SetEnv {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
