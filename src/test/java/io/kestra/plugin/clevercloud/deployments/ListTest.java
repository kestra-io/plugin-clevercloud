package io.kestra.plugin.clevercloud.deployments;

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
class ListTest {

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
    void parseDeploymentListResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "uuid": "deploy-123",
                    "state": "DEPLOY_OK",
                    "commit": "abc1234",
                    "date": "2024-01-15T10:00:00Z",
                    "endDate": "2024-01-15T10:05:00Z",
                    "action": "DEPLOY",
                    "cause": "GIT"
                  },
                  {
                    "uuid": "deploy-456",
                    "state": "WIP",
                    "commit": "def5678",
                    "date": "2024-01-15T11:00:00Z",
                    "action": "DEPLOY"
                  }
                ]
                """));

        var task = List.builder()
            .id("list-test")
            .type(List.class.getName())
            .consumerKey(Property.of("test-consumer-key"))
            .consumerSecret(Property.of("test-consumer-secret"))
            .token(Property.of("test-token"))
            .tokenSecret(Property.of("test-token-secret"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getDeployments(), hasSize(2));
        assertThat(output.getDeployments().getFirst().getId(), is("deploy-123"));
        assertThat(output.getDeployments().getFirst().getState(), is("DEPLOY_OK"));
        assertThat(output.getDeployments().getFirst().getCommit(), is("abc1234"));
        assertThat(output.getDeployments().get(1).getId(), is("deploy-456"));
    }

    @Test
    void applyLimitParameter() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = List.builder()
            .id("list-limit-test")
            .type(List.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .limit(Property.of(5))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("limit=5"));
    }

    @Test
    void handleEmptyDeploymentList() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = List.builder()
            .id("list-empty-test")
            .type(List.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getDeployments(), is(empty()));
    }
}
