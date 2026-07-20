package io.kestra.plugin.clevercloud.logs;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteDrainTest extends AbstractClevercloudTest {

    private TestableDeleteDrain.TestableDeleteDrainBuilder<?, ?> baseBuilder(String baseUrl) {
        return TestableDeleteDrain.builder()
            .id("delete-drain-test-" + System.nanoTime())
            .type(DeleteDrain.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_test"))
            .drainId(Property.ofValue("drain_1"))
            .testBaseUrl(baseUrl);
    }

    @Test
    void deletesDrain(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains/drain_1"))
            .willReturn(aResponse().withStatus(204)));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();
        task.run(runContext());

        verify(deleteRequestedFor(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains/drain_1"))
            .withHeader("Authorization", equalTo("Bearer test-api-token")));
    }

    @Test
    void throwsCleanExceptionOn404WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(delete(urlPathEqualTo("/drains/organisations/orga_test/applications/app_test/drains/drain_1"))
            .willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"super-secret-internal-error\"}")));

        var task = baseBuilder(wireMockRuntimeInfo.getHttpBaseUrl()).build();

        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext()));
        assertThat(ex.getMessage(), containsString("404"));
        assertThat(ex.getMessage(), not(containsString("super-secret-internal-error")));
    }
}
