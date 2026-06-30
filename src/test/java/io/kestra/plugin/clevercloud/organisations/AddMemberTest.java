package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.clevercloud.organisations.model.OrgRole;
import jakarta.inject.Inject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class AddMemberTest {

    @Inject
    RunContextFactory runContextFactory;

    MockWebServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void sendsPostWithEmailAndRole() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "member": {
                    "id": "user_newmember-0001",
                    "email": "newdev@example.com"
                  },
                  "role": "DEVELOPER"
                }
                """));

        var task = TestableAddMember.builder()
            .id("add-member-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("newdev@example.com"))
            .role(Property.ofValue(OrgRole.DEVELOPER))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getMethod(), is("POST"));
        assertThat(request.getPath(), containsString("/organisations/orga_test/members"));

        var requestBody = request.getBody().readUtf8();
        assertThat(requestBody, containsString("\"email\""));
        assertThat(requestBody, containsString("newdev@example.com"));
        assertThat(requestBody, containsString("\"role\""));
        assertThat(requestBody, containsString("DEVELOPER"));
    }

    @Test
    void requestBodyContainsJsonContentType() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        var task = TestableAddMember.builder()
            .id("add-member-ct-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("user@example.com"))
            .role(Property.ofValue(OrgRole.READ_ONLY))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Content-Type"), containsString("application/json"));
    }

    @Test
    void serialisesRoleNameCorrectly() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        var task = TestableAddMember.builder()
            .id("add-member-role-test")
            .type(AddMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .email(Property.ofValue("ro@example.com"))
            .role(Property.ofValue(OrgRole.READ_ONLY))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getBody().readUtf8(), containsString("READ_ONLY"));
    }
}
