package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class RemoveMemberTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void sendsDeleteRequestWithUserIdInUrl(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // CC API returns 200 with empty body on successful removal.
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .willReturn(ok()));

        var task = TestableRemoveMember.builder()
            .id("remove-member-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/members/user_abc-001")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .willReturn(ok()));

        var task = TestableRemoveMember.builder()
            .id("remove-member-auth-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(deleteRequestedFor(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .withHeader("Authorization", equalTo("Bearer my-secret-token")));
    }

    @Test
    void handlesNoContentResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // Some API versions return 204 No Content.
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_def-002"))
            .willReturn(aResponse().withStatus(204)));

        var task = TestableRemoveMember.builder()
            .id("remove-204-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_def-002"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        // Should not throw on 204.
        task.run(runContext);
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/organisations/orga_test/members/user_abc-001"))
            .willReturn(ok()));

        var task = TestableRemoveMember.builder()
            .id("remove-member-trailing-slash-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(deleteRequestedFor(urlEqualTo("/organisations/orga_test/members/user_abc-001")));
    }
}
