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
    title = "Get details of a Clever Cloud organisation or personal account",
    description = """
        Retrieves the organisation or personal account record.
        When organisationId is omitted, returns the personal account details via /self.
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
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        ),
        @Example(
            title = "Fetch personal account details",
            full = true,
            code = """
                id: get_self
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.clevercloud.organisations.Get
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                """
        )
    }
)
public class Get extends AbstractCleverCloudConnection implements RunnableTask<Get.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var url = baseUrl() + "/" + resourceBase(rOrgId);

        logger.info("Fetching {}", rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildGetRequest(url));
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
