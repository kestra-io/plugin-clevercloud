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
    title = "Redeploy a Clever Cloud application",
    description = """
        Triggers a new deployment of the application. Use commit to deploy a specific Git commit,
        otherwise the last pushed commit is redeployed.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        Track the resulting deployment with deployments.List, deployments.Get, or deployments.WaitForState.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Redeploy the last pushed commit",
            full = true,
            code = """
                id: redeploy_application
                namespace: company.team

                tasks:
                  - id: redeploy
                    type: io.kestra.plugin.clevercloud.applications.Redeploy
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        ),
        @Example(
            title = "Redeploy a specific commit without using the build cache",
            full = true,
            code = """
                id: redeploy_specific_commit
                namespace: company.team

                tasks:
                  - id: redeploy
                    type: io.kestra.plugin.clevercloud.applications.Redeploy
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    commit: "a1b2c3d4e5f6"
                    useCache: false
                """
        )
    }
)
public class Redeploy extends AbstractCleverCloudConnection implements RunnableTask<VoidOutput> {

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
        title = "Git commit SHA to deploy",
        description = "When omitted, the last pushed commit is redeployed."
    )
    @PluginProperty(group = "main")
    private Property<String> commit;

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
        var separator = "?";

        var rCommit = runContext.render(commit).as(String.class).orElse(null);
        if (rCommit != null) {
            urlBuilder.append(separator).append("commit=").append(encodeSegment(rCommit));
            separator = "&";
        }

        var rUseCache = runContext.render(useCache).as(Boolean.class).orElse(null);
        if (rUseCache != null) {
            urlBuilder.append(separator).append("useCache=").append(rUseCache);
        }

        logger.info("Redeploying application {}{}", rAppId, rCommit != null ? " at commit " + rCommit : "");
        makeCall(runContext, HttpRequest.builder().uri(URI.create(urlBuilder.toString())).method("POST"));

        return null;
    }
}
