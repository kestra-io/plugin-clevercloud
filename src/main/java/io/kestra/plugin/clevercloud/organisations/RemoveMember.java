package io.kestra.plugin.clevercloud.organisations;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Remove a member from a Clever Cloud organisation",
    description = """
        Removes the specified user from the organisation. The user ID can be obtained
        from the ListMembers task (member.id field).
        organisationId is required: the /self/members endpoint does not exist.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Remove a member from an organisation",
            full = true,
            code = """
                id: remove_org_member
                namespace: company.team

                tasks:
                  - id: remove
                    type: io.kestra.plugin.clevercloud.organisations.RemoveMember
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    userId: "user_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class RemoveMember extends AbstractCleverCloudConnection implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Organisation ID",
        description = "Required. The /self/members endpoint does not exist on the Clever Cloud API."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(
        title = "User ID of the member to remove",
        description = "Obtain this from the ListMembers task: {{ outputs.list.members[0].member.id }}."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> userId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var rUserId = runContext.render(userId).as(String.class).orElseThrow();

        var url = join(baseUrl(), "organisations/" + rOrgId + "/members/" + rUserId);

        logger.info("Removing member {} from organisation {}", rUserId, rOrgId);
        makeCall(runContext, buildDeleteRequest(url));

        return null;
    }
}
