package io.kestra.plugin.clevercloud.addons;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkToApplicationTest extends AbstractClevercloudTest {

    @Test
    void linksAddonToApplicationWithAddonIdAsJsonStringBody(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons"))
            .willReturn(ok()));

        var task = TestableLinkToApplication.builder()
            .id("link-addon-test")
            .type(LinkToApplication.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons"))
            .withRequestBody(equalTo("\"addon_xyz789\"")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/self/applications/app_personal/addons"))
            .willReturn(ok()));

        var task = TestableLinkToApplication.builder()
            .id("link-addon-self-path-test")
            .type(LinkToApplication.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_personal"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/self/applications/app_personal/addons")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons"))
            .willReturn(ok()));

        var task = TestableLinkToApplication.builder()
            .id("link-addon-auth-test")
            .type(LinkToApplication.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(postRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenApplicationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableLinkToApplication.builder()
            .id("link-addon-missing-app-test")
            .type(LinkToApplication.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("applicationId is required"));
    }

    @Test
    void throwsClearExceptionWhenAddonIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableLinkToApplication.builder()
            .id("link-addon-missing-addon-test")
            .type(LinkToApplication.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("addonId is required"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableLinkToApplication extends LinkToApplication {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
