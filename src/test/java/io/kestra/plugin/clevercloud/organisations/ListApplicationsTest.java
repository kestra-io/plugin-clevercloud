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
class ListApplicationsTest {

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
    void parseApplicationListResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "id": "app_cb34839b-18d9-4975-ac8c-946bd1576361",
                    "name": "kestra",
                    "description": "kestra app",
                    "zone": "par",
                    "zoneId": "aad32a21-24f8-40b3-a750-baab218d927b",
                    "instance": {
                      "type": "node",
                      "version": "20260617",
                      "variant": {
                        "id": "395103fb-d6e2-4fdd-93bc-bc99146f1ea2",
                        "slug": "node",
                        "name": "Node.js & Bun"
                      }
                    },
                    "extraField": "should be ignored"
                  },
                  {
                    "id": "app_def456",
                    "name": "api-gateway",
                    "description": "REST API gateway",
                    "zone": "par",
                    "zoneId": "aad32a21-24f8-40b3-a750-baab218d927b",
                    "instance": {
                      "type": "java",
                      "version": "21",
                      "variant": {
                        "id": "java-variant-id",
                        "slug": "java",
                        "name": "Java"
                      }
                    }
                  }
                ]
                """));

        var task = ListApplications.builder()
            .id("list-apps-test")
            .type(ListApplications.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getApplications(), hasSize(2));

        var first = output.getApplications().getFirst();
        assertThat(first.getId(), is("app_cb34839b-18d9-4975-ac8c-946bd1576361"));
        assertThat(first.getName(), is("kestra"));
        assertThat(first.getZone(), is("par"));
        assertThat(first.getInstance(), is(notNullValue()));
        assertThat(first.getInstance().getType(), is("node"));
        assertThat(first.getInstance().getVariant().getSlug(), is("node"));

        var second = output.getApplications().get(1);
        assertThat(second.getId(), is("app_def456"));
        assertThat(second.getInstance().getType(), is("java"));
    }

    @Test
    void handleEmptyApplicationList() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = ListApplications.builder()
            .id("list-apps-empty-test")
            .type(ListApplications.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_empty"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getApplications(), is(empty()));
    }
}
