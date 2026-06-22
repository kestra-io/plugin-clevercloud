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
class ListMembersTest {

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
    void parseMemberListResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {
                    "member": {
                      "id": "user_251a73ad-e581-4da2-bcff-7fc798fad2f7",
                      "email": "admin@example.com",
                      "name": "Alice Admin",
                      "avatar": "https://example.com/avatar.png",
                      "preferredMFA": "NONE"
                    },
                    "role": "ADMIN",
                    "job": "owner"
                  },
                  {
                    "member": {
                      "id": "user_b2c3d4e5-f6a7-4b8c-9d0e-f1a2b3c4d5e6",
                      "email": "dev@example.com",
                      "name": "Bob Developer",
                      "avatar": null,
                      "preferredMFA": "TOTP"
                    },
                    "role": "DEVELOPER",
                    "job": null
                  }
                ]
                """));

        var task = ListMembers.builder()
            .id("list-members-test")
            .type(ListMembers.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_test"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getMembers(), hasSize(2));

        var first = output.getMembers().getFirst();
        assertThat(first.getMember().getId(), is("user_251a73ad-e581-4da2-bcff-7fc798fad2f7"));
        assertThat(first.getMember().getEmail(), is("admin@example.com"));
        assertThat(first.getMember().getName(), is("Alice Admin"));
        assertThat(first.getRole(), is("ADMIN"));
        assertThat(first.getJob(), is("owner"));

        var second = output.getMembers().get(1);
        assertThat(second.getMember().getId(), is("user_b2c3d4e5-f6a7-4b8c-9d0e-f1a2b3c4d5e6"));
        assertThat(second.getRole(), is("DEVELOPER"));
        assertThat(second.getJob(), is(nullValue()));
    }

    @Test
    void handleEmptyMemberList() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = ListMembers.builder()
            .id("list-members-empty-test")
            .type(ListMembers.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_empty"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getMembers(), is(empty()));
    }

    @Test
    void requestUrlContainsOrgIdAndMembersPath() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = ListMembers.builder()
            .id("list-members-url-test")
            .type(ListMembers.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_abc123"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_abc123/members"));
    }
}
