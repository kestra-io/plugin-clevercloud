package io.kestra.plugin.clevercloud.logs;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.logs.model.Drain;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List log drains configured for a Clever Cloud application",
    description = """
        Lists the log drains forwarding an application's logs to an external observability
        platform, via GET /v4/drains/organisations/{organisationId}/applications/{applicationId}/drains.

        organisationId is always required here: unlike the rest of this plugin, the v4 drains API
        has no /self shortcut for personal accounts.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List log drains for an application",
            full = true,
            code = """
                id: list_log_drains
                namespace: company.team

                tasks:
                  - id: list_drains
                    type: io.kestra.plugin.clevercloud.logs.ListDrains
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class ListDrains extends AbstractLogsConnection implements RunnableTask<ListDrains.Output> {

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all items, FETCH_ONE returns the first item, STORE saves them to internal storage as an ION file, NONE returns only the count."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(getOrganisationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required")
        );
        var rAppId = runContext.render(getApplicationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var url = drainsUrl(baseUrlV4(), rOrgId, rAppId);

        logger.info("Listing log drains for application {}", rAppId);
        var body = makeCall(runContext, buildGetRequest(url));
        var drains = MAPPER.readValue(body, new TypeReference<ArrayList<Drain>>() {});

        logger.info("Found {} log drain(s)", drains.size());

        var result = fetchOutput(runContext, fetchType, drains);
        return Output.builder()
            .drains(result.items())
            .drain(result.first())
            .uri(result.uri())
            .total(result.total())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of log drains", description = "Populated when fetchType is FETCH.")
        private final List<Drain> drains;

        @Schema(title = "First log drain returned by the API", description = "Populated when fetchType is FETCH_ONE, null if no drain was found.")
        private final Drain drain;

        @Schema(title = "URI of the stored log drains", description = "Populated when fetchType is STORE, points to an ION file in Kestra internal storage.")
        private final URI uri;

        @Schema(title = "Total number of log drains returned")
        private final int total;
    }
}
