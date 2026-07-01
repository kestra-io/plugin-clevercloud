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
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractCleverCloudConnection extends Task {

    public static final String DEFAULT_BASE_URL = "https://api-bridge.clever-cloud.com/v2";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

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
            return "organisations/" + organisationId;
        }
        return "self";
    }

    /**
     * Joins a base URL and a path segment with exactly one slash, so a trailing slash on the
     * base (e.g. a misconfigured baseUrl override) never produces a double slash in the request URI.
     */
    public static String join(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
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

    protected HttpRequest.HttpRequestBuilder buildDeleteRequest(String url) {
        return HttpRequest.builder()
            .uri(URI.create(url))
            .method("DELETE");
    }
}
