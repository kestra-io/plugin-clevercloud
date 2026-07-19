package io.kestra.plugin.clevercloud.logs;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.logs.model.LogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger when a Clever Cloud application log line matches a regex pattern",
    description = """
        Polls application runtime logs at each interval and fires when a log line produced since
        the previous evaluation matches the given regex pattern.

        Only log lines whose date is strictly after the previous evaluation cutoff are considered,
        so already-seen lines never re-fire the trigger. If more than one new line matches in the
        same poll, only the most recent one fires the trigger; the interval should be kept short
        enough that bursts of matching lines are unlikely to be missed.

        organisationId is always required here: unlike the rest of this plugin, the v4 logs API
        has no /self shortcut for personal accounts.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Alert when a critical error appears in logs",
            full = true,
            code = """
                id: alert_on_error_log
                namespace: company.team

                triggers:
                  - id: on_error_log
                    type: io.kestra.plugin.clevercloud.logs.LogPatternTrigger
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    pattern: "FATAL|OutOfMemoryError"
                    interval: PT1M

                tasks:
                  - id: alert
                    type: io.kestra.plugin.core.log.Log
                    message: "Critical log detected: {{ trigger.matchedLine }}"
                """
        )
    }
)
public class LogPatternTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<LogPatternTrigger.Output> {

    @NotNull
    @Schema(title = "API token", description = "Bearer token for the Clever Cloud API. Store as a Kestra secret and reference with {{ secret('CC_API_TOKEN') }}.")
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> apiToken;

    @Schema(title = "HTTP client options", description = "Optional HttpConfiguration applied to every Clever Cloud API call, including timeouts and proxy settings.")
    @PluginProperty(group = "advanced")
    HttpConfiguration options;

    protected String baseUrlV4() {
        return AbstractLogsConnection.DEFAULT_BASE_URL_V4;
    }

    @NotNull
    @Schema(
        title = "Organisation ID",
        description = "Required. Unlike the rest of this plugin, the v4 logs API has no /self shortcut for personal accounts."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @NotNull
    @Schema(title = "Application ID to watch")
    @PluginProperty(group = "main")
    private Property<String> applicationId;

    @NotNull
    @Schema(title = "Regex pattern to match against each log line's message")
    @PluginProperty(group = "main")
    private Property<String> pattern;

    @Schema(
        title = "Maximum number of log lines to fetch per poll",
        description = "Defaults to 200. Must be between 1 and 10000. Increase this value if logs may arrive faster than the poll interval."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<Integer> limit = Property.ofValue(200);

    @Schema(
        title = "How often to check for matching log lines",
        description = "ISO-8601 duration. Minimum PT30S is recommended to avoid overloading the API. Defaults to PT1M."
    )
    @PluginProperty(group = "reliability")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(1);

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rApiToken = runContext.render(apiToken).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required")
        );
        var rAppId = runContext.render(applicationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );
        var rPattern = Pattern.compile(runContext.render(pattern).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("pattern is required")
        ));
        var rLimit = runContext.render(limit).as(Integer.class).orElse(200);
        if (rLimit < 1 || rLimit > 10000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000, got " + rLimit);
        }

        var cutoff = context.getDate().toInstant();
        var now = Instant.now();

        var url = AbstractLogsConnection.appendQuery(
            AbstractLogsConnection.logsUrl(baseUrlV4(), rOrgId, rAppId),
            AbstractLogsConnection.logsQueryParams(cutoff.toString(), now.toString(), rLimit, null)
        );

        logger.debug("Polling logs for application {}", rAppId);
        var entries = AbstractLogsConnection.fetchLogs(runContext, options, url, rApiToken);

        var matches = entries.stream()
            .filter(e -> e.getDate() != null && e.getDate().isAfter(cutoff))
            .filter(e -> e.getMessage() != null && rPattern.matcher(e.getMessage()).find())
            .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        if (matches.size() > 1) {
            logger.warn(
                "Found {} new log lines matching pattern '{}' in this poll, only the most recent will fire the trigger, the others are skipped",
                matches.size(), rPattern.pattern()
            );
        }

        var matched = matches.getLast();
        logger.info("Log line matched pattern '{}' for application {}", rPattern.pattern(), rAppId);

        var output = Output.builder()
            .matchedLine(matched.getMessage())
            .matchedAt(matched.getDate())
            .severity(matched.getSeverity())
            .service(matched.getService())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Log message that matched the pattern")
        private final String matchedLine;

        @Schema(title = "Instant the matching log line was emitted")
        private final Instant matchedAt;

        @Schema(title = "Severity of the matching log line")
        private final String severity;

        @Schema(title = "Service that emitted the matching log line")
        private final String service;
    }
}
