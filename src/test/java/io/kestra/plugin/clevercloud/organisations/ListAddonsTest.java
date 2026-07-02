package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.organisations.model.Addon;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class ListAddonsTest extends AbstractClevercloudTest {

    @Test
    void parseAddonListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", """
            [
              {
                "id": "addon_postgres_abc",
                "name": "my-postgres",
                "realId": "postgresql_real_id_001",
                "region": "par",
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
                "configKeys": ["POSTGRESQL_ADDON_URI"]
              }
            ]
            """);

        var task = TestableListAddons.builder()
            .id("list-addons-test")
            .type(ListAddons.class.getName())
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
    }

    @Test
    void handleEmptyAddonList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_empty/addons", "[]");

        var task = TestableListAddons.builder()
            .id("list-addons-empty-test")
            .type(ListAddons.class.getName())
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

        var task = TestableListAddons.builder()
            .id("list-addons-auth-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")), "my-secret-token");
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_abc123/addons", "[]");

        var task = TestableListAddons.builder()
            .id("list-addons-org-path-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123/addons")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/self/addons", "[]");

        var task = TestableListAddons.builder()
            .id("list-addons-self-path-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/self/addons")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("[]")));

        var task = TestableListAddons.builder()
            .id("list-addons-trailing-slash-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/addons")));
    }

    @Test
    void fetchTypeFetchOneReturnsFirstAddon(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/addons", """
            [
              {"id": "addon_one-1", "name": "first"},
              {"id": "addon_one-2", "name": "second"}
            ]
            """);

        var task = TestableListAddons.builder()
            .id("list-addons-fetch-one-test")
            .type(ListAddons.class.getName())
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

        var task = TestableListAddons.builder()
            .id("list-addons-store-test")
            .type(ListAddons.class.getName())
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
        assertThat(output.getAddon(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, Addon.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getId(), is("addon_store-1"));
            assertThat(stored.get(1).getId(), is("addon_store-2"));
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableListAddons extends ListAddons {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
