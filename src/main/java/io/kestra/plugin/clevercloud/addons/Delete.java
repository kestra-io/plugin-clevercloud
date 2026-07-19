package io.kestra.plugin.clevercloud.addons;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.addons.model.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
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
    title = "Delete a Clever Cloud add-on",
    description = """
        Permanently deletes the add-on and its data. If the add-on is still linked to an
        application, unlink it first with addons.UnlinkFromApplication.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an add-on",
            full = true,
            code = """
                id: delete_addon
                namespace: company.team

                tasks:
                  - id: delete
                    type: io.kestra.plugin.clevercloud.addons.Delete
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    addonId: "addon_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class Delete extends AbstractCleverCloudConnection implements RunnableTask<Delete.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "Add-on ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> addonId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAddonId = runContext.render(addonId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("addonId is required")
        );

        var url = resourceUrl(baseUrl(), rOrgId, "addons/" + encodeSegment(rAddonId));

        logger.info("Deleting add-on {}", rAddonId);
        var body = makeCall(runContext, buildDeleteRequest(url));
        var message = MAPPER.readValue(body, Message.class);

        return Output.builder().message(message.getMessage()).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Confirmation message returned by the API")
        private final String message;
    }
}
