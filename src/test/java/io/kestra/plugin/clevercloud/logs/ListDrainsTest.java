package io.kestra.plugin.clevercloud.logs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListDrainsTest extends AbstractClevercloudTest {

    private TestableListDrains.TestableListDrainsBuilder<?, ?> baseBuilder(String baseUrl) {
        return TestableListDrains.builder()
            .id("list-drains-test-" + System.nanoTime())
            .type(ListDrains.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(baseUrl);
    }

    @Test
    void listsConfiguredDrains(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("""
                [
                  {
                    "id": "drain_1",
                    "applicationId": "app_test",
                    "kind": "LOG",
                    "status": "ENABLED",
                    "recipient": {"type": "DATADOG", "url": "https://http-intake.logs.datadoghq.com/api/v2/logs"}
                  }
                ]
                """)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1));
        assertThat(output.getDrains(), hasSize(1));
        assertThat(output.getDrains().getFirst().getId(), is("drain_1"));
        assertThat(output.getDrains().getFirst().getStatus(), is("ENABLED"));
        assertThat(output.getDrains().getFirst().getRecipient().getType(), is("DATADOG"));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("[]")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();
        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .withHeader("Authorization", equalTo("Bearer test-api-token")));
    }

    @Test
    void fetchTypeFetchOneReturnsFirstDrain(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("""
                [
                  {"id": "drain_a", "kind": "LOG", "status": "ENABLED"},
                  {"id": "drain_b", "kind": "ACCESSLOG", "status": "CREATED"}
                ]
                """)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getDrain(), is(notNullValue()));
        assertThat(output.getDrain().getId(), is("drain_a"));
        assertThat(output.getDrains(), is(nullValue()));
    }

    @Test
    void handlesEmptyDrainList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("[]")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();
        var output = task.run(runContext());

        assertThat(output.getTotal(), is(0));
        assertThat(output.getDrains(), is(empty()));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"super-secret-internal-error\"}")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();

        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
    }
}
