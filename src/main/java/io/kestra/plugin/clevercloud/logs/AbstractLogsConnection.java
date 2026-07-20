package io.kestra.plugin.clevercloud.logs;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.logs.model.LogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared base for the logs and log drain tasks/triggers, targeting Clever Cloud APIv4.
 * Unlike v2, there is no /self fallback: organisationId is always required, even for personal accounts.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractLogsConnection extends AbstractCleverCloudConnection {

    public static final String DEFAULT_BASE_URL_V4 = "https://api-bridge.clever-cloud.com/v4";

    @NotNull
    @Schema(
        title = "Organisation ID",
        description = "The Clever Cloud organisation or personal account ID that owns the application (orga_xxx or user_xxx). " +
            "Unlike the rest of this plugin, the v4 logs and log drain APIs have no /self shortcut, so this is always required."
    )
    @PluginProperty(group = "main")
    private Property<String> organisationId;

    @NotNull
    @Schema(title = "Application ID")
    @PluginProperty(group = "main")
    private Property<String> applicationId;

    // Fixed bounds for callers of fetchLogs without their own maxDuration/idleTimeout properties (LogPatternTrigger).
    protected static final Duration DEFAULT_MAX_DURATION = Duration.ofSeconds(30);
    protected static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration WATCHDOG_POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(5);

    protected String baseUrlV4() {
        return DEFAULT_BASE_URL_V4;
    }

    protected static String logsUrl(String baseUrlV4, String organisationId, String applicationId) {
        return join(baseUrlV4, "logs/organisations/" + encodeSegment(organisationId)
            + "/applications/" + encodeSegment(applicationId) + "/logs");
    }

    protected static String drainsUrl(String baseUrlV4, String organisationId, String applicationId) {
        return join(baseUrlV4, "drains/organisations/" + encodeSegment(organisationId)
            + "/applications/" + encodeSegment(applicationId) + "/drains");
    }

    // Appends query params in insertion order, skipping nulls, mirroring AbstractCleverCloudConnection#instancesUrl.
    protected static String appendQuery(String url, Map<String, String> params) {
        var sb = new StringBuilder(url);
        var separator = "?";
        for (var entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            sb.append(separator).append(entry.getKey()).append("=").append(encodeSegment(entry.getValue()));
            separator = "&";
        }
        return sb.toString();
    }

    /**
     * Consumes the Clever Cloud v4 logs SSE endpoint. Never relies on the server closing the connection:
     * a watchdog force-closes it on limit/until/maxDuration/idleTimeout, whichever comes first.
     */
    protected static List<LogEntry> fetchLogs(
        RunContext runContext,
        HttpConfiguration options,
        String url,
        String apiToken,
        int limit,
        Instant until,
        Duration maxDuration,
        Duration idleTimeout
    ) throws Exception {
        var logger = runContext.logger();
        var entries = Collections.synchronizedList(new ArrayList<LogEntry>());
        var lastEventAt = new AtomicReference<>(Instant.now());
        var timedOut = new AtomicBoolean(false);

        var executor = Executors.newSingleThreadExecutor();
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        try (var client = new HttpClient(runContext, options)) {
            var request = HttpRequest.builder()
                .uri(URI.create(url))
                .method("GET")
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Accept", "text/event-stream")
                .build();

            var future = executor.submit(() -> client.sseRequest(request, String.class, event -> {
                lastEventAt.set(Instant.now());
                var data = event.data();
                if (data == null || data.isBlank()) {
                    return;
                }
                LogEntry entry;
                try {
                    entry = MAPPER.readValue(data, LogEntry.class);
                } catch (Exception e) {
                    logger.debug("Skipping unparsable SSE log event: {}", e.getMessage());
                    return;
                }
                entries.add(entry);
                var reachedLimit = entries.size() >= limit;
                var reachedUntil = until != null && entry.getDate() != null && !entry.getDate().isBefore(until);
                if (reachedLimit || reachedUntil) {
                    // Close before throwing so Apache's client hits an already-closed connection instead of draining the open stream first.
                    try {
                        client.close();
                    } catch (IOException e) {
                        logger.debug("Failed to close SSE connection after reaching limit/until: {}", e.getMessage());
                    }
                    throw new SseStopSignal();
                }
            }));

            var deadline = Instant.now().plus(maxDuration);
            watchdog.scheduleWithFixedDelay(() -> {
                var now = Instant.now();
                var idleElapsed = Duration.between(lastEventAt.get(), now);
                if (now.isBefore(deadline) && idleElapsed.compareTo(idleTimeout) < 0) {
                    return;
                }
                if (timedOut.compareAndSet(false, true)) {
                    // Unblocks the worker thread stuck in sseRequest; the later try-with-resources close is then a no-op.
                    try {
                        client.close();
                    } catch (IOException e) {
                        logger.debug("Failed to close SSE connection after client-side timeout: {}", e.getMessage());
                    }
                }
            }, WATCHDOG_POLL_INTERVAL.toMillis(), WATCHDOG_POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

            try {
                var response = future.get(maxDuration.plus(SAFETY_MARGIN).toMillis(), TimeUnit.MILLISECONDS);
                var status = response.getStatus().getCode();
                if (status >= 400) {
                    logger.debug("Clever Cloud logs API GET {} returned {}", url, status);
                    throw new HttpClientResponseException(
                        "Clever Cloud API error " + status + " on GET " + url + ": check apiToken and that the resource exists",
                        response
                    );
                }
            } catch (ExecutionException e) {
                if (e.getCause() instanceof SseStopSignal) {
                    logger.debug("Reached limit/until while consuming the Clever Cloud logs SSE stream");
                } else if (!timedOut.get()) {
                    if (e.getCause() instanceof Exception cause) {
                        throw cause;
                    }
                    throw e;
                }
            } catch (TimeoutException e) {
                timedOut.set(true);
                client.close();
            }

            // Hitting the client-side limit is the normal path here, not a warning: the server rarely closes on its own.
            if (timedOut.get()) {
                logger.debug("Stopped logs SSE after client-side limit, collected {} entries", entries.size());
            }
        } finally {
            executor.shutdownNow();
            watchdog.shutdownNow();
            if (!executor.awaitTermination(SAFETY_MARGIN.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("Clever Cloud logs SSE worker thread did not terminate within {} after shutdown", SAFETY_MARGIN);
            }
        }

        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    // Unwinds out of sseRequest once limit/until is reached; caught in fetchLogs as normal completion, not a failure.
    private static final class SseStopSignal extends RuntimeException {
        private SseStopSignal() {
            super(null, null, false, false);
        }
    }

    protected static Map<String, String> logsQueryParams(String since, String until, Integer limit, String filter) {
        var params = new LinkedHashMap<String, String>();
        params.put("since", since);
        params.put("until", until);
        params.put("limit", limit != null ? String.valueOf(limit) : null);
        params.put("filter", filter);
        return params;
    }
}
