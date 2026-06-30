package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ListAddonsTest {

    @Inject
    RunContextFactory runContextFactory;

    MockWebServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void parseAddonListResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
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
                """));

        var task = TestableListAddons.builder()
            .id("list-addons-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

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
    void handleEmptyAddonList() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableListAddons.builder()
            .id("list-addons-empty-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getAddons(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableListAddons.builder()
            .id("list-addons-auth-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization"), is("Bearer my-secret-token"));
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableListAddons.builder()
            .id("list-addons-org-path-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_abc123/addons"));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableListAddons.builder()
            .id("list-addons-self-path-test")
            .type(ListAddons.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/self/addons"));
        assertThat(request.getPath(), not(containsString("/organisations/")));
    }
}
