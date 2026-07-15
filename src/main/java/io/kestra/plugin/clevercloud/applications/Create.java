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

import java.util.LinkedHashMap;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a Clever Cloud application",
    description = """
        Creates a new application in the given organisation or personal account.
        Covers the required and most commonly used runtime and scaling fields; use the Clever
        Cloud console for advanced options such as custom domains or OAuth-based deployment.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create a Node.js application in an organisation",
            full = true,
            code = """
                id: create_application
                namespace: company.team

                tasks:
                  - id: create
                    type: io.kestra.plugin.clevercloud.applications.Create
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    name: my-node-app
                    zone: par
                    instanceType: node
                    instanceVersion: "20260617"
                    minInstances: 1
                    maxInstances: 2
                    minFlavor: XS
                    maxFlavor: S
                """
        )
    }
)
public class Create extends AbstractCleverCloudConnection implements RunnableTask<Create.Output> {

    @Schema(
        title = "Organisation ID",
        description = "When omitted, the personal account endpoint (/self) is used instead."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @Schema(title = "Application name")
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> name;

    @Schema(title = "Application description")
    @PluginProperty(group = "main")
    private Property<String> applicationDescription;

    @Schema(title = "Deployment zone, e.g. par, rbx, mtl")
    @PluginProperty(group = "main")
    private Property<String> zone;

    @Schema(title = "Runtime type, e.g. node, java, docker, php")
    @PluginProperty(group = "execution")
    private Property<String> instanceType;

    @Schema(title = "Runtime version, e.g. a Node.js or Java version identifier from the Clever Cloud console")
    @PluginProperty(group = "execution")
    private Property<String> instanceVersion;

    @Schema(title = "Minimum number of running instances")
    @PluginProperty(group = "execution")
    private Property<Integer> minInstances;

    @Schema(title = "Maximum number of running instances")
    @PluginProperty(group = "execution")
    private Property<Integer> maxInstances;

    @Schema(title = "Smallest instance flavor allowed to scale down to, e.g. XS")
    @PluginProperty(group = "execution")
    private Property<String> minFlavor;

    @Schema(title = "Largest instance flavor allowed to scale up to, e.g. M")
    @PluginProperty(group = "execution")
    private Property<String> maxFlavor;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(organisationId).as(String.class).orElse(null);
        var rName = runContext.render(name).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("name is required")
        );

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        runContext.render(applicationDescription).as(String.class).ifPresent(v -> payload.put("description", v));
        runContext.render(zone).as(String.class).ifPresent(v -> payload.put("zone", v));
        runContext.render(instanceType).as(String.class).ifPresent(v -> payload.put("instanceType", v));
        runContext.render(instanceVersion).as(String.class).ifPresent(v -> payload.put("instanceVersion", v));
        runContext.render(minInstances).as(Integer.class).ifPresent(v -> payload.put("minInstances", v));
        runContext.render(maxInstances).as(Integer.class).ifPresent(v -> payload.put("maxInstances", v));
        runContext.render(minFlavor).as(String.class).ifPresent(v -> payload.put("minFlavor", v));
        runContext.render(maxFlavor).as(String.class).ifPresent(v -> payload.put("maxFlavor", v));

        var url = resourceUrl(baseUrl(), rOrgId, "applications");

        logger.info("Creating application {} for {}", rName, rOrgId != null ? "organisation " + rOrgId : "personal account");
        var body = makeCall(runContext, buildPostRequest(url, payload));
        var app = MAPPER.readValue(body, Application.class);

        logger.info("Created application {}", app.getId());

        var instance = app.getInstance();
        return Output.builder()
            .id(app.getId())
            .name(app.getName())
            .zone(app.getZone())
            .deployUrl(app.getDeployUrl())
            .instanceType(instance != null ? instance.getType() : null)
            .instanceVersion(instance != null ? instance.getVersion() : null)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "ID of the created application")
        private final String id;

        @Schema(title = "Application name")
        private final String name;

        @Schema(title = "Deployment zone")
        private final String zone;

        @Schema(title = "URL to push code to for deployment (git remote or SFTP)")
        private final String deployUrl;

        @Schema(title = "Runtime type")
        private final String instanceType;

        @Schema(title = "Runtime version")
        private final String instanceVersion;
    }
}
