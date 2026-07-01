package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class GetTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void fetchOrganisationDetails(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_abc123"))
            .willReturn(okJson("""
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
                """)));

        var task = TestableGet.builder()
            .id("get-org-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
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
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_abc123"))
            .willReturn(okJson("""
                {"id":"orga_abc123","name":"Acme Corp","cleverEnterprise":false}
                """)));

        var task = TestableGet.builder()
            .id("get-auth-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123"))
            .withHeader("Authorization", equalTo("Bearer my-secret-token")));
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/organisations/orga_xyz789"))
            .willReturn(okJson("""
                {"id":"orga_xyz789","name":"Test Org","cleverEnterprise":true}
                """)));

        var task = TestableGet.builder()
            .id("get-org-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_xyz789")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/self"))
            .willReturn(okJson("""
                {"id":"user_personal123","name":"Personal Account","cleverEnterprise":false}
                """)));

        var task = TestableGet.builder()
            .id("get-self-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlPathEqualTo("/self")));
        verify(0, getRequestedFor(urlPathMatching("/organisations/.*")));
    }

    @Test
    void requestPathHasNoDoubleSlashWhenBaseUrlHasTrailingSlash(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlEqualTo("/organisations/orga_abc123"))
            .willReturn(okJson("""
                {"id":"orga_abc123","name":"Acme Corp","cleverEnterprise":false}
                """)));

        var task = TestableGet.builder()
            .id("get-trailing-slash-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/")
            .build();

        var runContext = runContextFactory.of();
        task.run(runContext);

        verify(getRequestedFor(urlEqualTo("/organisations/orga_abc123")));
    }

    @Test
    void throwsCleanExceptionOn403WithoutBodyLeak(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/organisations/orga_xyz"))
            .willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":6017,\"message\":\"This organisation is not allowed to perform this operation.\",\"type\":\"error\"}")));

        var task = TestableGet.builder()
            .id("get-403-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_xyz"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContextFactory.of();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("403"));
        assertThat(ex.getMessage(), not(containsString("is not allowed")));
    }
}
