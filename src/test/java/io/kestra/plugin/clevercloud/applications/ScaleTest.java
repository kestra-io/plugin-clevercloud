package io.kestra.plugin.clevercloud.applications;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScaleTest extends AbstractClevercloudTest {

    @Test
    void sendsOnlyProvidedFieldsInRequestBody(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(okJson("""
                {
                  "id": "app_abc123",
                  "instance": {
                    "minInstances": 2,
                    "maxInstances": 4,
                    "minFlavor": {"name": "S"},
                    "maxFlavor": {"name": "M"}
                  }
                }
                """)));

        var task = TestableScale.builder()
            .id("scale-app-test")
            .type(Scale.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .minInstances(Property.ofValue(2))
            .maxInstances(Property.ofValue(4))
            .minFlavor(Property.ofValue("S"))
            .maxFlavor(Property.ofValue("M"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getMinInstances(), is(2));
        assertThat(output.getMaxInstances(), is(4));
        assertThat(output.getMinFlavor(), is("S"));
        assertThat(output.getMaxFlavor(), is("M"));

        verify(putRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .withRequestBody(equalToJson("""
                {"minInstances": 2, "maxInstances": 4, "minFlavor": "S", "maxFlavor": "M"}
                """, true, true)));
    }

    @Test
    void sendsOnlyMaxInstancesWhenOnlyThatIsSet(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(okJson("{\"id\": \"app_abc123\", \"instance\": {\"maxInstances\": 5}}")));

        var task = TestableScale.builder()
            .id("scale-app-partial-test")
            .type(Scale.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .maxInstances(Property.ofValue(5))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(putRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .withRequestBody(equalToJson("{\"maxInstances\": 5}", true, true)));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/organisations/orga_test/applications/app_abc123"))
            .willReturn(okJson("{}")));

        var task = TestableScale.builder()
            .id("scale-app-auth-test")
            .type(Scale.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .minInstances(Property.ofValue(1))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(putRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenNoScalingFieldSet(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableScale.builder()
            .id("scale-app-empty-test")
            .type(Scale.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("At least one of minInstances, maxInstances, minFlavor, maxFlavor"));
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableScale.builder()
            .id("scale-app-missing-id-test")
            .type(Scale.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .minInstances(Property.ofValue(1))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("applicationId is required"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableScale extends Scale {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
