package io.kestra.plugin.clevercloud.applications;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Restart a Clever Cloud application",
    description = """
        Restarts the application's instances on their currently deployed commit, without
        deploying different code. This calls the same endpoint as Redeploy but never specifies a
        commit; use Redeploy to deploy a different commit.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Restart an application",
            full = true,
            code = """
                id: restart_application
                namespace: company.team

                tasks:
                  - id: restart
                    type: io.kestra.plugin.clevercloud.applications.Restart
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class Restart extends AbstractCleverCloudConnection implements RunnableTask<VoidOutput> {

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
        title = "Whether to reuse the build cache",
        description = "Defaults to true. Set to false to force a clean build."
    )
    @PluginProperty(group = "execution")
    private Property<Boolean> useCache;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var urlBuilder = new StringBuilder(resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId) + "/instances"));

        var rUseCache = runContext.render(useCache).as(Boolean.class).orElse(null);
        if (rUseCache != null) {
            urlBuilder.append("?useCache=").append(rUseCache);
        }

        logger.info("Restarting application {}", rAppId);
        makeCall(runContext, HttpRequest.builder().uri(URI.create(urlBuilder.toString())).method("POST"));

        return null;
    }
}
