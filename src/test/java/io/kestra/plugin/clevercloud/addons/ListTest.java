package io.kestra.plugin.clevercloud.addons;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.addons.model.Addon;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest extends AbstractClevercloudTest {

    @Test
    void parseAddonListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", """
            [
              {
                "id": "addon_postgres_abc",
                "name": "my-postgres",
                "realId": "postgresql_real_id_001",
                "region": "par",
                "zoneId": "aad32a21-24f8-40b3-a750-baab218d927b",
                "provider": {
                  "id": "postgresql-addon",
                  "name": "PostgreSQL",
                  "shortDesc": "Managed PostgreSQL",
                  "logoUrl": "https://example.com/pg.svg"
                },
                "plan": {
                  "id": "plan_dev",
                  "slug": "dev",
                  "name": "DEV"
                },
                "creationDate": 1782127329927,
                "configKeys": ["POSTGRESQL_ADDON_URI"],
                "extraField": "should be ignored"
              }
            ]
            """);

        var task = TestableList.builder()
            .id("list-addons-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1));
        assertThat(output.getAddons(), hasSize(1));

        var addon = output.getAddons().getFirst();
        assertThat(addon.getId(), is("addon_postgres_abc"));
        assertThat(addon.getName(), is("my-postgres"));
        assertThat(addon.getRealId(), is("postgresql_real_id_001"));
        assertThat(addon.getRegion(), is("par"));
        assertThat(addon.getProvider(), is(notNullValue()));
        assertThat(addon.getProvider().getId(), is("postgresql-addon"));
        assertThat(addon.getProvider().getName(), is("PostgreSQL"));
        assertThat(addon.getPlan(), is(notNullValue()));
        assertThat(addon.getPlan().getSlug(), is("dev"));
        assertThat(addon.getCreationDate(), is(1782127329927L));
        assertThat(addon.getConfigKeys(), hasSize(1));
    }

    @Test
    void handleEmptyAddonList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_empty/addons", "[]");

        var task = TestableList.builder()
            .id("list-addons-empty-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(0));
        assertThat(output.getAddons(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", "[]");

        var task = TestableList.builder()
            .id("list-addons-auth-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")), "my-secret-token");
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/self/addons", "[]");

        var task = TestableList.builder()
            .id("list-addons-self-path-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/self/addons")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void fetchTypeFetchOneReturnsFirstAddon(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", """
            [
              {"id": "addon_one-1", "name": "first"},
              {"id": "addon_one-2", "name": "second"}
            ]
            """);

        var task = TestableList.builder()
            .id("list-addons-fetch-one-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getAddon(), is(notNullValue()));
        assertThat(output.getAddon().getId(), is("addon_one-1"));
        assertThat(output.getAddons(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", """
            [
              {"id": "addon_store-1", "name": "first"},
              {"id": "addon_store-2", "name": "second"}
            ]
            """);

        var task = TestableList.builder()
            .id("list-addons-store-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getAddons(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, Addon.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getId(), is("addon_store-1"));
            assertThat(stored.get(1).getId(), is("addon_store-2"));
        }
    }

    @Test
    void fetchTypeNoneReturnsOnlyCount(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", """
            [
              {"id": "addon_none-1", "name": "first"}
            ]
            """);

        var task = TestableList.builder()
            .id("list-addons-none-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.NONE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1));
        assertThat(output.getAddons(), is(nullValue()));
        assertThat(output.getAddon(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableList.builder()
            .id("list-addons-500-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), is(not(is("internal secret stack trace details"))));
        assertThat(ex.getMessage(), org.hamcrest.Matchers.containsString("500"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableList extends List {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
