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
class GetTest {

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
    void fetchDeploymentDetails() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "uuid": "deploy-789",
                  "state": "DEPLOY_OK",
                  "commit": "cafe0001",
                  "date": "2024-02-01T09:00:00Z",
                  "endDate": "2024-02-01T09:03:00Z",
                  "action": "DEPLOY",
                  "cause": "GIT"
                }
                """));

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deploy-789"))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getDeploymentId(), is("deploy-789"));
        assertThat(output.getState(), is("DEPLOY_OK"));
        assertThat(output.getCommit(), is("cafe0001"));
        assertThat(output.getStartDate(), is("2024-02-01T09:00:00Z"));
        assertThat(output.getEndDate(), is("2024-02-01T09:03:00Z"));
    }

    @Test
    void requestUrlContainsDeploymentId() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"d1","state":"DEPLOY_OK","commit":"a1b2c3d4"}
                """));

        var task = Get.builder()
            .id("get-url-test")
            .type(Get.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_abc"))
            .applicationId(Property.of("app_xyz"))
            .deploymentId(Property.of("d1"))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_abc/applications/app_xyz/deployments/d1"));
    }

    @Test
    void handlesNullEndDate() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "uuid": "deploy-wip",
                  "state": "WIP",
                  "commit": "deadbeef",
                  "date": "2024-02-01T10:00:00Z"
                }
                """));

        var task = Get.builder()
            .id("get-wip-test")
            .type(Get.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deploy-wip"))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getState(), is("WIP"));
        assertThat(output.getEndDate(), is(nullValue()));
    }
}
