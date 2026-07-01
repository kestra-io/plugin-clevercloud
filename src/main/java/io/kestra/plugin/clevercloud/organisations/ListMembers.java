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
import io.kestra.plugin.clevercloud.organisations.model.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "List members of a Clever Cloud organisation",
    description = """
        Returns all members of the given organisation with their role and job title.
        Each entry contains a user info object (id, email, name, avatar) along with
        the role assigned within this organisation.
        The /self/members endpoint does not exist on the Clever Cloud API, so organisationId is required.
        Use fetchType to control how the members are exposed in the output.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List all members of an organisation",
            full = true,
            code = """
                id: list_org_members
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.organisations.ListMembers
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class ListMembers extends AbstractCleverCloudConnection implements RunnableTask<ListMembers.Output> {

    @Schema(
        title = "Organisation ID",
        description = "Required. The /self/members endpoint does not exist on the Clever Cloud API."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(title = "How to fetch the results", description = "FETCH returns all items in the task output, FETCH_ONE returns the first item, STORE writes the items to Kestra internal storage as an ion file and returns its uri, NONE returns nothing but the count")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required for ListMembers: /self/members does not exist")
        );
        var url = join(baseUrl(), "organisations/" + rOrgId + "/members");

        logger.info("Listing members for organisation {}", rOrgId);
        var body = makeCall(runContext, buildGetRequest(url));
        var members = MAPPER.readValue(body, new TypeReference<ArrayList<Member>>() {});

        logger.info("Found {} member(s)", members.size());

        var outputBuilder = Output.builder().total(members.size());

        switch (runContext.render(fetchType).as(FetchType.class).orElseThrow()) {
            case FETCH -> outputBuilder.members(members);
            case FETCH_ONE -> outputBuilder.member(members.isEmpty() ? null : members.getFirst());
            case STORE -> outputBuilder.uri(store(runContext, members));
            case NONE -> {
            }
        }

        return outputBuilder.build();
    }

    private URI store(RunContext runContext, List<Member> members) throws Exception {
        var tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var writer = new FileWriter(tempFile)) {
            FileSerde.writeAll(writer, Flux.fromIterable(members)).block();
        }

        return runContext.storage().putFile(tempFile);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of organisation members", description = "Populated when fetchType is FETCH")
        private final List<Member> members;

        @Schema(title = "First member returned by the API", description = "Populated when fetchType is FETCH_ONE, null if no member was found")
        private final Member member;

        @Schema(title = "URI of the stored members", description = "Populated when fetchType is STORE, points to an ion file in Kestra internal storage")
        private final URI uri;

        @Schema(title = "Total number of members returned")
        private final int total;
    }
}
