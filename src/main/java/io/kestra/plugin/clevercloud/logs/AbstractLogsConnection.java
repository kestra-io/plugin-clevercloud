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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @PluginProperty(group = "connection")
    private Property<String> organisationId;

    @NotNull
    @Schema(title = "Application ID")
    @PluginProperty(group = "connection")
    private Property<String> applicationId;

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
     * Consumes the Clever Cloud v4 logs SSE endpoint to completion and returns the collected
     * entries. The endpoint is fundamentally SSE-based even for a bounded historical window: the
     * server closes the connection once the requested "until" timestamp is reached, which is what
     * makes a deterministic, non-hanging call possible here. Non-2xx responses are checked manually
     * because HttpClient#sseRequest does not enforce allowed status codes the way #request does.
     */
    protected static List<LogEntry> fetchLogs(RunContext runContext, HttpConfiguration options, String url, String apiToken) throws Exception {
        var logger = runContext.logger();
        var entries = new ArrayList<LogEntry>();

        try (var client = new HttpClient(runContext, options)) {
            var request = HttpRequest.builder()
                .uri(URI.create(url))
                .method("GET")
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Accept", "text/event-stream")
                .build();

            var response = client.sseRequest(request, String.class, event -> {
                var data = event.data();
                if (data == null || data.isBlank()) {
                    return;
                }
                try {
                    entries.add(MAPPER.readValue(data, LogEntry.class));
                } catch (Exception e) {
                    logger.debug("Skipping unparsable SSE log event: {}", e.getMessage());
                }
            });

            var status = response.getStatus().getCode();
            if (status >= 400) {
                logger.debug("Clever Cloud logs API GET {} returned {}", url, status);
                throw new HttpClientResponseException(
                    "Clever Cloud API error " + status + " on GET " + url + ": check apiToken and that the resource exists",
                    response
                );
            }
        }

        return entries;
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
