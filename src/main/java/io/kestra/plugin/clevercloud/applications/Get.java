package io.kestra.plugin.clevercloud.applications;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.applications.model.Application;
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
    title = "Get details of a Clever Cloud application",
    description = """
        Retrieves a single application by its ID: zone, instance type and version, state,
        deploy URL, and scaling bounds.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch an application in an organisation",
            full = true,
            code = """
                id: get_application
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.clevercloud.applications.Get
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                """
        ),
        @Example(
            title = "Fetch a personal account application",
            full = true,
            code = """
                id: get_personal_application
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.clevercloud.applications.Get
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
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

    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> applicationId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var url = resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId));

        logger.info("Fetching application {}", rAppId);
        var body = makeCall(runContext, buildGetRequest(url));
        var app = MAPPER.readValue(body, Application.class);

        var instance = app.getInstance();
        return Output.builder()
            .id(app.getId())
            .name(app.getName())
            .description(app.getDescription())
            .zone(app.getZone())
            .state(app.getState())
            .deployUrl(app.getDeployUrl())
            .instanceType(instance != null ? instance.getType() : null)
            .instanceVersion(instance != null ? instance.getVersion() : null)
            .minInstances(instance != null ? instance.getMinInstances() : null)
            .maxInstances(instance != null ? instance.getMaxInstances() : null)
            .minFlavor(instance != null && instance.getMinFlavor() != null ? instance.getMinFlavor().getName() : null)
            .maxFlavor(instance != null && instance.getMaxFlavor() != null ? instance.getMaxFlavor().getName() : null)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Application ID")
        private final String id;

        @Schema(title = "Application name")
        private final String name;

        @Schema(title = "Application description")
        private final String description;

        @Schema(title = "Deployment zone, e.g. par")
        private final String zone;

        @Schema(
            title = "Application state",
            description = "One of SHOULD_BE_UP, SHOULD_BE_DOWN, WANTS_TO_BE_UP, MODERATED, DEFAULT_OF_PAYMENT."
        )
        private final String state;

        @Schema(title = "URL used to push code for deployment (git remote or SFTP)")
        private final String deployUrl;

        @Schema(title = "Runtime type, e.g. node, java, docker")
        private final String instanceType;

        @Schema(title = "Runtime version")
        private final String instanceVersion;

        @Schema(title = "Minimum number of running instances")
        private final Integer minInstances;

        @Schema(title = "Maximum number of running instances")
        private final Integer maxInstances;

        @Schema(title = "Smallest instance flavor allowed to scale down to")
        private final String minFlavor;

        @Schema(title = "Largest instance flavor allowed to scale up to")
        private final String maxFlavor;
    }
}
