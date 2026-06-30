package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.organisations.model.Member;
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

        var task = TestableListMembers.builder()
            .id("list-members-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(mockServer.url("").toString())
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

        var task = TestableListMembers.builder()
            .id("list-members-empty-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getMembers(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableListMembers.builder()
            .id("list-members-auth-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization"), is("Bearer my-secret-token"));
    }

    @Test
    void requestUrlContainsOrgIdAndMembersPath() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("[]"));

        var task = TestableListMembers.builder()
            .id("list-members-url-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_abc123/members"));
    }

    @Test
    void fetchTypeFetchReturnsFullListInOutput() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {"member": {"id": "user_fetch-1"}, "role": "ADMIN"},
                  {"member": {"id": "user_fetch-2"}, "role": "DEVELOPER"}
                ]
                """));

        var task = TestableListMembers.builder()
            .id("list-members-fetch-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getMembers(), hasSize(2));
        assertThat(output.getMember(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                [
                  {"member": {"id": "user_store-1"}, "role": "ADMIN"},
                  {"member": {"id": "user_store-2"}, "role": "DEVELOPER"}
                ]
                """));

        var task = TestableListMembers.builder()
            .id("list-members-store-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(mockServer.url("").toString())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getMembers(), is(nullValue()));
        assertThat(output.getMember(), is(nullValue()));

        try (var reader = new java.io.InputStreamReader(runContext.storage().getFile(output.getUri()))) {
            var stored = FileSerde.readAll(reader, Member.class).collectList().block();
            assertThat(stored, hasSize(2));
            assertThat(stored.get(0).getMember().getId(), is("user_store-1"));
            assertThat(stored.get(1).getMember().getId(), is("user_store-2"));
        }
    }
}
