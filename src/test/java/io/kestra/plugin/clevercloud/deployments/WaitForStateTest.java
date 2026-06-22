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

        var task = WaitForState.builder()
            .id("wait-ok-test")
            .type(WaitForState.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deployment_d1"))
            .targetState(Property.of("OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
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

        var task = WaitForState.builder()
            .id("wait-fail-test")
            .type(WaitForState.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deployment_d2"))
            .targetState(Property.of("OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
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

        var task = WaitForState.builder()
            .id("wait-cancelled-test")
            .type(WaitForState.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deployment_d5"))
            .targetState(Property.of("OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
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

        var task = WaitForState.builder()
            .id("wait-poll-test")
            .type(WaitForState.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deployment_d3"))
            .targetState(Property.of("OK"))
            .pollInterval(Property.of(Duration.ofMillis(50)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
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

        var task = WaitForState.builder()
            .id("wait-timeout-test")
            .type(WaitForState.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .applicationId(Property.of("app_test"))
            .deploymentId(Property.of("deployment_d4"))
            .targetState(Property.of("OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofMillis(50)))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Timed out"));
    }
}
