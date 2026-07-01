package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.organisations.model.Member;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class ListMembersTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void parseMemberListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("""
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
                """)));

        var task = TestableListMembers.builder()
            .id("list-members-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
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
    void handleEmptyMemberList(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_empty/members"))
            .willReturn(okJson("[]")));

        var task = TestableListMembers.builder()
            .id("list-members-empty-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(0));
        assertThat(output.getMembers(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_abc123/members"))
            .willReturn(okJson("[]")));

        var task = TestableListMembers.builder()
            .id("list-members-auth-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123/members"))
            .withHeader("Authorization", equalTo("Bearer my-secret-token")));
    }

    @Test
    void requestUrlContainsOrgIdAndMembersPath(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_abc123/members"))
            .willReturn(okJson("[]")));

        var task = TestableListMembers.builder()
            .id("list-members-url-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123/members")));
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("[]")));

        var task = TestableListMembers.builder()
            .id("list-members-trailing-slash-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/members")));
    }

    @Test
    void fetchTypeFetchReturnsFullListInOutput(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("""
                [
                  {"member": {"id": "user_fetch-1"}, "role": "ADMIN"},
                  {"member": {"id": "user_fetch-2"}, "role": "DEVELOPER"}
                ]
                """)));

        var task = TestableListMembers.builder()
            .id("list-members-fetch-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getTotal(), is(2));
        assertThat(output.getMembers(), hasSize(2));
        assertThat(output.getMember(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(okJson("""
                [
                  {"member": {"id": "user_store-1"}, "role": "ADMIN"},
                  {"member": {"id": "user_store-2"}, "role": "DEVELOPER"}
                ]
                """)));

        var task = TestableListMembers.builder()
            .id("list-members-store-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
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
