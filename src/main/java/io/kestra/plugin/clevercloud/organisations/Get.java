package io.kestra.plugin.clevercloud.organisations;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.organisations.model.Organisation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get details of a Clever Cloud organisation.",
    description = """
        Retrieves the organisation record for a given ID.

        This endpoint requires an organisation ID starting with orga_xxx. Personal user
        accounts (user_xxx) are not supported by this endpoint and will return a 403.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch organisation details",
            full = true,
            code = """
                id: get_organisation
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.clevercloud.organisations.Get
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class Get extends AbstractCleverCloudConnection implements RunnableTask<Get.Output> {

    @Schema(title = "Organisation ID (orga_xxx)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var url = baseUrl(runContext) + "organisations/" + rOrgId;

        logger.info("Fetching organisation {}", rOrgId);
        var body = client.get(url);
        var org = MAPPER.readValue(body, Organisation.class);

        return Output.builder()
            .id(org.getId())
            .name(org.getName())
            .description(org.getDescription())
            .city(org.getCity())
            .country(org.getCountry())
            .avatar(org.getAvatar())
            .email(org.getEmail())
            .cleverEnterprise(org.isCleverEnterprise())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Organisation ID")
        private final String id;

        @Schema(title = "Organisation display name")
        private final String name;

        @Schema(title = "Organisation description")
        private final String description;

        @Schema(title = "City")
        private final String city;

        @Schema(title = "Country")
        private final String country;

        @Schema(title = "Avatar URL")
        private final String avatar;

        @Schema(title = "Billing contact email")
        private final String email;

        @Schema(title = "Whether the organisation has a Clever Enterprise contract")
        private final boolean cleverEnterprise;
    }
}
