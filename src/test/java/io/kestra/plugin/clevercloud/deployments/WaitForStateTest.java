package io.kestra.plugin.clevercloud.deployments;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.clevercloud.deployments.model.DeploymentState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class WaitForStateTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void returnsWhenTargetStateReached(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d1"))
            .willReturn(okJson("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY","commit":"abc123"}
                """)));

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
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getDeploymentId(), is("deployment_d1"));
        assertThat(output.getState(), is("OK"));
    }

    @Test
    void throwsWhenDeploymentReachesWrongTerminalState(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d2"))
            .willReturn(okJson("""
                {"uuid":"deployment_d2","state":"FAIL","date":"1782127329927","action":"DEPLOY","commit":"bad1234"}
                """)));

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
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("FAIL"));
        assertThat(ex.getMessage(), containsString("expected OK"));
    }

    @Test
    void throwsOnCancelledState(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d5"))
            .willReturn(okJson("""
                {"uuid":"deployment_d5","state":"CANCELLED","date":"1782127287203","action":"DEPLOY","commit":"abc"}
                """)));

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
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("CANCELLED"));
        assertThat(ex.getMessage(), containsString("expected OK"));
    }

    @Test
    void pollsUntilStateChanges(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d3"))
            .inScenario("poll-until-ok")
            .whenScenarioStateIs("Started")
            .willReturn(okJson("""
                {"uuid":"deployment_d3","state":"WIP","date":"1782127287203","action":"DEPLOY","commit":"poll0001"}
                """))
            .willSetStateTo("second-poll"));

        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d3"))
            .inScenario("poll-until-ok")
            .whenScenarioStateIs("second-poll")
            .willReturn(okJson("""
                {"uuid":"deployment_d3","state":"OK","date":"1782127287203","action":"DEPLOY","commit":"poll0001"}
                """)));

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
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        verify(2, getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d3")));
        assertThat(output.getState(), is("OK"));
    }

    @Test
    void throwsOnTimeout(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d4"))
            .willReturn(okJson("""
                {"uuid":"deployment_d4","state":"WIP","date":"1782127287203","action":"DEPLOY","commit":"stuck0001"}
                """)));

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
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Timed out"));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d6"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"super-secret-internal-error\",\"token\":\"leaked-value\"}")));

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
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
        assertThat(ex.getMessage(), not(containsString("leaked-value")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_personal/deployments/deployment_d7"))
            .willReturn(okJson("""
                {"uuid":"deployment_d7","state":"OK","date":"1782127329927","action":"DEPLOY","commit":"abc"}
                """)));

        var task = TestableWaitForState.builder()
            .id("wait-self-test")
            .type(WaitForState.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_personal"))
            .deploymentId(Property.ofValue("deployment_d7"))
            .targetState(Property.ofValue(DeploymentState.OK))
            .pollInterval(Property.ofValue(Duration.ofMillis(10)))
            .timeout(Property.ofValue(Duration.ofSeconds(5)))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_personal/deployments/deployment_d7")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
    }
}
