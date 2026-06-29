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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List applications in a Clever Cloud organisation or personal account",
    description = """
        Returns all applications deployed in the given organisation or personal account.
        When organisationId is omitted, lists applications under the personal account via /self.
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
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        ),
        @Example(
            title = "List all applications for a personal account",
            full = true,
            code = """
                id: list_personal_applications
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.clevercloud.organisations.ListApplications
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                """
        )
    }
)
public class ListApplications extends AbstractCleverCloudConnection implements RunnableTask<ListApplications.Output> {

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
        var url = baseUrl(runContext) + "/" + resourceBase(rOrgId) + "/applications";

        logger.info("Listing applications for {}", rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildGetRequest(url));
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

        @Schema(title = "List of applications in the organisation or personal account")
        private final List<Application> applications;

        @Schema(title = "Total number of applications returned")
        private final int total;
    }
}
