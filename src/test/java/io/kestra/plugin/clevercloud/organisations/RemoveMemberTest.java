package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoveMemberTest extends AbstractClevercloudTest {

    @Test
    void sendsDeleteRequestWithUserIdInUrl(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // CC API returns 200 with empty body on successful removal.
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .willReturn(ok()));

        var task = TestTasks.TestableRemoveMember.builder()
            .id("remove-member-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/members/user_abc-001")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .willReturn(ok()));

        var task = TestTasks.TestableRemoveMember.builder()
            .id("remove-member-auth-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/members/user_abc-001")), "my-secret-token");
    }

    @Test
    void handlesNoContentResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // Some API versions return 204 No Content.
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_def-002"))
            .willReturn(aResponse().withStatus(204)));

        var task = TestTasks.TestableRemoveMember.builder()
            .id("remove-204-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_def-002"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        // Should not throw on 204.
        task.run(runContext());
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .willReturn(ok()));

        var task = TestTasks.TestableRemoveMember.builder()
            .id("remove-member-trailing-slash-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        task.run(runContext());

        verify(deleteRequestedFor(urlEqualTo("/organisations/orga_test/members/user_abc-001")));
    }

    @Test
    void throwsClearExceptionWhenOrganisationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestTasks.TestableRemoveMember.builder()
            .id("remove-member-missing-org-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("organisationId is required for RemoveMember"));
    }

    @Test
    void throwsClearExceptionWhenUserIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestTasks.TestableRemoveMember.builder()
            .id("remove-member-missing-user-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("userId is required for RemoveMember"));
    }
}
