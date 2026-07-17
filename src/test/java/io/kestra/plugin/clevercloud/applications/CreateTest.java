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

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractClevercloudTest {

    @Test
    void createsApplicationWithRequiredAndScalingFields(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("""
                {
                  "id": "app_new-0001",
                  "name": "my-node-app",
                  "zone": "par",
                  "deployUrl": "https://push.clever-cloud.com/my-node-app.git",
                  "instance": {"type": "node", "version": "20260617"}
                }
                """)));

        var task = TestableCreate.builder()
            .id("create-app-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .name(Property.ofValue("my-node-app"))
            .zone(Property.ofValue("par"))
            .instanceType(Property.ofValue("node"))
            .instanceVersion(Property.ofValue("20260617"))
            .minInstances(Property.ofValue(1))
            .maxInstances(Property.ofValue(2))
            .minFlavor(Property.ofValue("XS"))
            .maxFlavor(Property.ofValue("S"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("app_new-0001"));
        assertThat(output.getName(), is("my-node-app"));
        assertThat(output.getZone(), is("par"));
        assertThat(output.getInstanceType(), is("node"));

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications"))
            .withRequestBody(containing("\"name\":\"my-node-app\""))
            .withRequestBody(containing("\"zone\":\"par\""))
            .withRequestBody(containing("\"instanceType\":\"node\""))
            .withRequestBody(containing("\"minInstances\":1"))
            .withRequestBody(containing("\"maxFlavor\":\"S\""))
            .withRequestBody(containing("\"deploy\":\"git\"")));
    }

    @Test
    void createsApplicationWithOnlyRequiredFields(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/self/applications"))
            .willReturn(okJson("{\"id\": \"app_new-0002\", \"name\": \"minimal-app\"}")));

        var task = TestableCreate.builder()
            .id("create-app-minimal-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .name(Property.ofValue("minimal-app"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("app_new-0002"));
        verify(postRequestedFor(urlPathEqualTo("/self/applications"))
            .withRequestBody(containing("\"name\":\"minimal-app\""))
            .withRequestBody(containing("\"deploy\":\"git\"")));
    }

    @Test
    void createsApplicationWithFtpDeployMethod(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/self/applications"))
            .willReturn(okJson("{\"id\": \"app_new-0003\", \"name\": \"ftp-app\"}")));

        var task = TestableCreate.builder()
            .id("create-app-ftp-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .name(Property.ofValue("ftp-app"))
            .deploy(Property.ofValue("ftp"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("app_new-0003"));
        verify(postRequestedFor(urlPathEqualTo("/self/applications"))
            .withRequestBody(containing("\"name\":\"ftp-app\""))
            .withRequestBody(containing("\"deploy\":\"ftp\"")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications"))
            .willReturn(okJson("{\"id\": \"app_new\"}")));

        var task = TestableCreate.builder()
            .id("create-app-auth-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .name(Property.ofValue("my-app"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenNameMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableCreate.builder()
            .id("create-app-missing-name-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("name is required"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableCreate extends Create {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
