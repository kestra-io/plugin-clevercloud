package io.kestra.plugin.clevercloud.addons;

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
        stubGetJson("/organisations/orga_test/addons/addon_abc123/env", """
            [
              {"name": "POSTGRESQL_ADDON_URI", "value": "postgresql://user:pass@host:5432/db"},
              {"name": "POSTGRESQL_ADDON_DB", "value": "db"}
            ]
            """);

        var task = TestableGetEnv.builder()
            .id("get-addon-env-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .addonId(Property.ofValue("addon_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getVariables(), aMapWithSize(2));
        assertThat(output.getVariables(), hasEntry("POSTGRESQL_ADDON_URI", "postgresql://user:pass@host:5432/db"));
        assertThat(output.getVariables(), hasEntry("POSTGRESQL_ADDON_DB", "db"));
    }

    @Test
    void handleEmptyEnvironment(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons/addon_empty/env", "[]");

        var task = TestableGetEnv.builder()
            .id("get-addon-env-empty-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .addonId(Property.ofValue("addon_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(0));
        assertThat(output.getVariables().isEmpty(), is(true));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons/addon_abc123/env", "[]");

        var task = TestableGetEnv.builder()
            .id("get-addon-env-auth-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .addonId(Property.ofValue("addon_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons/addon_abc123/env")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenAddonIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableGetEnv.builder()
            .id("get-addon-env-missing-id-test")
            .type(GetEnv.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("addonId is required"));
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
