package io.kestra.plugin.clevercloud.deployments;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class ListTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void parseDeploymentListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {
                    "uuid": "deployment_abc123",
                    "state": "OK",
                    "commit": "abc1234",
                    "date": "1782127329927",
                    "action": "DEPLOY",
                    "cause": "Git"
                  },
                  {
                    "uuid": "deployment_def456",
                    "state": "WIP",
                    "commit": "def5678",
                    "date": "1782127287203",
                    "action": "DEPLOY",
                    "cause": "Git"
                  }
                ]
                """)));

        var task = TestableList.builder()
            .id("list-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getDeployments(), hasSize(2));
        assertThat(output.getDeployments().getFirst().getUuid(), is("deployment_abc123"));
        assertThat(output.getDeployments().getFirst().getState(), is("OK"));
        assertThat(output.getDeployments().getFirst().getCommit(), is("abc1234"));
        assertThat(output.getDeployments().getFirst().getDate(), is("1782127329927"));
        assertThat(output.getDeployments().getFirst().getAction(), is("DEPLOY"));
        assertThat(output.getDeployments().get(1).getUuid(), is("deployment_def456"));
        assertThat(output.getDeployments().get(1).getState(), is("WIP"));
    }

    @Test
    void appliesDefaultLimitParameter(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("[]")));

        var task = TestableList.builder()
            .id("list-default-limit-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .withQueryParam("limit", equalTo("50")));
    }

    @Test
    void applyCustomLimitParameter(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("[]")));

        var task = TestableList.builder()
            .id("list-limit-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .limit(Property.ofValue(5))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .withQueryParam("limit", equalTo("5")));
    }

    @Test
    void handleEmptyDeploymentList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(okJson("[]")));

        var task = TestableList.builder()
            .id("list-empty-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getDeployments(), is(empty()));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications/app_test/deployments"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"super-secret-internal-error\",\"token\":\"leaked-value\"}")));

        var task = TestableList.builder()
            .id("list-500-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
        assertThat(ex.getMessage(), not(containsString("leaked-value")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_test/deployments"))
            .willReturn(okJson("[]")));

        var task = TestableList.builder()
            .id("list-auth-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_test/deployments"))
            .withHeader("Authorization", equalTo("Bearer my-secret-token")));
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_myorg/applications/app_myapp/deployments"))
            .willReturn(okJson("[]")));

        var task = TestableList.builder()
            .id("list-org-path-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_myorg"))
            .applicationId(Property.ofValue("app_myapp"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_myorg/applications/app_myapp/deployments")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_myapp/deployments"))
            .willReturn(okJson("[]")));

        var task = TestableList.builder()
            .id("list-self-path-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_myapp"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self/applications/app_myapp/deployments")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
    }

    @Test
    void fetchTypeFetchReturnsFullListInOutput(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {"uuid": "deployment_fetch-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"},
                  {"uuid": "deployment_fetch-2", "state": "WIP", "date": "1782127287203", "action": "DEPLOY"}
                ]
                """)));

        var task = TestableList.builder()
            .id("list-fetch-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getDeployments(), hasSize(2));
        assertThat(output.getDeployment(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeFetchOneReturnsFirstDeployment(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {"uuid": "deployment_one-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"},
                  {"uuid": "deployment_one-2", "state": "WIP", "date": "1782127287203", "action": "DEPLOY"}
                ]
                """)));

        var task = TestableList.builder()
            .id("list-fetch-one-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getDeployment(), is(notNullValue()));
        assertThat(output.getDeployment().getUuid(), is("deployment_one-1"));
        assertThat(output.getDeployments(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {"uuid": "deployment_store-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"},
                  {"uuid": "deployment_store-2", "state": "WIP", "date": "1782127287203", "action": "DEPLOY"}
                ]
                """)));

        var task = TestableList.builder()
            .id("list-store-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getDeployments(), is(nullValue()));
        assertThat(output.getDeployment(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, Deployment.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getUuid(), is("deployment_store-1"));
            assertThat(stored.get(1).getUuid(), is("deployment_store-2"));
        }
    }

    @Test
    void fetchTypeNoneReturnsOnlyCount(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications/app_test/deployments"))
            .willReturn(okJson("""
                [
                  {"uuid": "deployment_none-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"}
                ]
                """)));

        var task = TestableList.builder()
            .id("list-none-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.NONE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(1));
        assertThat(output.getDeployments(), is(nullValue()));
        assertThat(output.getDeployment(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }
}
