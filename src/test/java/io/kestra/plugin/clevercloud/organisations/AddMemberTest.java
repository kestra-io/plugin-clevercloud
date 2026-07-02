package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.organisations.model.OrgRole;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddMemberTest extends AbstractClevercloudTest {

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

        var task = TestTasks.TestableAddMember.builder()
            .id("add-member-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("newdev@example.com"))
            .role(Property.ofValue(OrgRole.DEVELOPER))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

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

        var task = TestTasks.TestableAddMember.builder()
            .id("add-member-ct-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("user@example.com"))
            .role(Property.ofValue(OrgRole.READ_ONLY))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/members"))
            .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void serialisesRoleNameCorrectly(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("{}")));

        var task = TestTasks.TestableAddMember.builder()
            .id("add-member-role-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("ro@example.com"))
            .role(Property.ofValue(OrgRole.READ_ONLY))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/members"))
            .withRequestBody(containing("READ_ONLY")));
    }

    @Test
    void throwsClearExceptionWhenOrganisationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestTasks.TestableAddMember.builder()
            .id("add-member-missing-org-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .email(Property.ofValue("dev@example.com"))
            .role(Property.ofValue(OrgRole.DEVELOPER))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("organisationId is required for AddMember"));
    }

    @Test
    void throwsClearExceptionWhenEmailMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestTasks.TestableAddMember.builder()
            .id("add-member-missing-email-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .role(Property.ofValue(OrgRole.DEVELOPER))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("email is required for AddMember"));
    }

    @Test
    void throwsClearExceptionWhenRoleMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestTasks.TestableAddMember.builder()
            .id("add-member-missing-role-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("dev@example.com"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("role is required for AddMember"));
    }
}
