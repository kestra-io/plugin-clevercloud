package io.kestra.plugin.clevercloud.applications;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Set environment variables of a Clever Cloud application",
    description = """
        Creates or updates the given environment variables on the application, one API call per
        variable. Existing variables not listed in vars keep their current value.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Set environment variables on an application",
            full = true,
            code = """
                id: set_application_env
                namespace: company.team

                tasks:
                  - id: set_env
                    type: io.kestra.plugin.clevercloud.applications.SetEnv
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    vars:
                      NODE_ENV: production
                      API_KEY: "{{ secret('THIRD_PARTY_API_KEY') }}"
                """
        )
    }
)
public class SetEnv extends AbstractCleverCloudConnection implements RunnableTask<SetEnv.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Schema(
        title = "Environment variables to create or update",
        description = "Map of variable name to value. At least one entry is required."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Map<String, String>> vars;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );
        var rVars = runContext.render(vars).asMap(String.class, String.class);

        if (rVars.isEmpty()) {
            throw new IllegalArgumentException("vars must contain at least one environment variable to set");
        }

        var envUrl = resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId) + "/env");

        logger.info("Setting {} environment variable(s) on application {}", rVars.size(), rAppId);
        for (var entry : rVars.entrySet()) {
            var url = envUrl + "/" + encodeSegment(entry.getKey());
            makeCall(runContext, buildPutRequest(url, Map.of("value", entry.getValue())));
        }

        return Output.builder().updatedCount(rVars.size()).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Number of environment variables created or updated")
        private final int updatedCount;
    }
}
