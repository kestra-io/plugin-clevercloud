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
class RemoveMemberTest {

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
    void sendsDeleteRequestWithUserIdInUrl() throws Exception {
        // CC API returns 200 with empty body on successful removal.
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        var task = RemoveMember.builder()
            .id("remove-member-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getMethod(), is("DELETE"));
        assertThat(request.getPath(), containsString("/organisations/orga_test/members/user_abc-001"));
    }

    @Test
    void sendsBearerAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        var task = RemoveMember.builder()
            .id("remove-member-auth-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_abc-001"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization"), is("Bearer my-secret-token"));
    }

    @Test
    void handlesNoContentResponse() throws Exception {
        // Some API versions return 204 No Content.
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        var task = RemoveMember.builder()
            .id("remove-204-test")
            .type(RemoveMember.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .userId(Property.ofValue("user_def-002"))
            .apiBaseUrl(Property.ofValue(mockServer.url("").toString()))
            .build();

        var runContext = runContextFactory.of();
        // Should not throw on 204.
        task.run(runContext);
    }
}
