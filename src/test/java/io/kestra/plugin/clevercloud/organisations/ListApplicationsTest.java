package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.organisations.model.Application;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class ListApplicationsTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void parseApplicationListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("""
                [
                  {
                    "id": "app_cb34839b-18d9-4975-ac8c-946bd1576361",
                    "name": "kestra",
                    "description": "kestra app",
                    "zone": "par",
                    "zoneId": "aad32a21-24f8-40b3-a750-baab218d927b",
                    "instance": {
                      "type": "node",
                      "version": "20260617",
                      "variant": {
                        "id": "395103fb-d6e2-4fdd-93bc-bc99146f1ea2",
                        "slug": "node",
                        "name": "Node.js & Bun"
                      }
                    },
                    "extraField": "should be ignored"
                  },
                  {
                    "id": "app_def456",
                    "name": "api-gateway",
                    "description": "REST API gateway",
                    "zone": "par",
                    "zoneId": "aad32a21-24f8-40b3-a750-baab218d927b",
                    "instance": {
                      "type": "java",
                      "version": "21",
                      "variant": {
                        "id": "java-variant-id",
                        "slug": "java",
                        "name": "Java"
                      }
                    }
                  }
                ]
                """)));

        var task = TestableListApplications.builder()
            .id("list-apps-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getApplications(), hasSize(2));

        var first = output.getApplications().getFirst();
        assertThat(first.getId(), is("app_cb34839b-18d9-4975-ac8c-946bd1576361"));
        assertThat(first.getName(), is("kestra"));
        assertThat(first.getZone(), is("par"));
        assertThat(first.getInstance(), is(notNullValue()));
        assertThat(first.getInstance().getType(), is("node"));
        assertThat(first.getInstance().getVariant().getSlug(), is("node"));

        var second = output.getApplications().get(1);
        assertThat(second.getId(), is("app_def456"));
        assertThat(second.getInstance().getType(), is("java"));
    }

    @Test
    void handleEmptyApplicationList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_empty/applications"))
            .willReturn(okJson("[]")));

        var task = TestableListApplications.builder()
            .id("list-apps-empty-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getApplications(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("[]")));

        var task = TestableListApplications.builder()
            .id("list-apps-auth-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications"))
            .withHeader("Authorization", equalTo("Bearer my-secret-token")));
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_abc123/applications"))
            .willReturn(okJson("[]")));

        var task = TestableListApplications.builder()
            .id("list-apps-org-path-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123/applications")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self/applications"))
            .willReturn(okJson("[]")));

        var task = TestableListApplications.builder()
            .id("list-apps-self-path-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self/applications")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("[]")));

        var task = TestableListApplications.builder()
            .id("list-apps-trailing-slash-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/applications")));
    }

    @Test
    void fetchTypeFetchOneReturnsFirstApplication(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("""
                [
                  {"id": "app_one-1", "name": "first"},
                  {"id": "app_one-2", "name": "second"}
                ]
                """)));

        var task = TestableListApplications.builder()
            .id("list-apps-fetch-one-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getApplication(), is(notNullValue()));
        assertThat(output.getApplication().getId(), is("app_one-1"));
        assertThat(output.getApplications(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("""
                [
                  {"id": "app_store-1", "name": "first"},
                  {"id": "app_store-2", "name": "second"}
                ]
                """)));

        var task = TestableListApplications.builder()
            .id("list-apps-store-test")
            .type(ListApplications.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getApplications(), is(nullValue()));
        assertThat(output.getApplication(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, Application.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getId(), is("app_store-1"));
            assertThat(stored.get(1).getId(), is("app_store-2"));
        }
    }
}
