package io.kestra.plugin.clevercloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Shared authentication state and signed HTTP execution for all Clever Cloud tasks and triggers.
 * Clever Cloud's API uses OAuth 1.0a (HMAC-SHA1). ScribeJava builds the Authorization header;
 * OkHttp sends the request.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractCleverCloudConnection extends Task {

    public static final String BASE_URL = "https://api.clever-cloud.com/v2/";

    // Shared thread-safe client. OkHttpClient docs recommend a single instance per application.
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    /**
     * Overrides the Clever Cloud API base URL. Used in tests to point at a mock server.
     * Hidden from the UI and docs; should not be set in production flows.
     */
    @Schema(title = "Override the Clever Cloud API base URL", description = "Used in tests to point at a mock server. Do not set in production flows.")
    @PluginProperty(group = "advanced", hidden = true)
    private Property<String> apiBaseUrl;

    protected String baseUrl(RunContext runContext) throws Exception {
        return runContext.render(apiBaseUrl).as(String.class).orElse(BASE_URL);
    }

    @Schema(
        title = "OAuth consumer key",
        description = "Store as a Kestra secret and reference with {{ secret('CC_CONSUMER_KEY') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> consumerKey;

    @Schema(
        title = "OAuth consumer secret",
        description = "Store as a Kestra secret and reference with {{ secret('CC_CONSUMER_SECRET') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> consumerSecret;

    @Schema(
        title = "OAuth access token",
        description = "Store as a Kestra secret and reference with {{ secret('CC_TOKEN') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> token;

    @Schema(
        title = "OAuth access token secret",
        description = "Store as a Kestra secret and reference with {{ secret('CC_TOKEN_SECRET') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> tokenSecret;

    public static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    /**
     * Renders credentials and returns a ready-to-use {@link SignedClient}.
     * Callers use this to build and execute signed requests.
     */
    protected SignedClient signedClient(RunContext runContext) throws Exception {
        var rConsumerKey = runContext.render(consumerKey).as(String.class).orElseThrow();
        var rConsumerSecret = runContext.render(consumerSecret).as(String.class).orElseThrow();
        var rToken = runContext.render(token).as(String.class).orElseThrow();
        var rTokenSecret = runContext.render(tokenSecret).as(String.class).orElseThrow();
        return signedClient(rConsumerKey, rConsumerSecret, rToken, rTokenSecret);
    }

    /**
     * Builds a {@link SignedClient} from already-rendered credential strings.
     * Triggers call this directly because they hold their own credential properties and
     * do not extend this class.
     */
    public static SignedClient signedClient(
        String consumerKey,
        String consumerSecret,
        String token,
        String tokenSecret
    ) {
        var service = new ServiceBuilder(consumerKey)
            .apiSecret(consumerSecret)
            .build(new CleverCloudApi());
        var accessToken = new OAuth1AccessToken(token, tokenSecret);
        return new SignedClient(service, accessToken, HTTP_CLIENT);
    }

    /**
     * Wraps an OAuth 1.0a service + access token so callers can issue signed requests
     * without repeated credential resolution.
     */
    public static class SignedClient {
        private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
        private static final Logger LOG = LoggerFactory.getLogger(SignedClient.class);

        private final OAuth10aService service;
        private final OAuth1AccessToken accessToken;
        private final OkHttpClient httpClient;

        public SignedClient(OAuth10aService service, OAuth1AccessToken accessToken, OkHttpClient httpClient) {
            this.service = service;
            this.accessToken = accessToken;
            this.httpClient = httpClient;
        }

        /**
         * Issues a signed GET request to {@code url} and returns the raw response body string.
         * Throws {@link IOException} on non-2xx responses.
         */
        public String get(String url) throws Exception {
            var oauthRequest = new OAuthRequest(Verb.GET, url);
            service.signRequest(accessToken, oauthRequest);

            var httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", oauthRequest.getHeaders().get("Authorization"))
                .addHeader("Accept", "application/json")
                .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                var body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.debug("Clever Cloud API GET {} returned {}: {}", url, response.code(), body);
                    throw new IOException(
                        "Clever Cloud API error " + response.code() + " on GET " + url
                            + ": check credentials and that the resource exists"
                    );
                }
                return body;
            }
        }

        /**
         * Issues a signed POST request with a JSON body to {@code url}.
         * OAuth 1.0a signature covers only the URL and method (not the JSON body),
         * which matches how ScribeJava handles non-form-encoded POST bodies.
         */
        public String post(String url, String jsonBody) throws Exception {
            var oauthRequest = new OAuthRequest(Verb.POST, url);
            service.signRequest(accessToken, oauthRequest);

            var httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", oauthRequest.getHeaders().get("Authorization"))
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                var body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.debug("Clever Cloud API POST {} returned {}: {}", url, response.code(), body);
                    throw new IOException(
                        "Clever Cloud API error " + response.code() + " on POST " + url
                            + ": check credentials and request body"
                    );
                }
                return body;
            }
        }

        /**
         * Issues a signed DELETE request to {@code url}.
         * Returns the raw response body (may be empty on 204 responses).
         */
        public String delete(String url) throws Exception {
            var oauthRequest = new OAuthRequest(Verb.DELETE, url);
            service.signRequest(accessToken, oauthRequest);

            var httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", oauthRequest.getHeaders().get("Authorization"))
                .addHeader("Accept", "application/json")
                .delete()
                .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                var body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.debug("Clever Cloud API DELETE {} returned {}: {}", url, response.code(), body);
                    throw new IOException(
                        "Clever Cloud API error " + response.code() + " on DELETE " + url
                            + ": check credentials and that the resource exists"
                    );
                }
                return body;
            }
        }
    }
}
