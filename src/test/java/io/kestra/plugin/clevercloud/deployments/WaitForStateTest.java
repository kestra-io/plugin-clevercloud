package io.kestra.plugin.clevercloud.deployments;

import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.clevercloud.deployments.model.DeploymentState;
import jakarta.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class WaitForStateTest {

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
    void returnsWhenTargetStateReached() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY","commit":"abc123"}
                """));

        var task = TestableWaitForState.builder()
            .id("wait-ok-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d1"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getDeploymentId(), is("deployment_d1"));
        assertThat(output.getState(), is("OK"));
    }

    @Test
    void throwsWhenDeploymentReachesWrongTerminalState() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d2","state":"FAIL","date":"1782127329927","action":"DEPLOY","commit":"bad1234"}
                """));

        var task = TestableWaitForState.builder()
            .id("wait-fail-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d2"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("FAIL"));
        assertThat(ex.getMessage(), containsString("expected OK"));
    }

    @Test
    void throwsOnCancelledState() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d5","state":"CANCELLED","date":"1782127287203","action":"DEPLOY","commit":"abc"}
                """));

        var task = TestableWaitForState.builder()
            .id("wait-cancelled-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d5"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("CANCELLED"));
        assertThat(ex.getMessage(), containsString("expected OK"));
    }

    @Test
    void pollsUntilStateChanges() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d3","state":"WIP","date":"1782127287203","action":"DEPLOY","commit":"poll0001"}
                """));
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d3","state":"OK","date":"1782127287203","action":"DEPLOY","commit":"poll0001"}
                """));

        var task = TestableWaitForState.builder()
            .id("wait-poll-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d3"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(50)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(mockServer.getRequestCount(), is(2));
        assertThat(output.getState(), is("OK"));
    }

    @Test
    void throwsOnTimeout() {
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"uuid":"deployment_d4","state":"WIP","date":"1782127287203","action":"DEPLOY","commit":"stuck0001"}
                    """));
        }

        var task = TestableWaitForState.builder()
            .id("wait-timeout-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d4"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofMillis(50)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Timed out"));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"super-secret-internal-error\",\"token\":\"leaked-value\"}"));

        var task = TestableWaitForState.builder()
            .id("wait-500-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d6"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
        assertThat(ex.getMessage(), not(containsString("leaked-value")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"deployment_d7","state":"OK","date":"1782127329927","action":"DEPLOY","commit":"abc"}
                """));

        var task = TestableWaitForState.builder()
            .id("wait-self-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_personal"))
            .deploymentId(Property.ofValue("deployment_d7"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/self/applications/app_personal/deployments/"));
        assertThat(request.getPath(), not(containsString("/organisations/")));
    }
}
