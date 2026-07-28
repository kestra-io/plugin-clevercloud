package io.kestra.plugin.clevercloud.addons;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.addons.model.Addon;
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
    title = "List add-ons in a Clever Cloud organisation or personal account",
    description = """
        Returns all add-ons provisioned in the given organisation or personal account.
        When organisationId is omitted, lists add-ons under the personal account via /self.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List all add-ons in an organisation",
            full = true,
            code = """
                id: list_org_addons
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.addons.List
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        ),
        @Example(
            title = "List all add-ons for a personal account",
            full = true,
            code = """
                id: list_personal_addons
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.addons.List
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                """
        )
    },
    aliases = "io.kestra.plugin.clevercloud.organisations.ListAddons"
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
        description = "FETCH returns all items, FETCH_ONE returns the first item, STORE saves them to internal storage as an ION file, NONE returns only the count."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var url = resourceUrl(baseUrl(), rOrgId, "addons");

        logger.info("Listing add-ons for {}", rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildGetRequest(url));
        var addons = MAPPER.readValue(body, new TypeReference<ArrayList<Addon>>() {});

        logger.info("Found {} add-on(s)", addons.size());

        var result = fetchOutput(runContext, fetchType, addons);
        return Output.builder()
            .addons(result.items())
            .addon(result.first())
            .uri(result.uri())
            .total(result.total())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "List of add-ons in the organisation or personal account",
            description = "Populated when fetchType is FETCH."
        )
        private final java.util.List<Addon> addons;

        @Schema(
            title = "First add-on returned by the API",
            description = "Populated when fetchType is FETCH_ONE, null if no add-on was found."
        )
        private final Addon addon;

        @Schema(
            title = "URI of the stored add-ons",
            description = "Populated when fetchType is STORE, points to an ION file in Kestra internal storage."
        )
        private final URI uri;

        @Schema(title = "Total number of add-ons returned")
        private final int total;
    }
}
