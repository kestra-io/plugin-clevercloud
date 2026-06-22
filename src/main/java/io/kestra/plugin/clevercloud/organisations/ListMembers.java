package io.kestra.plugin.clevercloud.organisations;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.organisations.model.Member;
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
    title = "List members of a Clever Cloud organisation.",
    description = """
        Returns all members of the given organisation with their role and job title.
        Each entry contains a user info object (id, email, name, avatar) along with
        the role assigned within this organisation.
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
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class ListMembers extends AbstractCleverCloudConnection implements RunnableTask<ListMembers.Output> {

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal accounts)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var url = baseUrl(runContext) + "organisations/" + rOrgId + "/members";

        logger.info("Listing members for organisation {}", rOrgId);
        var body = client.get(url);
        var members = MAPPER.readValue(body, new TypeReference<ArrayList<Member>>() {});

        logger.info("Found {} member(s)", members.size());
        return Output.builder()
            .members(members)
            .total(members.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of organisation members")
        private final java.util.List<Member> members;

        @Schema(title = "Total number of members returned")
        private final int total;
    }
}
