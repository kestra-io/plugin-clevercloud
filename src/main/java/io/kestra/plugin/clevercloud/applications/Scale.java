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
    title = "Scale a Clever Cloud application",
    description = """
        Updates the instance count and flavor bounds of an application. Only the scaling fields
        you set are changed: this task first reads the application's current definition and
        re-sends it in full, with your scaling fields overlaid on top, so name, zone, and other
        unset fields are never cleared.
        When organisationId is omitted, the personal account endpoint (/self) is used.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Scale an application to a fixed instance count and flavor",
            full = true,
            code = """
                id: scale_application
                namespace: company.team

                tasks:
                  - id: scale
                    type: io.kestra.plugin.clevercloud.applications.Scale
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    minInstances: 2
                    maxInstances: 4
                    minFlavor: S
                    maxFlavor: M
                """
        )
    }
)
public class Scale extends AbstractCleverCloudConnection implements RunnableTask<Scale.Output> {

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
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );

        var scaleFields = new LinkedHashMap<String, Object>();
        runContext.render(minInstances).as(Integer.class).ifPresent(v -> scaleFields.put("minInstances", v));
        runContext.render(maxInstances).as(Integer.class).ifPresent(v -> scaleFields.put("maxInstances", v));
        runContext.render(minFlavor).as(String.class).ifPresent(v -> scaleFields.put("minFlavor", v));
        runContext.render(maxFlavor).as(String.class).ifPresent(v -> scaleFields.put("maxFlavor", v));

        if (scaleFields.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one of minInstances, maxInstances, minFlavor, maxFlavor must be set to scale an application"
            );
        }

        var url = resourceUrl(baseUrl(), rOrgId, "applications/" + encodeSegment(rAppId));

        logger.info("Fetching current definition of application {} before scaling", rAppId);
        var currentBody = makeCall(runContext, buildGetRequest(url));
        var currentApp = MAPPER.readValue(currentBody, Application.class);

        var payload = toWannabeApplication(currentApp);
        payload.putAll(scaleFields);

        logger.info("Scaling application {}: {}", rAppId, scaleFields);
        var body = makeCall(runContext, buildPutRequest(url, payload));
        var app = MAPPER.readValue(body, Application.class);

        var instance = app.getInstance();
        return Output.builder()
            .minInstances(instance != null ? instance.getMinInstances() : null)
            .maxInstances(instance != null ? instance.getMaxInstances() : null)
            .minFlavor(instance != null && instance.getMinFlavor() != null ? instance.getMinFlavor().getName() : null)
            .maxFlavor(instance != null && instance.getMaxFlavor() != null ? instance.getMaxFlavor().getName() : null)
            .build();
    }

    /**
     * Rebuilds the WannabeApplication PUT body from the currently deployed application so a
     * scale request never clears fields it doesn't set (name, zone, instance type/version, ...).
     */
    private static LinkedHashMap<String, Object> toWannabeApplication(Application currentApp) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", currentApp.getName());
        if (currentApp.getDescription() != null) {
            payload.put("description", currentApp.getDescription());
        }
        if (currentApp.getZone() != null) {
            payload.put("zone", currentApp.getZone());
        }

        var instance = currentApp.getInstance();
        if (instance != null) {
            if (instance.getType() != null) {
                payload.put("instanceType", instance.getType());
            }
            if (instance.getVersion() != null) {
                payload.put("instanceVersion", instance.getVersion());
            }
            if (instance.getMinInstances() != null) {
                payload.put("minInstances", instance.getMinInstances());
            }
            if (instance.getMaxInstances() != null) {
                payload.put("maxInstances", instance.getMaxInstances());
            }
            if (instance.getMinFlavor() != null) {
                payload.put("minFlavor", instance.getMinFlavor().getName());
            }
            if (instance.getMaxFlavor() != null) {
                payload.put("maxFlavor", instance.getMaxFlavor().getName());
            }
        }

        return payload;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Minimum number of running instances after scaling")
        private final Integer minInstances;

        @Schema(title = "Maximum number of running instances after scaling")
        private final Integer maxInstances;

        @Schema(title = "Smallest instance flavor after scaling")
        private final String minFlavor;

        @Schema(title = "Largest instance flavor after scaling")
        private final String maxFlavor;
    }
}
