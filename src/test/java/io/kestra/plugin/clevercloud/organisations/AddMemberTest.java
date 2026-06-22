package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
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
        // CC API returns 200 with the new member object when the member is added.
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

        var task = AddMember.builder()
            .id("add-member-test")
            .type(AddMember.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .email(Property.of("newdev@example.com"))
            .role(Property.of("DEVELOPER"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
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

        var task = AddMember.builder()
            .id("add-member-ct-test")
            .type(AddMember.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .email(Property.of("user@example.com"))
            .role(Property.of("READ_ONLY"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Content-Type"), containsString("application/json"));
    }
}
