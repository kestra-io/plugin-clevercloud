package io.kestra.plugin.clevercloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractCleverCloudConnection extends Task {

    public static final String DEFAULT_BASE_URL = "https://api-bridge.clever-cloud.com/v2";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String ORGANISATIONS_SEGMENT = "organisations";
    private static final String SELF_SEGMENT = "self";
    private static final String MEMBERS_SEGMENT = "members";

    public static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @NotNull
    @Schema(title = "API token", description = "Bearer token for the Clever Cloud API. Store as a Kestra secret and reference with {{ secret('CC_API_TOKEN') }}.")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> apiToken;

    @Schema(title = "HTTP client options", description = "Optional HttpConfiguration applied to every Clever Cloud API call, including timeouts and proxy settings.")
    @PluginProperty(group = "advanced")
    HttpConfiguration options;

    protected String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    public static String resourceBase(String organisationId) {
        if (organisationId != null && !organisationId.isBlank()) {
            return ORGANISATIONS_SEGMENT + "/" + encodeSegment(organisationId);
        }
        return SELF_SEGMENT;
    }

    /**
     * Encodes a single dynamic path segment so ids containing reserved URL characters
     * (e.g. a slash) cannot alter the target path of the request.
     */
    protected static String encodeSegment(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * Joins a base URL and a path segment with exactly one slash, so a trailing slash on the
     * base (e.g. a misconfigured baseUrl override) never produces a double slash in the request URI.
     */
    public static String join(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    /**
     * Builds a resource URL under the organisation or personal account base, e.g. .../organisations/{id}/applications
     * or .../self/addons. Used by endpoints that support both the organisation and personal account scope.
     */
    public static String resourceUrl(String baseUrl, String organisationId, String path) {
        return join(baseUrl, resourceBase(organisationId)) + "/" + path;
    }

    /**
     * Builds the members URL under an organisation. Unlike other resources, /self/members does not exist
     * on the Clever Cloud API, so this always targets /organisations/{id}/members.
     */
    public static String membersUrl(String baseUrl, String organisationId) {
        return join(baseUrl, ORGANISATIONS_SEGMENT + "/" + encodeSegment(organisationId) + "/" + MEMBERS_SEGMENT);
    }

    /**
     * Builds the .../applications/{appId}/instances URL shared by Redeploy and Restart, with optional
     * query parameters appended in insertion order (e.g. commit, useCache).
     */
    public static String instancesUrl(String baseUrl, String organisationId, String applicationId, Map<String, String> queryParams) {
        var url = new StringBuilder(resourceUrl(baseUrl, organisationId, "applications/" + encodeSegment(applicationId) + "/instances"));
        var separator = "?";
        for (var entry : queryParams.entrySet()) {
            url.append(separator).append(entry.getKey()).append("=").append(encodeSegment(entry.getValue()));
            separator = "&";
        }
        return url.toString();
    }

    public String makeCall(RunContext runContext, HttpRequest.HttpRequestBuilder requestBuilder) throws Exception {
        try {
            var rToken = renderApiToken(runContext);
            var body = makeCall(runContext, options, requestBuilder, rToken, String.class);
            return body != null ? body : "";
        } catch (IllegalVariableEvaluationException e) {
            runContext.logger().error("Failed to render apiToken: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Shared HTTP call logic: builds the client, adds the Bearer auth header, executes the request,
     * and on a non-2xx response wraps the failure into a body-free {@link HttpClientResponseException}
     * so secrets or sensitive API error payloads are never leaked into task/trigger logs or outputs.
     */
    public static <T> T makeCall(
        RunContext runContext,
        HttpConfiguration options,
        HttpRequest.HttpRequestBuilder requestBuilder,
        String apiToken,
        Class<T> responseType
    ) throws Exception {
        var logger = runContext.logger();
        try (var client = new HttpClient(runContext, options)) {
            requestBuilder
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", JSON_CONTENT_TYPE);
            var response = client.request(requestBuilder.build(), responseType);
            return response.getBody();
        } catch (HttpClientResponseException e) {
            var status = e.getResponse() != null ? e.getResponse().getStatus().getCode() : -1;
            var method = "request";
            var uri = "";
            if (e.getResponse() != null && e.getResponse().getRequest() != null) {
                method = e.getResponse().getRequest().getMethod();
                uri = String.valueOf(e.getResponse().getRequest().getUri());
            }
            logger.debug("Clever Cloud API {} {} returned {}", method, uri, status);
            throw new HttpClientResponseException(
                "Clever Cloud API error " + status + " on " + method + " " + uri
                    + ": check apiToken and that the resource exists",
                e.getResponse(),
                e
            );
        }
    }

    public String renderApiToken(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(apiToken).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
    }

    protected HttpRequest.HttpRequestBuilder buildGetRequest(String url) {
        return HttpRequest.builder()
            .uri(URI.create(url))
            .method("GET");
    }

    protected HttpRequest.HttpRequestBuilder buildPostRequest(String url, Object body) throws Exception {
        var jsonBody = MAPPER.writeValueAsString(body);
        return HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.StringRequestBody.builder().content(jsonBody).build());
    }

    protected HttpRequest.HttpRequestBuilder buildPutRequest(String url, Object body) throws Exception {
        var jsonBody = MAPPER.writeValueAsString(body);
        return HttpRequest.builder()
            .uri(URI.create(url))
            .method("PUT")
            .body(HttpRequest.StringRequestBody.builder().content(jsonBody).build());
    }

    protected HttpRequest.HttpRequestBuilder buildDeleteRequest(String url) {
        return HttpRequest.builder()
            .uri(URI.create(url))
            .method("DELETE");
    }

    /**
     * Shared FetchType handling for list tasks: FETCH keeps all items, FETCH_ONE keeps the first,
     * STORE writes the items to an ion file in internal storage, NONE keeps only the count.
     */
    protected static <T> FetchResult<T> fetchOutput(RunContext runContext, Property<FetchType> fetchType, List<T> items) throws Exception {
        var total = items.size();
        return switch (runContext.render(fetchType).as(FetchType.class).orElseThrow()) {
            case FETCH -> new FetchResult<>(items, null, null, total);
            case FETCH_ONE -> new FetchResult<>(null, items.isEmpty() ? null : items.getFirst(), null, total);
            case STORE -> new FetchResult<>(null, null, store(runContext, items), total);
            case NONE -> new FetchResult<>(null, null, null, total);
        };
    }

    private static <T> URI store(RunContext runContext, List<T> items) throws Exception {
        var tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            FileSerde.writeAll(writer, Flux.fromIterable(items)).block();
        }

        return runContext.storage().putFile(tempFile);
    }

    public record FetchResult<T>(List<T> items, T first, URI uri, int total) {
    }
}
