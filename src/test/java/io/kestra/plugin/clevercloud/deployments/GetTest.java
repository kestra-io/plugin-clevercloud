package io.kestra.plugin.clevercloud.deployments;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class GetTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void fetchDeploymentDetails(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_5ea89906-3651-4ffc-989a-bf54db93a9c8"))
            .willReturn(okJson("""
                {
                  "uuid": "deployment_5ea89906-3651-4ffc-989a-bf54db93a9c8",
                  "state": "OK",
                  "commit": "cafe0001",
                  "date": "1782127329927",
                  "action": "DEPLOY",
                  "cause": "Git"
                }
                """)));

        var task = TestableGet.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_5ea89906-3651-4ffc-989a-bf54db93a9c8"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
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
    void requestUrlContainsDeploymentId(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_abc/applications/app_xyz/deployments/deployment_d1"))
            .willReturn(okJson("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """)));

        var task = TestableGet.builder()
            .id("get-url-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc"))
            .applicationId(Property.ofValue("app_xyz"))
            .deploymentId(Property.ofValue("deployment_d1"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_abc/applications/app_xyz/deployments/deployment_d1")));
    }

    @Test
    void nullCommitForNonGitDeploy(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_cb6f0557-7c84-46b2-837c-e121e54cde78"))
            .willReturn(okJson("""
                {
                  "uuid": "deployment_cb6f0557-7c84-46b2-837c-e121e54cde78",
                  "state": "OK",
                  "date": "1782127328326",
                  "action": "UNDEPLOY",
                  "cause": "Killed/Moderated"
                }
                """)));

        var task = TestableGet.builder()
            .id("get-undeploy-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_cb6f0557-7c84-46b2-837c-e121e54cde78"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getState(), is("OK"));
        assertThat(output.getAction(), is("UNDEPLOY"));
        assertThat(output.getCommit(), is(nullValue()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_test/deployments/deployment_d1"))
            .willReturn(okJson("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """)));

        var task = TestableGet.builder()
            .id("get-auth-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("my-bearer-token"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d1"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_test/deployments/deployment_d1"))
            .withHeader("Authorization", equalTo("Bearer my-bearer-token")));
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d1"))
            .willReturn(okJson("""
                {"uuid":"deployment_d1","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """)));

        var task = TestableGet.builder()
            .id("get-trailing-slash-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .deploymentId(Property.ofValue("deployment_d1"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/applications/app_test/deployments/deployment_d1")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_mine/deployments/deployment_d2"))
            .willReturn(okJson("""
                {"uuid":"deployment_d2","state":"OK","date":"1782127329927","action":"DEPLOY"}
                """)));

        var task = TestableGet.builder()
            .id("get-self-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_mine"))
            .deploymentId(Property.ofValue("deployment_d2"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_mine/deployments/deployment_d2")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
    }
}
