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

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnlinkFromApplicationTest extends AbstractClevercloudTest {

    @Test
    void unlinksAddonFromApplication(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons/addon_xyz789"))
            .willReturn(ok()));

        var task = TestableUnlinkFromApplication.builder()
            .id("unlink-addon-test")
            .type(UnlinkFromApplication.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons/addon_xyz789")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/self/applications/app_personal/addons/addon_xyz789"))
            .willReturn(ok()));

        var task = TestableUnlinkFromApplication.builder()
            .id("unlink-addon-self-path-test")
            .type(UnlinkFromApplication.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .applicationId(Property.ofValue("app_personal"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(deleteRequestedFor(urlPathEqualTo("/self/applications/app_personal/addons/addon_xyz789")));
        verifyNeverCalled("/organisations/.*");
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons/addon_xyz789"))
            .willReturn(ok()));

        var task = TestableUnlinkFromApplication.builder()
            .id("unlink-addon-auth-test")
            .type(UnlinkFromApplication.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .applicationId(Property.ofValue("app_abc123"))
            .addonId(Property.ofValue("addon_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/applications/app_abc123/addons/addon_xyz789")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenAddonIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableUnlinkFromApplication.builder()
            .id("unlink-addon-missing-addon-test")
            .type(UnlinkFromApplication.class.getName())
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
    public static class TestableUnlinkFromApplication extends UnlinkFromApplication {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
