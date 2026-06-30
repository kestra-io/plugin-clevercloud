package io.kestra.plugin.clevercloud.deployments;

import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import jakarta.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                """));

        var task = TestableList.builder()
            .id("list-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(mockServer.url("").toString())
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
    void appliesDefaultLimitParameter() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableList.builder()
            .id("list-default-limit-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("limit=50"));
    }

    @Test
    void applyCustomLimitParameter() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableList.builder()
            .id("list-limit-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .limit(Property.ofValue(5))
            .testBaseUrl(mockServer.url("").toString())
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

        var task = TestableList.builder()
            .id("list-empty-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getDeployments(), is(empty()));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"super-secret-internal-error\",\"token\":\"leaked-value\"}"));

        var task = TestableList.builder()
            .id("list-500-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
        assertThat(ex.getMessage(), not(containsString("leaked-value")));
    }

    @Test
    void sendsBearerAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableList.builder()
            .id("list-auth-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization"), is("Bearer my-secret-token"));
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableList.builder()
            .id("list-org-path-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_myorg"))
            .applicationId(Property.ofValue("app_myapp"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_myorg/applications/app_myapp/deployments"));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableList.builder()
            .id("list-self-path-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_myapp"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/self/applications/app_myapp/deployments"));
        assertThat(request.getPath(), not(containsString("/organisations/")));
    }

    @Test
    void fetchTypeFetchReturnsFullListInOutput() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {"uuid": "deployment_fetch-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"},
                  {"uuid": "deployment_fetch-2", "state": "WIP", "date": "1782127287203", "action": "DEPLOY"}
                ]
                """));

        var task = TestableList.builder()
            .id("list-fetch-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getDeployments(), hasSize(2));
        assertThat(output.getDeployment(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeFetchOneReturnsFirstDeployment() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {"uuid": "deployment_one-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"},
                  {"uuid": "deployment_one-2", "state": "WIP", "date": "1782127287203", "action": "DEPLOY"}
                ]
                """));

        var task = TestableList.builder()
            .id("list-fetch-one-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .testBaseUrl(mockServer.url("").toString())
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
    void fetchTypeStoreWritesIonFileToInternalStorage() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {"uuid": "deployment_store-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"},
                  {"uuid": "deployment_store-2", "state": "WIP", "date": "1782127287203", "action": "DEPLOY"}
                ]
                """));

        var task = TestableList.builder()
            .id("list-store-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(mockServer.url("").toString())
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
    void fetchTypeNoneReturnsOnlyCount() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {"uuid": "deployment_none-1", "state": "OK", "date": "1782127329927", "action": "DEPLOY"}
                ]
                """));

        var task = TestableList.builder()
            .id("list-none-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_test"))
            .fetchType(Property.ofValue(FetchType.NONE))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(1));
        assertThat(output.getDeployments(), is(nullValue()));
        assertThat(output.getDeployment(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }
}
