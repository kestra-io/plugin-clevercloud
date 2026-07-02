package io.kestra.plugin.clevercloud.organisations;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractClevercloudTest {

    @Test
    void fetchOrganisationDetails(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_abc123", """
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
            """);

        var task = TestableGet.builder()
            .id("get-org-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

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
        stubGetJson("/organisations/orga_abc123", """
            {"id":"orga_abc123","name":"Acme Corp","cleverEnterprise":false}
            """);

        var task = TestableGet.builder()
            .id("get-auth-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_abc123"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(getRequestedFor(urlPathEqualTo("/organisations/orga_abc123")), "my-secret-token");
    }

    @Test
    void usesOrganisationPathWhenOrgIdProvided(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/organisations/orga_xyz789", """
            {"id":"orga_xyz789","name":"Test Org","cleverEnterprise":true}
            """);

        var task = TestableGet.builder()
            .id("get-org-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_xyz789"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/organisations/orga_xyz789")));
    }

    @Test
    void usesSelfPathWhenOrgIdOmitted(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/self", """
            {"id":"user_personal123","name":"Personal Account","cleverEnterprise":false}
            """);

        var task = TestableGet.builder()
            .id("get-self-path-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verify(getRequestedFor(urlPathEqualTo("/self")));
        verifyNeverCalled("/organisations/.*");
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

        task.run(runContext());

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

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("403"));
        assertThat(ex.getMessage(), not(containsString("is not allowed")));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableGet extends Get {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
