package io.kestra.plugin.clevercloud.applications;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.applications.model.Application;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest extends AbstractClevercloudTest {

    @Test
    void parseApplicationListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications", """
            [
              {
                "id": "app_cb34839b-18d9-4975-ac8c-946bd1576361",
                "name": "kestra",
                "description": "kestra app",
                "zone": "par",
                "zoneId": "aad32a21-24f8-40b3-a750-baab218d927b",
                "state": "SHOULD_BE_UP",
                "deployUrl": "https://push.clever-cloud.com/kestra.git",
                "instance": {
                  "type": "node",
                  "version": "20260617",
                  "variant": {"id": "395103fb-d6e2-4fdd-93bc-bc99146f1ea2", "slug": "node", "name": "Node.js & Bun"},
                  "minInstances": 1,
                  "maxInstances": 2,
                  "minFlavor": {"name": "XS"},
                  "maxFlavor": {"name": "S"}
                },
                "extraField": "should be ignored"
              }
            ]
            """);

        var task = TestableList.builder()
            .id("list-apps-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1));
        assertThat(output.getApplications(), hasSize(1));

        var first = output.getApplications().getFirst();
        assertThat(first.getId(), is("app_cb34839b-18d9-4975-ac8c-946bd1576361"));
        assertThat(first.getState(), is("SHOULD_BE_UP"));
        assertThat(first.getDeployUrl(), is("https://push.clever-cloud.com/kestra.git"));
        assertThat(first.getInstance().getType(), is("node"));
        assertThat(first.getInstance().getMinInstances(), is(1));
        assertThat(first.getInstance().getMaxFlavor().getName(), is("S"));
    }

    @Test
    void handleEmptyApplicationList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_empty/applications", "[]");

        var task = TestableList.builder()
            .id("list-apps-empty-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(0));
        assertThat(output.getApplications(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications", "[]");

        var task = TestableList.builder()
            .id("list-apps-auth-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_test/applications")), "my-secret-token");
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/self/applications", "[]");

        var task = TestableList.builder()
            .id("list-apps-self-path-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/self/applications")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void fetchTypeFetchOneReturnsFirstApplication(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications", """
            [
              {"id": "app_one-1", "name": "first"},
              {"id": "app_one-2", "name": "second"}
            ]
            """);

        var task = TestableList.builder()
            .id("list-apps-fetch-one-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getApplication(), is(notNullValue()));
        assertThat(output.getApplication().getId(), is("app_one-1"));
        assertThat(output.getApplications(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/applications", """
            [
              {"id": "app_store-1", "name": "first"},
              {"id": "app_store-2", "name": "second"}
            ]
            """);

        var task = TestableList.builder()
            .id("list-apps-store-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getApplications(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, Application.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getId(), is("app_store-1"));
            assertThat(stored.get(1).getId(), is("app_store-2"));
        }
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"internal secret stack trace details\"}")));

        var task = TestableList.builder()
            .id("list-apps-500-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), is(not(is("internal secret stack trace details"))));
        assertThat(ex.getMessage(), org.hamcrest.Matchers.containsString("500"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableList extends List {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
