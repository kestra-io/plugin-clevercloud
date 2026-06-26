package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.organisations.model.OrgRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Add a member to a Clever Cloud organisation",
    description = """
        Invites a user to the organisation by email and assigns them a role.

        Valid roles: ADMIN, MANAGER, DEVELOPER, ACCOUNTING, READ_ONLY.
        The user receives an email invitation if they do not already have a Clever Cloud account.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Add a developer to an organisation",
            full = true,
            code = """
                id: add_org_member
                namespace: company.team

                tasks:
                  - id: add
                    type: io.kestra.plugin.clevercloud.organisations.AddMember
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    email: "developer@example.com"
                    role: DEVELOPER
                """
        )
    }
)
public class AddMember extends AbstractCleverCloudConnection implements RunnableTask<VoidOutput> {

    @Schema(title = "Organisation ID (orga_xxx)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(
        title = "Email address of the user to add",
        description = "The user will be invited to the organisation at this address."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> email;

    @Schema(
        title = "Role to assign to the new member",
        description = """
            Accepts: ADMIN (full control), MANAGER, DEVELOPER (can deploy),
            ACCOUNTING, READ_ONLY (view-only access).
            """
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<OrgRole> role;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var rEmail = runContext.render(email).as(String.class).orElseThrow();
        var rRole = runContext.render(role).as(OrgRole.class).orElseThrow();

        var url = baseUrl(runContext) + "organisations/" + rOrgId + "/members";
        var jsonBody = MAPPER.writeValueAsString(Map.of("email", rEmail, "role", rRole.name()));

        logger.info("Adding member {} with role {} to organisation {}", rEmail, rRole, rOrgId);
        client.post(url, jsonBody);

        return null;
    }
}
