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
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class GetTest {

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
    void fetchOrganisationDetails() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "id": "orga_abc123",
                  "name": "Acme Corp",
                  "description": "A test organisation",
                  "city": "Paris",
                  "country": "FR",
                  "avatar": "https://example.com/avatar.png",
                  "email": "admin@acme.com",
                  "cleverEnterprise": false,
                  "billingEmail": "billing@acme.com"
                }
                """));

        var task = Get.builder()
            .id("get-org-test")
            .type(Get.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_abc123"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat(output.getId(), is("orga_abc123"));
        assertThat(output.getName(), is("Acme Corp"));
        assertThat(output.getDescription(), is("A test organisation"));
        assertThat(output.getCity(), is("Paris"));
        assertThat(output.getCountry(), is("FR"));
        assertThat(output.getEmail(), is("admin@acme.com"));
        assertThat(output.isCleverEnterprise(), is(false));
    }

    @Test
    void requestUrlContainsOrgId() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"id":"orga_xyz789","name":"Test Org","cleverEnterprise":true}
                """));

        var task = Get.builder()
            .id("get-url-test")
            .type(Get.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("orga_xyz789"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        var request = mockServer.takeRequest();
        assertThat(request.getPath(), containsString("/organisations/orga_xyz789"));
    }

    @Test
    void throwsOnNonSuccessResponse() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(403)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"id":6017,"message":"This organisation is not allowed to perform this operation.","type":"error"}
                """));

        var task = Get.builder()
            .id("get-403-test")
            .type(Get.class.getName())
            .consumerKey(Property.of("ck"))
            .consumerSecret(Property.of("cs"))
            .token(Property.of("tk"))
            .tokenSecret(Property.of("ts"))
            .organisationId(Property.of("user_personal123"))
            .apiBaseUrl(Property.of(mockServer.url("/v2/").toString()))
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("403"));
    }
}
