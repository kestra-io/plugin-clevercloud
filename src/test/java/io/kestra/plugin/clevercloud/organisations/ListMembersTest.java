package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import io.kestra.plugin.clevercloud.organisations.model.Member;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListMembersTest extends AbstractClevercloudTest {

    @Test
    void parseMemberListResponse(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/members", """
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
            """);

        var task = TestableListMembers.builder()
            .id("list-members-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

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
        stubGetJson("/organisations/orga_empty/members", "[]");

        var task = TestableListMembers.builder()
            .id("list-members-empty-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_empty"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(0));
        assertThat(output.getMembers(), is(empty()));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_abc123/members", "[]");

        var task = TestableListMembers.builder()
            .id("list-members-auth-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123/members")), "my-secret-token");
    }

    @Test
    void requestUrlContainsOrgIdAndMembersPath(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_abc123/members", "[]");

        var task = TestableListMembers.builder()
            .id("list-members-url-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

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

        task.run(runContext());

        verify(getRequestedFor(urlEqualTo("/organisations/orga_test/members")));
    }

    @Test
    void fetchTypeFetchReturnsFullListInOutput(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/members", """
            [
              {"member": {"id": "user_fetch-1"}, "role": "ADMIN"},
              {"member": {"id": "user_fetch-2"}, "role": "DEVELOPER"}
            ]
            """);

        var task = TestableListMembers.builder()
            .id("list-members-fetch-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2));
        assertThat(output.getMembers(), hasSize(2));
        assertThat(output.getMember(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void fetchTypeStoreWritesIonFileToInternalStorage(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_test/members", """
            [
              {"member": {"id": "user_store-1"}, "role": "ADMIN"},
              {"member": {"id": "user_store-2"}, "role": "DEVELOPER"}
            ]
            """);

        var task = TestableListMembers.builder()
            .id("list-members-store-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
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

    @Test
    void throwsClearExceptionWhenOrganisationIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableListMembers.builder()
            .id("list-members-missing-org-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("organisationId is required for ListMembers"));
    }

    @Test
    void throwsCleanExceptionOn500WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_test/members"))
            .willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":9999,\"message\":\"Internal database connection leaked at host db-primary-42\",\"type\":\"error\"}")));

        var task = TestableListMembers.builder()
            .id("list-members-500-test")
            .type(ListMembers.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("500"));
        assertThat(ex.getMessage(), not(containsString("db-primary-42")));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableListMembers extends ListMembers {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
