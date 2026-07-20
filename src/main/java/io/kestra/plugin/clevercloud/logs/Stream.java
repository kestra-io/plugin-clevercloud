package io.kestra.plugin.clevercloud.logs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.logs.model.LogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Consume live Clever Cloud application logs for a bounded duration",
    description = """
        Connects to the Clever Cloud APIv4 logs SSE endpoint and collects logs produced during the
        given duration, then returns. duration is enforced client-side: the connection is force-closed
        once it elapses even if the server keeps the SSE stream open or never sends a closing signal,
        so this task always terminates deterministically. Set duration to how long you want to keep
        listening (defaults to PT1M, capped at PT15M). For a historical, already-produced window
        instead, use logs.Fetch.

        organisationId is always required here: unlike the rest of this plugin, the v4 logs API has
        no /self shortcut for personal accounts.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Tail logs for one minute right after a deployment",
            full = true,
            code = """
                id: tail_logs_after_deploy
                namespace: company.team

                tasks:
                  - id: tail_logs
                    type: io.kestra.plugin.clevercloud.logs.Stream
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    duration: PT1M

                  - id: log_output
                    type: io.kestra.plugin.core.log.Log
                    message: "Captured {{ outputs.tail_logs.total }} live log lines"
                """
        )
    }
)
public class Stream extends AbstractLogsConnection implements RunnableTask<Stream.Output> {

    @Schema(
        title = "How long to keep listening for live logs",
        description = "ISO-8601 duration. Defaults to PT1M, capped at PT15M so the task always terminates in a bounded time."
    )
    @PluginProperty(group = "main")
    @Builder.Default
    private Property<Duration> duration = Property.ofValue(Duration.ofMinutes(1));

    @Schema(
        title = "Maximum number of log lines to collect",
        description = "Defaults to 500. Must be between 1 and 10000."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<Integer> limit = Property.ofValue(500);

    @Schema(
        title = "Server-side text filter",
        description = "Optional free-text filter applied by the Clever Cloud API before logs are returned."
    )
    @PluginProperty(group = "processing")
    private Property<String> filter;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rApiToken = renderApiToken(runContext);
        var rOrgId = runContext.render(getOrganisationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required")
        );
        var rAppId = runContext.render(getApplicationId()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("applicationId is required")
        );
        var rDuration = runContext.render(duration).as(Duration.class).orElse(Duration.ofMinutes(1));
        if (rDuration.isNegative() || rDuration.isZero() || rDuration.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("duration must be between PT1S and PT15M, got " + rDuration);
        }
        var rLimit = runContext.render(limit).as(Integer.class).orElse(500);
        if (rLimit < 1 || rLimit > 10000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000, got " + rLimit);
        }
        var rFilter = runContext.render(filter).as(String.class).orElse(null);

        var since = Instant.now();
        var until = since.plus(rDuration);

        var url = appendQuery(
            logsUrl(baseUrlV4(), rOrgId, rAppId),
            logsQueryParams(since.toString(), until.toString(), rLimit, rFilter)
        );

        logger.info("Streaming logs for application {} for {}", rAppId, rDuration);
        // idleTimeout equals the hard deadline: unlike Fetch, silence alone must never end a live tail early.
        var entries = fetchLogs(runContext, new LogsSseRequest(getOptions(), url, rApiToken, rLimit, until, rDuration, rDuration));
        logger.info("Collected {} live log line(s)", entries.size());

        return Output.builder()
            .logs(entries)
            .total(entries.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Log lines collected during the stream window")
        private final List<LogEntry> logs;

        @Schema(title = "Total number of log lines collected")
        private final int total;
    }
}
