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

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetEnvTest extends AbstractClevercloudTest {

    @Test
    void fetchEnvironmentVariables(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications/app_abc123/env", """
            [
              {"name": "NODE_ENV", "value": "production"},
              {"name": "PORT", "value": "8080"}
            ]
            """);

        var task = TestableGetEnv.builder()
            .id("get-env-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getVariables(), aMapWithSize(2));
        assertThat(output.getVariables(), hasEntry("NODE_ENV", "production"));
        assertThat(output.getVariables(), hasEntry("PORT", "8080"));
    }

    @Test
    void handleEmptyEnvironment(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications/app_empty/env", "[]");

        var task = TestableGetEnv.builder()
            .id("get-env-empty-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(0));
        assertThat(output.getVariables().isEmpty(), is(true));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications/app_abc123/env", "[]");

        var task = TestableGetEnv.builder()
            .id("get-env-auth-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/env")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableGetEnv.builder()
            .id("get-env-missing-id-test")
            .type(GetEnv.class.getName())
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
    public static class TestableGetEnv extends GetEnv {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
