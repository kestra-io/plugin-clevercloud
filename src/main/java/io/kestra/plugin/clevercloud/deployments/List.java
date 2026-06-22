package io.kestra.plugin.clevercloud.deployments;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List deployments for a Clever Cloud application.",
    description = """
        Retrieves the deployment history for a given application within an organisation.
        Returns a list of deployment objects and the total count.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List recent deployments for an application",
            full = true,
            code = """
                id: list_deployments
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.deployments.List
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    limit: 10
                """
        )
    }
)
public class List extends AbstractCleverCloudConnection implements RunnableTask<List.Output> {

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal apps)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Schema(
        title = "Maximum number of deployments to return.",
        description = "When not set, the API default applies (typically 20)."
    )
    @PluginProperty(group = "processing")
    private Property<Integer> limit;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow();

        var url = new StringBuilder(baseUrl(runContext))
            .append("organisations/").append(rOrgId)
            .append("/applications/").append(rAppId)
            .append("/deployments");

        runContext.render(limit).as(Integer.class).ifPresent(l -> url.append("?limit=").append(l));

        logger.info("Listing deployments for application {}", rAppId);
        var body = client.get(url.toString());
        var deployments = MAPPER.readValue(body, new TypeReference<ArrayList<Deployment>>() {});

        logger.info("Found {} deployments", deployments.size());
        return Output.builder()
            .deployments(deployments)
            .total(deployments.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of deployments returned by the API")
        private final java.util.List<Deployment> deployments;

        @Schema(title = "Total number of deployments returned")
        private final int total;
    }
}
