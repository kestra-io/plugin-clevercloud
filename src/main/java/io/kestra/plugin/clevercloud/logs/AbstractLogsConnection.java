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
 * Shared base for the logs and log drain tasks/triggers, all of which hit Clever Cloud APIv4
 * (as opposed to the rest of this plugin, which targets APIv2). Unlike the v2 endpoints, the v4
 * logs and drains endpoints have no /self fallback: organisationId is always required, even for
 * personal accounts (pass the personal account's user_xxx id as organisationId in that case).
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

    /**
     * Default hard cap and idle cutoff shared by callers of {@link #fetchLogs} that do not expose
     * their own maxDuration/idleTimeout properties (currently {@link LogPatternTrigger}).
     */
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

    /**
     * Appends query parameters in insertion order, skipping null values. Mirrors
     * AbstractCleverCloudConnection#instancesUrl, kept local to this package since it is only
     * needed here.
     */
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
     * Consumes the Clever Cloud v4 logs SSE endpoint and returns whatever entries were collected.
     * This must never rely on the server closing the connection: a live tail can idle forever and a
     * bounded historical fetch can be served by a proxy that keeps the socket open past "until". So
     * the read runs on a bounded worker thread and a watchdog forcibly closes the underlying
     * HttpClient (unblocking the read with an IOException) as soon as any of the following happens,
     * whichever comes first: limit reached, an event's date is at/after "until" (both signalled by
     * the event callback throwing the private SseStopSignal), the hard maxDuration deadline elapses,
     * or idleTimeout passes with no new event. A real server-side close (the historical, well-behaved
     * case) still short-circuits all of the above. Non-2xx responses are checked manually because
     * HttpClient#sseRequest does not enforce allowed status codes the way #request does; a non-2xx
     * response is expected to complete quickly on its own since the server has nothing left to stream,
     * so it is always observed before the watchdog ever needs to step in.
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
                    // Apache's classic HttpClient quietly drains the rest of the response body before
                    // letting a handler exception propagate, so it can return the connection to the
                    // pool for reuse. On a connection that is still open and slowly trickling data,
                    // that drain would block for as long as the server keeps sending, defeating the
                    // whole point of stopping here. Force-closing first, exactly like the watchdog
                    // does, makes that drain hit an already-closed connection and return immediately.
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
                    // Force-closes the client to unblock the worker thread stuck in sseRequest.
                    // The try-with-resources close on the normal exit path is then a harmless no-op.
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

            // The v4 logs SSE endpoint never closes on its own for a live tail or an idle-open historical
            // fetch, so hitting the client-side limit is the normal, every-run path, not a warning.
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

    /**
     * Thrown from the SSE event callback to unwind out of HttpClient#sseRequest as soon as limit or
     * until is reached, without waiting for the server to close the connection. Caught in fetchLogs
     * and treated as normal completion, not a failure. No stack trace: used purely for control flow.
     */
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
