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
                  "uuid": "deployment_5ea89906-3651-4ffc-989a-bf54db93a9c8",
                  "state": "OK",
                  "commit": "cafe0001",
                  "date": "1782127329927",
                  "action": "DEPLOY",
                  "cause": "Git"
                }
                """));

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_5ea89906-3651-4ffc-989a-bf54db93a9c8"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getDeploymentId(), is("deployment_5ea89906-3651-4ffc-989a-bf54db93a9c8"));
        assertThat(output.getState(), is("OK"));
        assertThat(output.getCommit(), is("cafe0001"));
        assertThat(output.getDate(), is("1782127329927"));
        assertThat(output.getAction(), is("DEPLOY"));
        assertThat(output.getCause(), is("Git"));
    }

    @Test
    void requestUrlContainsDeploymentId() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """));

        var task = Get.builder()
            .id("get-url-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc"))
            .applicationId(Property.ofValue("app_xyz"))
            .deploymentId(Property.ofValue("deployment_d1"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_abc/applications/app_xyz/deployments/deployment_d1"));
    }

    @Test
    void nullCommitForNonGitDeploy() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "uuid": "deployment_cb6f0557-7c84-46b2-837c-e121e54cde78",
                  "state": "OK",
                  "date": "1782127328326",
                  "action": "UNDEPLOY",
                  "cause": "Killed/Moderated"
                }
                """));

        var task = Get.builder()
            .id("get-undeploy-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_cb6f0557-7c84-46b2-837c-e121e54cde78"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getState(), is("OK"));
        assertThat(output.getAction(), is("UNDEPLOY"));
        assertThat(output.getCommit(), is(nullValue()));
    }

    @Test
    void sendsBearerAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """));

        var task = Get.builder()
            .id("get-auth-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("my-bearer-token"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d1"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization"), is("Bearer my-bearer-token"));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d2","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """));

        var task = Get.builder()
            .id("get-self-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_mine"))
            .deploymentId(Property.ofValue("deployment_d2"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/self/applications/app_mine/deployments/deployment_d2"));
        assertThat(request.getPath(), not(containsString("/organisations/")));
    }
}
