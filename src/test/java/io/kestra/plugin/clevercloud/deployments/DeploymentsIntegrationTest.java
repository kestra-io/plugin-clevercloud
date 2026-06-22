package io.kestra.plugin.clevercloud.deployments;

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
 * All six env vars must be present for the test to be meaningful.
 */
@KestraTest
@EnabledIfEnvironmentVariable(named = "CLEVER_TOKEN", matches = ".+")
class DeploymentsIntegrationTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void listDeployments_succeeds_against_real_api() throws Exception {
        var consumerKey = System.getenv("CLEVER_CONSUMER_KEY");
        var consumerSecret = System.getenv("CLEVER_CONSUMER_SECRET");
        var token = System.getenv("CLEVER_TOKEN");
        var tokenSecret = System.getenv("CLEVER_SECRET");
        var orgId = System.getenv("CLEVER_ORG_ID");
        var appId = System.getenv("CLEVER_APP_ID");

        var task = List.builder()
            .id("integration-list")
            .type(List.class.getName())
            .consumerKey(Property.of(consumerKey))
            .consumerSecret(Property.of(consumerSecret))
            .token(Property.of(token))
            .tokenSecret(Property.of(tokenSecret))
            .organisationId(Property.of(orgId))
            .applicationId(Property.of(appId))
            .build();

        var runContext = runContextFactory.of();
        var output = task.run(runContext);

        assertThat("output must not be null", output, is(notNullValue()));
        assertThat("deployments list must not be null", output.getDeployments(), is(notNullValue()));
        assertThat("total must match list size", output.getTotal(), is(output.getDeployments().size()));

        System.out.println("[integration] Real API returned " + output.getTotal() + " deployment(s) for app " + appId);
        runContext.logger().info("Real API returned {} deployment(s) for app {}", output.getTotal(), appId);

        if (!output.getDeployments().isEmpty()) {
            var first = output.getDeployments().getFirst();
            runContext.logger().info("First deployment: id={} state={} commit={}", first.getId(), first.getState(), first.getCommit());

            var getTask = Get.builder()
                .id("integration-get")
                .type(Get.class.getName())
                .consumerKey(Property.of(consumerKey))
                .consumerSecret(Property.of(consumerSecret))
                .token(Property.of(token))
                .tokenSecret(Property.of(tokenSecret))
                .organisationId(Property.of(orgId))
                .applicationId(Property.of(appId))
                .deploymentId(Property.of(first.getId()))
                .build();

            var getOutput = getTask.run(runContextFactory.of());

            assertThat("Get output must not be null", getOutput, is(notNullValue()));
            assertThat("Get deploymentId must match", getOutput.getDeploymentId(), is(first.getId()));
            assertThat("Get state must not be null", getOutput.getState(), is(notNullValue()));

            runContext.logger().info("Get response: id={} state={} startDate={}", getOutput.getDeploymentId(), getOutput.getState(), getOutput.getStartDate());
        }
    }
}
