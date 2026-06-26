package io.kestra.plugin.clevercloud.organisations;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.organisations.model.Application;
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
    title = "List applications in a Clever Cloud organisation",
    description = """
        Returns all applications deployed in the given organisation.
        Each entry includes the application ID, name, description, zone, and instance type.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List all applications in an organisation",
            full = true,
            code = """
                id: list_org_applications
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.organisations.ListApplications
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        )
    }
)
public class ListApplications extends AbstractCleverCloudConnection implements RunnableTask<ListApplications.Output> {

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal accounts)")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var client = signedClient(runContext);

        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var url = baseUrl(runContext) + "organisations/" + rOrgId + "/applications";

        logger.info("Listing applications for organisation {}", rOrgId);
        var body = client.get(url);
        var applications = MAPPER.readValue(body, new TypeReference<ArrayList<Application>>() {});

        logger.info("Found {} application(s)", applications.size());
        return Output.builder()
            .applications(applications)
            .total(applications.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "List of applications in the organisation")
        private final List<Application> applications;

        @Schema(title = "Total number of applications returned")
        private final int total;
    }
}
