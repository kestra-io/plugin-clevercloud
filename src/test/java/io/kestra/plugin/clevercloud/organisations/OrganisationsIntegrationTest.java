package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Live integration test against the real Clever Cloud API.
 * Skipped unless CLEVER_TOKEN is set in the environment.
 *
 * Covers only read-only tasks. AddMember and RemoveMember mutate the real organisation
 * and are covered exclusively by mock-server unit tests.
 */
@KestraTest
@EnabledIfEnvironmentVariable(named = "CLEVER_TOKEN", matches = ".+")
class OrganisationsIntegrationTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void listMembers_succeeds_against_real_api() throws Exception {
        var consumerKey = System.getenv("CLEVER_CONSUMER_KEY");
        var consumerSecret = System.getenv("CLEVER_CONSUMER_SECRET");
        var token = System.getenv("CLEVER_TOKEN");
        var tokenSecret = System.getenv("CLEVER_SECRET");
        var orgId = System.getenv("CLEVER_ORG_ID");

        var task = ListMembers.builder()
            .id("integration-list-members")
            .type(ListMembers.class.getName())
            .consumerKey(Property.of(consumerKey))
            .consumerSecret(Property.of(consumerSecret))
            .token(Property.of(token))
            .tokenSecret(Property.of(tokenSecret))
            .organisationId(Property.of(orgId))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat("output must not be null", output, is(notNullValue()));
        assertThat("members list must not be null", output.getMembers(), is(notNullValue()));
        assertThat("total must match list size", output.getTotal(), is(output.getMembers().size()));

        runContext.logger().info("Real API returned {} member(s) for org {}", output.getTotal(), orgId);

        if (!output.getMembers().isEmpty()) {
            var first = output.getMembers().getFirst();
            runContext.logger().info("First member: id={} email={} role={} job={}",
                first.getMember() != null ? first.getMember().getId() : null,
                first.getMember() != null ? first.getMember().getEmail() : null,
                first.getRole(),
                first.getJob());

            assertThat("first member info must not be null", first.getMember(), is(notNullValue()));
            assertThat("first member id must not be null", first.getMember().getId(), is(notNullValue()));
        }
    }

    @Test
    void listApplications_succeeds_against_real_api() throws Exception {
        var consumerKey = System.getenv("CLEVER_CONSUMER_KEY");
        var consumerSecret = System.getenv("CLEVER_CONSUMER_SECRET");
        var token = System.getenv("CLEVER_TOKEN");
        var tokenSecret = System.getenv("CLEVER_SECRET");
        var orgId = System.getenv("CLEVER_ORG_ID");

        var task = ListApplications.builder()
            .id("integration-list-apps")
            .type(ListApplications.class.getName())
            .consumerKey(Property.of(consumerKey))
            .consumerSecret(Property.of(consumerSecret))
            .token(Property.of(token))
            .tokenSecret(Property.of(tokenSecret))
            .organisationId(Property.of(orgId))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat("output must not be null", output, is(notNullValue()));
        assertThat("applications list must not be null", output.getApplications(), is(notNullValue()));
        assertThat("total must match list size", output.getTotal(), is(output.getApplications().size()));

        runContext.logger().info("Real API returned {} application(s) for org {}", output.getTotal(), orgId);

        if (!output.getApplications().isEmpty()) {
            var first = output.getApplications().getFirst();
            runContext.logger().info("First app: id={} name={} zone={} type={}",
                first.getId(), first.getName(), first.getZone(),
                first.getInstance() != null ? first.getInstance().getType() : null);

            assertThat("first app id must not be null", first.getId(), is(notNullValue()));
            assertThat("first app name must not be null", first.getName(), is(notNullValue()));
        }
    }
}
