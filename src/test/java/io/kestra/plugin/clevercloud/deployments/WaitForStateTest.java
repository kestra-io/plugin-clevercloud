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
                {"uuid":"d1","state":"DEPLOY_OK","commit":"abc123"}
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
            .deploymentId(Property.of("d1"))
            .targetState(Property.of("DEPLOY_OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getDeploymentId(), is("d1"));
        assertThat(output.getState(), is("DEPLOY_OK"));
    }

    @Test
    void throwsWhenDeploymentReachesWrongTerminalState() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"d2","state":"DEPLOY_FAILED","commit":"bad1234"}
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
            .deploymentId(Property.of("d2"))
            .targetState(Property.of("DEPLOY_OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("DEPLOY_FAILED"));
        assertThat(ex.getMessage(), containsString("expected DEPLOY_OK"));
    }

    @Test
    void pollsUntilStateChanges() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"d3","state":"WIP","commit":"poll0001"}
                """));
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"uuid":"d3","state":"DEPLOY_OK","commit":"poll0001"}
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
            .deploymentId(Property.of("d3"))
            .targetState(Property.of("DEPLOY_OK"))
            .pollInterval(Property.of(Duration.ofMillis(50)))
            .timeout(Property.of(Duration.ofSeconds(5)))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(mockServer.getRequestCount(), is(2));
        assertThat(output.getState(), is("DEPLOY_OK"));
    }

    @Test
    void throwsOnTimeout() {
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"uuid":"d4","state":"WIP","commit":"stuck0001"}
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
            .deploymentId(Property.of("d4"))
            .targetState(Property.of("DEPLOY_OK"))
            .pollInterval(Property.of(Duration.ofMillis(10)))
            .timeout(Property.of(Duration.ofMillis(50)))
            .apiBaseUrl(Property.of(mockServer.url("/v4/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Timed out"));
    }
}
