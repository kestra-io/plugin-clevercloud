package io.kestra.plugin.clevercloud.logs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.logs.model.LogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch application runtime logs for a Clever Cloud application",
    description = """
        Retrieves application runtime logs emitted within a bounded time window, via the Clever
        Cloud APIv4 logs endpoint (GET /v4/logs/organisations/{organisationId}/applications/{applicationId}/logs).

        This endpoint is Server-Sent Events (SSE) based even for a bounded fetch: the connection
        closes on its own once "until" is reached, so this task always terminates deterministically.
        Set until explicitly (rather than leaving it open-ended) to fetch a historical window; use
        logs.Stream instead to tail logs as they are produced.

        organisationId is always required here: unlike the rest of this plugin, the v4 logs API has
        no /self shortcut for personal accounts.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch logs produced since the start of this flow execution",
            full = true,
            code = """
                id: post_deploy_log_check
                namespace: company.team

                tasks:
                  - id: fetch_logs
                    type: io.kestra.plugin.clevercloud.logs.Fetch
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    applicationId: "app_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    since: "{{ execution.startDate }}"
                    limit: 200

                  - id: log_output
                    type: io.kestra.plugin.core.log.Log
                    message: "Captured {{ outputs.fetch_logs.total }} log lines"
                """
        )
    }
)
public class Fetch extends AbstractLogsConnection implements RunnableTask<Fetch.Output> {

    @NotNull
    @Schema(
        title = "Start of the time range to fetch logs from",
        description = "ISO-8601 instant. Only logs emitted at or after this time are returned."
    )
    @PluginProperty(group = "main")
    private Property<Instant> since;

    @Schema(
        title = "End of the time range to fetch logs from",
        description = "ISO-8601 instant, defaults to now. Kept bounded on purpose so the underlying " +
            "SSE stream always closes once the historical window is exhausted."
    )
    @PluginProperty(group = "main")
    private Property<Instant> until;

    @Schema(
        title = "Maximum number of log lines to return",
        description = "Defaults to 100. Must be between 1 and 10000."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<Integer> limit = Property.ofValue(100);

    @Schema(
        title = "Server-side text filter",
        description = "Optional free-text filter applied by the Clever Cloud API before logs are returned."
    )
    @PluginProperty(group = "processing")
    private Property<String> filter;

    @Schema(
        title = "How to fetch the results",
        description = "FETCH returns all items, FETCH_ONE returns the first item, STORE saves them to internal storage as an ion file, NONE returns only the count."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

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
        var rSince = runContext.render(since).as(Instant.class).orElseThrow(
            () -> new IllegalArgumentException("since is required")
        );
        var rUntil = runContext.render(until).as(Instant.class).orElse(Instant.now());
        var rLimit = runContext.render(limit).as(Integer.class).orElse(100);
        if (rLimit < 1 || rLimit > 10000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000, got " + rLimit);
        }
        var rFilter = runContext.render(filter).as(String.class).orElse(null);

        var url = appendQuery(
            logsUrl(baseUrlV4(), rOrgId, rAppId),
            logsQueryParams(rSince.toString(), rUntil.toString(), rLimit, rFilter)
        );

        logger.info("Fetching logs for application {} between {} and {}", rAppId, rSince, rUntil);
        var entries = fetchLogs(runContext, getOptions(), url, rApiToken);
        logger.info("Found {} log line(s)", entries.size());

        var result = fetchOutput(runContext, fetchType, entries);
        return Output.builder()
            .logs(result.items())
            .log(result.first())
            .uri(result.uri())
            .total(result.total())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Log lines returned by the API", description = "Populated when fetchType is FETCH.")
        private final List<LogEntry> logs;

        @Schema(title = "First log line returned by the API", description = "Populated when fetchType is FETCH_ONE, null if no log line was found.")
        private final LogEntry log;

        @Schema(title = "URI of the stored log lines", description = "Populated when fetchType is STORE, points to an ion file in Kestra internal storage.")
        private final URI uri;

        @Schema(title = "Total number of log lines returned")
        private final int total;
    }
}
