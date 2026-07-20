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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractClevercloudTest {

    private static final String PROVIDERS_CATALOG_JSON = """
        [
          {
            "id": "azimutt",
            "plans": [
              {"id": "plan_free-0001", "slug": "free", "name": "Free", "price": 0.0},
              {"id": "plan_solo-0001", "slug": "solo", "name": "Solo", "price": 9.0},
              {"id": "plan_team-0001", "slug": "team", "name": "Team", "price": 42.0}
            ]
          }
        ]
        """;

    @Test
    void provisionsAddonWithAllFields(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("""
                {
                  "id": "addon_new-0001",
                  "name": "my-postgres",
                  "region": "par",
                  "provider": {"id": "postgresql-addon"},
                  "plan": {"id": "plan_dev"},
                  "creationDate": 1782127329927
                }
                """)));

        var task = TestableCreate.builder()
            .id("create-addon-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .plan(Property.ofValue("plan_dev"))
            .region(Property.ofValue("par"))
            .name(Property.ofValue("my-postgres"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("addon_new-0001"));
        assertThat(output.getName(), is("my-postgres"));
        assertThat(output.getRegion(), is("par"));
        assertThat(output.getProviderId(), is("postgresql-addon"));
        assertThat(output.getPlanId(), is("plan_dev"));

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withRequestBody(containing("\"providerId\":\"postgresql-addon\""))
            .withRequestBody(containing("\"plan\":\"plan_dev\""))
            .withRequestBody(containing("\"region\":\"par\""))
            .withRequestBody(containing("\"name\":\"my-postgres\"")));
    }

    @Test
    void provisionsAddonWithOnlyRequiredFields(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/self/addons"))
            .willReturn(okJson("{\"id\": \"addon_new-0002\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-minimal-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .providerId(Property.ofValue("redis-addon"))
            .plan(Property.ofValue("plan_s"))
            .region(Property.ofValue("rbx"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("addon_new-0002"));
        verify(postRequestedFor(urlPathEqualTo("/self/addons"))
            .withRequestBody(containing("\"providerId\":\"redis-addon\""))
            .withRequestBody(containing("\"plan\":\"plan_s\""))
            .withRequestBody(containing("\"region\":\"rbx\"")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("{\"id\": \"addon_new\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-auth-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .plan(Property.ofValue("plan_dev"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenProviderIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableCreate.builder()
            .id("create-addon-missing-provider-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .plan(Property.ofValue("plan_dev"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("providerId is required"));
    }

    @Test
    void throwsClearExceptionWhenRegionMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableCreate.builder()
            .id("create-addon-missing-region-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .plan(Property.ofValue("plan_dev"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("region is required"));
    }

    @Test
    void resolvesPlanSlugToIdViaCatalog(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/products/addonproviders", PROVIDERS_CATALOG_JSON);
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("{\"id\": \"addon_new-0003\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-slug-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("azimutt"))
            .plan(Property.ofValue("free"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withRequestBody(containing("\"plan\":\"plan_free-0001\"")));
    }

    @Test
    void passesRawPlanIdThroughWithoutCatalogLookup(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("{\"id\": \"addon_new-0004\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-raw-id-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("azimutt"))
            .plan(Property.ofValue("plan_abcdef01-2345-6789-abcd-ef0123456789"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyNeverCalled("/products/addonproviders");
        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withRequestBody(containing("\"plan\":\"plan_abcdef01-2345-6789-abcd-ef0123456789\"")));
    }

    @Test
    void defaultsToCheapestPlanWhenOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/products/addonproviders", PROVIDERS_CATALOG_JSON);
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("{\"id\": \"addon_new-0005\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-cheapest-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("azimutt"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withRequestBody(containing("\"plan\":\"plan_free-0001\"")));
    }

    @Test
    void throwsClearExceptionForUnknownPlanSlug(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubGetJson("/products/addonproviders", PROVIDERS_CATALOG_JSON);

        var task = TestableCreate.builder()
            .id("create-addon-unknown-slug-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("azimutt"))
            .plan(Property.ofValue("bogus-plan"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Unknown plan 'bogus-plan'"));
        assertThat(ex.getMessage(), containsString("free"));
        assertThat(ex.getMessage(), containsString("solo"));
        assertThat(ex.getMessage(), containsString("team"));

        verify(0, postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")));
    }

    @Test
    void fallsBackToPublicCatalogWhenPrimaryUnreachable(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // /products/addonproviders is intentionally left unstubbed so WireMock returns 404 for the primary attempt.
        stubGetJson("/fallback/products/addonproviders", PROVIDERS_CATALOG_JSON);
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("{\"id\": \"addon_new-0006\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-fallback-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("azimutt"))
            .plan(Property.ofValue("solo"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .testFallbackUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/fallback/products/addonproviders")
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withRequestBody(containing("\"plan\":\"plan_solo-0001\"")));
    }

    @Test
    void throwsCleanExceptionOnFallbackErrorWithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // /products/addonproviders is intentionally left unstubbed so WireMock returns 404 for the primary attempt.
        stubFor(get(urlPathEqualTo("/fallback/products/addonproviders"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-fallback-error-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("azimutt"))
            .plan(Property.ofValue("solo"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .testFallbackUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/fallback/products/addonproviders")
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), not(containsString("internal secret stack trace details")));
        assertThat(ex.getMessage(), containsString("500"));

        verify(0, postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableCreate extends Create {

        private String testBaseUrl;
        private String testFallbackUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }

        @Override
        protected String providersCatalogFallbackUrl() {
            return testFallbackUrl;
        }
    }
}
