package io.kestra.plugin.clevercloud.applications;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.applications.model.EnvironmentVariable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get environment variables of a Clever Cloud application",
    description = """
        Retrieves all environment variables configured on the application.
        Values are returned as-is: if any variable holds a credential, avoid logging or storing
        this task's output in plain text.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch environment variables of an application",
            full = true,
            code = """
                id: get_application_env
                namespace: company.team

                tasks:
                  - id: get_env
                    type: io.kestra.plugin.clevercloud.applications.GetEnv
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class GetEnv extends AbstractCleverCloudConnection implements RunnableTask<GetEnv.Output> {

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

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var url = resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId) + "/env");

        logger.info("Fetching environment variables for application {}", rAppId);
        var body = makeCall(runContext, buildGetRequest(url));
        var vars = MAPPER.readValue(body, new TypeReference<ArrayList<EnvironmentVariable>>() {});

        var variables = new LinkedHashMap<String, String>();
        vars.forEach(v -> variables.put(v.getName(), v.getValue()));

        logger.info("Found {} environment variable(s)", variables.size());

        return Output.builder()
            .variables(variables)
            .total(variables.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Environment variables as a map of name to value")
        private final Map<String, String> variables;

        @Schema(title = "Total number of environment variables returned")
        private final int total;
    }
}
