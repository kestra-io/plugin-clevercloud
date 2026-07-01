package io.kestra.plugin.clevercloud.organisations;

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
import io.kestra.plugin.clevercloud.organisations.model.Addon;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
        Each entry includes the add-on ID, name, region, provider info, and plan.
        Use fetchType to control how the add-ons are exposed in the output.
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
                    type: io.kestra.plugin.clevercloud.organisations.ListAddons
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
                    type: io.kestra.plugin.clevercloud.organisations.ListAddons
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                """
        )
    }
)
public class ListAddons extends AbstractCleverCloudConnection implements RunnableTask<ListAddons.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "How to fetch the results", description = "FETCH returns all items in the task output, FETCH_ONE returns the first item, STORE writes the items to Kestra internal storage as an ion file and returns its uri, NONE returns nothing but the count")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var url = join(baseUrl(), resourceBase(rOrgId)) + "/addons";

        logger.info("Listing add-ons for {}", rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildGetRequest(url));
        var addons = MAPPER.readValue(body, new TypeReference<ArrayList<Addon>>() {});

        logger.info("Found {} add-on(s)", addons.size());

        var outputBuilder = Output.builder().total(addons.size());

        switch (runContext.render(fetchType).as(FetchType.class).orElseThrow()) {
            case FETCH -> outputBuilder.addons(addons);
            case FETCH_ONE -> outputBuilder.addon(addons.isEmpty() ? null : addons.getFirst());
            case STORE -> outputBuilder.uri(store(runContext, addons));
            case NONE -> {
            }
        }

        return outputBuilder.build();
    }

    private URI store(RunContext runContext, List<Addon> addons) throws Exception {
        var tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var writer = new FileWriter(tempFile)) {
            FileSerde.writeAll(writer, Flux.fromIterable(addons)).block();
        }

        return runContext.storage().putFile(tempFile);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of add-ons in the organisation or personal account", description = "Populated when fetchType is FETCH")
        private final List<Addon> addons;

        @Schema(title = "First add-on returned by the API", description = "Populated when fetchType is FETCH_ONE, null if no add-on was found")
        private final Addon addon;

        @Schema(title = "URI of the stored add-ons", description = "Populated when fetchType is STORE, points to an ion file in Kestra internal storage")
        private final URI uri;

        @Schema(title = "Total number of add-ons returned")
        private final int total;
    }
}
