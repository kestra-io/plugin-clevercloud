package io.kestra.plugin.clevercloud.logs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.logs.model.DrainType;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateDrainTest extends AbstractClevercloudTest {

    private TestableCreateDrain.TestableCreateDrainBuilder<?, ?> baseBuilder(String baseUrl) {
        return TestableCreateDrain.builder()
            .id("create-drain-test-" + System.nanoTime())
            .type(CreateDrain.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .testBaseUrl(baseUrl);
    }

    @Test
    void createsDatadogDrain(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("""
                {"id": "drain_new", "kind": "LOG", "status": {"date": "2024-01-01T00:00:00Z", "status": "CREATED", "authorId": "user_test"}}
                """)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .drainType(Property.ofValue(DrainType.DATADOG))
            .url(Property.ofValue("https://http-intake.logs.datadoghq.com/api/v2/logs"))
            .build();
        var output = task.run(runContext());

        assertThat(output.getId(), is("drain_new"));
        assertThat(output.getKind(), is("LOG"));
        assertThat(output.getStatus(), is("CREATED"));

        verify(postRequestedFor(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .withHeader("Authorization", equalTo("Bearer test-api-token"))
            .withRequestBody(matchingJsonPath("$.kind", equalTo("LOG")))
            .withRequestBody(matchingJsonPath("$.recipient.type", equalTo("DATADOG")))
            .withRequestBody(matchingJsonPath("$.recipient.url", equalTo("https://http-intake.logs.datadoghq.com/api/v2/logs"))));
    }

    @Test
    void includesElasticsearchCredentialsAndIndexInBody(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("""
                {"id": "drain_es", "kind": "LOG", "status": {"date": "2024-01-01T00:00:00Z", "status": "CREATED", "authorId": "user_test"}}
                """)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .drainType(Property.ofValue(DrainType.ELASTICSEARCH))
            .url(Property.ofValue("https://es.example.com:9200"))
            .username(Property.ofValue("elastic"))
            .password(Property.ofValue("s3cr3t"))
            .indexPrefix(Property.ofValue("app-logs"))
            .build();
        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .withRequestBody(matchingJsonPath("$.recipient.username", equalTo("elastic")))
            .withRequestBody(matchingJsonPath("$.recipient.password", equalTo("s3cr3t")))
            .withRequestBody(matchingJsonPath("$.recipient.index", equalTo("app-logs"))));
    }

    @Test
    void omitsUnrelatedFieldsForDatadog(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(okJson("""
                {"id": "drain_new", "kind": "LOG", "status": {"date": "2024-01-01T00:00:00Z", "status": "CREATED", "authorId": "user_test"}}
                """)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .drainType(Property.ofValue(DrainType.DATADOG))
            .url(Property.ofValue("https://http-intake.logs.datadoghq.com/api/v2/logs"))
            .apiKey(Property.ofValue("should-not-be-sent"))
            .build();
        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .withRequestBody(notMatching(".*apiKey.*")));
    }

    @Test
    void throwsCleanExceptionOn400WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains"))
            .willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"super-secret-internal-error\"}")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl())
            .drainType(Property.ofValue(DrainType.DATADOG))
            .url(Property.ofValue("https://http-intake.logs.datadoghq.com/api/v2/logs"))
            .build();

        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("400"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
    }
}
