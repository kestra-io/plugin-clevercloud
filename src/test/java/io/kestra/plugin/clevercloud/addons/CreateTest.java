package io.kestra.plugin.clevercloud.addons;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.clevercloud.AbstractClevercloudTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractClevercloudTest {

    @Test
    void provisionsAddonWithAllFields(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("""
                {
                  "id": "addon_new-0001",
                  "name": "my-postgres",
                  "region": "par",
                  "provider": {"id": "postgresql-addon"},
                  "plan": {"id": "plan_dev"},
                  "creationDate": 1782127329927
                }
                """)));

        var task = TestableCreate.builder()
            .id("create-addon-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .plan(Property.ofValue("dev"))
            .region(Property.ofValue("par"))
            .name(Property.ofValue("my-postgres"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("addon_new-0001"));
        assertThat(output.getName(), is("my-postgres"));
        assertThat(output.getRegion(), is("par"));
        assertThat(output.getProviderId(), is("postgresql-addon"));
        assertThat(output.getPlanId(), is("plan_dev"));

        verify(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons"))
            .withRequestBody(containing("\"providerId\":\"postgresql-addon\""))
            .withRequestBody(containing("\"plan\":\"dev\""))
            .withRequestBody(containing("\"region\":\"par\""))
            .withRequestBody(containing("\"name\":\"my-postgres\"")));
    }

    @Test
    void provisionsAddonWithOnlyRequiredFields(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/self/addons"))
            .willReturn(okJson("{\"id\": \"addon_new-0002\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-minimal-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .providerId(Property.ofValue("redis-addon"))
            .plan(Property.ofValue("s"))
            .region(Property.ofValue("rbx"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("addon_new-0002"));
        verify(postRequestedFor(urlPathEqualTo("/self/addons"))
            .withRequestBody(containing("\"providerId\":\"redis-addon\""))
            .withRequestBody(containing("\"plan\":\"s\""))
            .withRequestBody(containing("\"region\":\"rbx\"")));
    }

    @Test
    void sendsBearerAuthorizationHeader(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/organisations/orga_test/addons"))
            .willReturn(okJson("{\"id\": \"addon_new\"}")));

        var task = TestableCreate.builder()
            .id("create-addon-auth-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("my-secret-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .plan(Property.ofValue("dev"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        task.run(runContext());

        verifyBearerAuth(postRequestedFor(urlPathEqualTo("/organisations/orga_test/addons")), "my-secret-token");
    }

    @Test
    void throwsClearExceptionWhenProviderIdMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableCreate.builder()
            .id("create-addon-missing-provider-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .plan(Property.ofValue("dev"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("providerId is required"));
    }

    @Test
    void throwsClearExceptionWhenPlanMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableCreate.builder()
            .id("create-addon-missing-plan-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .region(Property.ofValue("par"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("plan is required"));
    }

    @Test
    void throwsClearExceptionWhenRegionMissing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = TestableCreate.builder()
            .id("create-addon-missing-region-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-api-token"))
            .organisationId(Property.ofValue("orga_test"))
            .providerId(Property.ofValue("postgresql-addon"))
            .plan(Property.ofValue("dev"))
            .testBaseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("region is required"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TestableCreate extends Create {

        private String testBaseUrl;

        @Override
        protected String baseUrl() {
            return testBaseUrl;
        }
    }
}
