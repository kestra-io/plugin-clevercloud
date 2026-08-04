package io.kestra.plugin.clevercloud.deployments;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.deployments.model.Deployment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List deployments for a Clever Cloud application",
    description = """
        Retrieves the deployment history for a given application.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        Use fetchType to control how the deployments are exposed in the output.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List recent deployments for an organisation application",
            full = true,
            code = """
                id: list_deployments
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.deployments.List
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    limit: 10
                """
        ),
        @Example(
            title = "List recent deployments for a personal account application",
            full = true,
            code = """
                id: list_personal_deployments
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.deployments.List
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    limit: 10
                """
        )
    }
)
public class List extends AbstractCleverCloudConnection implements RunnableTask<List.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    private Property<String> applicationId;

    @Schema(
        title = "Maximum number of deployments to return",
        description = """
            Defaults to 50. Set to a higher value to retrieve more history.
            A large value combined with fetchType FETCH materializes the full history in the task
            output, which can be memory-intensive. Prefer fetchType STORE for large result sets.
            """
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<Integer> limit = Property.ofValue(50);

    @Schema(title = "How to fetch the results", description = "FETCH returns all items in the task output, FETCH_ONE returns the first item, STORE writes the items to Kestra internal storage as an ION file and returns its URI, NONE returns nothing but the count")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var urlBuilder = new StringBuilder(join(baseUrl(), resourceBase(rOrgId)))
            .append("/applications/").append(rAppId)
            .append("/deployments");

        runContext.render(limit).as(Integer.class).ifPresent(l -> urlBuilder.append("?limit=").append(l));

        logger.info("Listing deployments for application {}", rAppId);
        var body = makeCall(runContext, buildGetRequest(urlBuilder.toString()));
        var deployments = MAPPER.readValue(body, new TypeReference<ArrayList<Deployment>>() {});

        logger.info("Found {} deployments", deployments.size());

        var outputBuilder = Output.builder().total(deployments.size());

        switch (runContext.render(fetchType).as(FetchType.class).orElseThrow()) {
            case FETCH -> outputBuilder.deployments(deployments);
            case FETCH_ONE -> outputBuilder.deployment(deployments.isEmpty() ? null : deployments.getFirst());
            case STORE -> outputBuilder.uri(store(runContext, deployments));
            case NONE -> {
            }
        }

        return outputBuilder.build();
    }

    private URI store(RunContext runContext, java.util.List<Deployment> deployments) throws Exception {
        var tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            FileSerde.writeAll(writer, Flux.fromIterable(deployments)).block();
        }

        return runContext.storage().putFile(tempFile);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of deployments returned by the API", description = "Populated when fetchType is FETCH")
        private final java.util.List<Deployment> deployments;

        @Schema(title = "First deployment returned by the API", description = "Populated when fetchType is FETCH_ONE, null if no deployment was found")
        private final Deployment deployment;

        @Schema(title = "URI of the stored deployments", description = "Populated when fetchType is STORE, points to an ION file in Kestra internal storage")
        private final URI uri;

        @Schema(title = "Total number of deployments returned")
        private final int total;
    }
}
