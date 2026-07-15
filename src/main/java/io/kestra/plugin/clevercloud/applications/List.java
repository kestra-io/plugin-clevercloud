package io.kestra.plugin.clevercloud.applications;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.applications.model.Application;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List applications in a Clever Cloud organisation or personal account",
    description = """
        Returns all applications in the given organisation or personal account, with their zone,
        instance type, and state.
        When organisationId is omitted, lists applications under the personal account via /self.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List all applications in an organisation",
            full = true,
            code = """
                id: list_org_applications
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.applications.List
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        ),
        @Example(
            title = "List all applications for a personal account",
            full = true,
            code = """
                id: list_personal_applications
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.applications.List
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
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

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all items, FETCH_ONE returns the first item, STORE saves them to internal storage as an ion file, NONE returns only the count."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var url = resourceUrl(baseUrl(), rOrgId, "applications");

        logger.info("Listing applications for {}", rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildGetRequest(url));
        var applications = MAPPER.readValue(body, new TypeReference<ArrayList<Application>>() {});

        logger.info("Found {} application(s)", applications.size());

        var result = fetchOutput(runContext, fetchType, applications);
        return Output.builder()
            .applications(result.items())
            .application(result.first())
            .uri(result.uri())
            .total(result.total())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "List of applications in the organisation or personal account",
            description = "Populated when fetchType is FETCH."
        )
        private final java.util.List<Application> applications;

        @Schema(
            title = "First application returned by the API",
            description = "Populated when fetchType is FETCH_ONE, null if no application was found."
        )
        private final Application application;

        @Schema(
            title = "URI of the stored applications",
            description = "Populated when fetchType is STORE, points to an ion file in Kestra internal storage."
        )
        private final URI uri;

        @Schema(title = "Total number of applications returned")
        private final int total;
    }
}
