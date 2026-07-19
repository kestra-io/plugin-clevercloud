package io.kestra.plugin.clevercloud.addons;

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

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractClevercloudTest {

    @Test
    void fetchesAddonDetails(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons/addon_abc123", """
            {
              "id": "addon_abc123",
              "name": "my-postgres",
              "realId": "postgresql_real_id_001",
              "region": "par",
              "provider": {"id": "postgresql-addon", "name": "PostgreSQL"},
              "plan": {"id": "plan_dev", "name": "DEV"},
              "creationDate": 1782127329927,
              "configKeys": ["POSTGRESQL_ADDON_URI"]
            }
            """);

        var task = TestableGet.builder()
            .id("get-addon-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .addonId(Property.ofValue("addon_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("addon_abc123"));
        assertThat(output.getName(), is("my-postgres"));
        assertThat(output.getRealId(), is("postgresql_real_id_001"));
        assertThat(output.getRegion(), is("par"));
        assertThat(output.getProviderId(), is("postgresql-addon"));
        assertThat(output.getProviderName(), is("PostgreSQL"));
        assertThat(output.getPlanId(), is("plan_dev"));
        assertThat(output.getPlanName(), is("DEV"));
        assertThat(output.getCreationDate(), is(Instant.ofEpochMilli(1782127329927L)));
        assertThat(output.getConfigKeys(), hasSize(1));
        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons/addon_abc123")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/self/addons/addon_personal", "{\"id\": \"addon_personal\"}");

        var task = TestableGet.builder()
            .id("get-addon-self-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .addonId(Property.ofValue("addon_personal"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/self/addons/addon_personal")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void throwsClearExceptionWhenAddonIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableGet.builder()
            .id("get-addon-missing-id-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("addonId is required"));
    }

    @Test
    void throwsCleanExceptionOn404WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons/addon_missing"))
            .willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableGet.builder()
            .id("get-addon-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .addonId(Property.ofValue("addon_missing"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), not(containsString("internal secret stack trace details")));
        assertThat(ex.getMessage(), containsString("404"));
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
