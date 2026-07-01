package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.clevercloud.organisations.model.OrgRole;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class AddMemberTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void sendsPostWithEmailAndRole(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("""
                {
                  "member": {
                    "id": "user_newmember-0001",
                    "email": "newdev@example.com"
                  },
                  "role": "DEVELOPER"
                }
                """)));

        var task = TestableAddMember.builder()
            .id("add-member-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("newdev@example.com"))
            .role(Property.ofValue(OrgRole.DEVELOPER))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/members"))
            .withRequestBody(containing("\"email\""))
            .withRequestBody(containing("newdev@example.com"))
            .withRequestBody(containing("\"role\""))
            .withRequestBody(containing("DEVELOPER")));
    }

    @Test
    void requestBodyContainsJsonContentType(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("{}")));

        var task = TestableAddMember.builder()
            .id("add-member-ct-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("user@example.com"))
            .role(Property.ofValue(OrgRole.READ_ONLY))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/members"))
            .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void serialisesRoleNameCorrectly(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("{}")));

        var task = TestableAddMember.builder()
            .id("add-member-role-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("ro@example.com"))
            .role(Property.ofValue(OrgRole.READ_ONLY))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/members"))
            .withRequestBody(containing("READ_ONLY")));
    }
}
