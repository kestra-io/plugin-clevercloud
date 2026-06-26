package io.kestra.plugin.clevercloud.organisations;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.organisations.model.Addon;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List add-ons in a Clever Cloud organisation",
    description = """
        Returns all add-ons provisioned in the given organisation.
        Each entry includes the add-on ID, name, region, provider info, and plan.
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
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class ListAddons extends AbstractCleverCloudConnection implements RunnableTask<ListAddons.Output> {

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal accounts)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var url = baseUrl(runContext) + "organisations/" + rOrgId + "/addons";

        logger.info("Listing add-ons for organisation {}", rOrgId);
        var body = client.get(url);
        var addons = MAPPER.readValue(body, new TypeReference<ArrayList<Addon>>() {});

        logger.info("Found {} add-on(s)", addons.size());
        return Output.builder()
            .addons(addons)
            .total(addons.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of add-ons in the organisation")
        private final List<Addon> addons;

        @Schema(title = "Total number of add-ons returned")
        private final int total;
    }
}
