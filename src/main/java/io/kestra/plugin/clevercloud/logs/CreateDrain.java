package io.kestra.plugin.clevercloud.logs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.logs.model.Drain;
import io.kestra.plugin.clevercloud.logs.model.DrainKind;
import io.kestra.plugin.clevercloud.logs.model.DrainType;
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
    title = "Create a log drain forwarding a Clever Cloud application's logs",
    description = """
        Creates a drain that forwards an application's logs to an external observability platform,
        via POST /v4/drains/organisations/{organisationId}/applications/{applicationId}/drains.

        Supported drainType values: RAW_HTTP, SYSLOG_TCP, SYSLOG_UDP, DATADOG, ELASTICSEARCH,
        NEWRELIC. There is no dedicated OVHCLOUD type on the Clever Cloud API: forward to OVHcloud
        (or any other generic HTTP or syslog ingestion endpoint) via RAW_HTTP or SYSLOG_TCP/SYSLOG_UDP.

        This task returns as soon as the drain is created; it does not wait for it to reach the
        ENABLED status. organisationId is always required (no /self shortcut for personal accounts).
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create a Datadog log drain",
            full = true,
            code = """
                id: setup_log_drain
                namespace: company.team

                tasks:
                  - id: create_drain
                    type: io.kestra.plugin.clevercloud.logs.CreateDrain
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    drainType: DATADOG
                    url: "https://http-intake.logs.datadoghq.com/api/v2/logs"
                    apiKey: "{{ secret('DATADOG_API_KEY') }}"
                """
        )
    }
)
public class CreateDrain extends AbstractLogsConnection implements RunnableTask<CreateDrain.Output> {

    @NotNull
    @Schema(
        title = "Recipient type",
        description = "One of RAW_HTTP, SYSLOG_TCP, SYSLOG_UDP, DATADOG, ELASTICSEARCH, NEWRELIC."
    )
    @PluginProperty(group = "main")
    private Property<DrainType> drainType;

    @Schema(
        title = "Which log stream this drain forwards",
        description = "One of LOG (application runtime logs), ACCESSLOG, AUDITLOG. Defaults to LOG."
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<DrainKind> kind = Property.ofValue(DrainKind.LOG);

    @NotNull
    @Schema(title = "Destination URL to forward logs to")
    @PluginProperty(group = "main")
    private Property<String> url;

    @Schema(
        title = "Username for the destination",
        description = "Only used when drainType is RAW_HTTP or ELASTICSEARCH."
    )
    @PluginProperty(group = "connection")
    private Property<String> username;

    @Schema(
        title = "Password for the destination",
        description = "Only used when drainType is RAW_HTTP or ELASTICSEARCH."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> password;

    @Schema(
        title = "Elasticsearch index prefix",
        description = "Only used when drainType is ELASTICSEARCH."
    )
    @PluginProperty(group = "processing")
    private Property<String> indexPrefix;

    @Schema(
        title = "New Relic API key",
        description = "Only used when drainType is NEWRELIC."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> apiKey;

    @Schema(
        title = "RFC 5424 structured data parameters",
        description = "Only used when drainType is SYSLOG_TCP or SYSLOG_UDP."
    )
    @PluginProperty(group = "advanced")
    private Property<String> structuredDataParameters;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rOrgId = runContext.render(getOrganisationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required")
        );
        var rAppId = runContext.render(getApplicationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );
        var rDrainType = runContext.render(drainType).as(DrainType.class).orElseThrow(
            () -> new IllegalArgumentException("drainType is required")
        );
        var rKind = runContext.render(kind).as(DrainKind.class).orElse(DrainKind.LOG);
        var rUrl = runContext.render(url).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("url is required")
        );

        var recipient = new LinkedHashMap<String, Object>();
        recipient.put("type", rDrainType.name());
        recipient.put("url", rUrl);

        if (rDrainType == DrainType.RAW_HTTP || rDrainType == DrainType.ELASTICSEARCH) {
            runContext.render(username).as(String.class).ifPresent(v -> recipient.put("username", v));
            runContext.render(password).as(String.class).ifPresent(v -> recipient.put("password", v));
        }
        if (rDrainType == DrainType.ELASTICSEARCH) {
            runContext.render(indexPrefix).as(String.class).ifPresent(v -> recipient.put("index", v));
        }
        if (rDrainType == DrainType.NEWRELIC) {
            runContext.render(apiKey).as(String.class).ifPresent(v -> recipient.put("apiKey", v));
        }
        if (rDrainType == DrainType.SYSLOG_TCP || rDrainType == DrainType.SYSLOG_UDP) {
            runContext.render(structuredDataParameters).as(String.class)
                .ifPresent(v -> recipient.put("rfc5424StructuredDataParameters", v));
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("kind", rKind.name());
        payload.put("recipient", recipient);

        var drainsUrl = drainsUrl(baseUrlV4(), rOrgId, rAppId);

        logger.info("Creating {} log drain for application {}", rDrainType, rAppId);
        var body = makeCall(runContext, buildPostRequest(drainsUrl, payload));
        var drain = MAPPER.readValue(body, Drain.class);

        logger.info("Created log drain {}", drain.getId());

        return Output.builder()
            .id(drain.getId())
            .kind(drain.getKind())
            .status(drain.getStatus() != null ? drain.getStatus().getState() : null)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "ID of the created log drain")
        private final String id;

        @Schema(title = "Which log stream the drain forwards")
        private final String kind;

        @Schema(title = "Drain status returned by the API", description = "One of CREATED, ENABLING, ENABLED, DISABLING, DISABLED, DELETED, extracted from the nested status object the API returns.")
        private final String status;
    }
}
