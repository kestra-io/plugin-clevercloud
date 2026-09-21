package io.kestra.plugin.clevercloud.deployments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deployment {

    @Schema(title = "Deployment UUID")
    private String uuid;

    @Schema(title = "Deployment timestamp")
    @JsonDeserialize(using = Deployment.EpochMillisStringDeserializer.class)
    @tools.jackson.databind.annotation.JsonDeserialize(using = Deployment.Jackson3EpochMillisStringDeserializer.class)
    private Instant date;

    @Schema(title = "Deployment state", description = "One of WIP, OK, FAIL, or CANCELLED.")
    private String state;

    @Schema(title = "Deployment action", description = "DEPLOY or UNDEPLOY.")
    private String action;

    @Schema(title = "What triggered the deployment", description = "For example Git, API, or Console.")
    private String cause;

    @Schema(title = "Git commit SHA", description = "Null for non-Git deployments.")
    private String commit;

    /**
     * The Clever Cloud API returns "date" as an epoch-milliseconds STRING (e.g. "1782127329927"),
     * not a number or ISO-8601 string, so a plain Instant deserializer would fail.
     * <p>
     * See {@link Jackson3EpochMillisStringDeserializer} for the Jackson 3 twin, required so this
     * deserializer is also applied at the Jackson 3 (HTTP/Micronaut) boundary, since
     * {@code @JsonDeserialize(using = ...)} is not bridged by Micronaut's Jackson2AnnotationSupport.
     */
    static class EpochMillisStringDeserializer extends StdDeserializer<Instant> {

        EpochMillisStringDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            var text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Instant.ofEpochMilli(Long.parseLong(text));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Jackson 3 twin of {@link EpochMillisStringDeserializer}, required since Micronaut 5 / core expose
     * Jackson 3 at the HTTP boundary and {@code @JsonDeserialize(using = ...)} is not bridged by
     * Micronaut's Jackson2AnnotationSupport. Both deserializers must be stacked on the same field.
     */
    static class Jackson3EpochMillisStringDeserializer extends tools.jackson.databind.deser.std.StdDeserializer<Instant> {

        Jackson3EpochMillisStringDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(tools.jackson.core.JsonParser p, tools.jackson.databind.DeserializationContext ctxt) {
            var text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Instant.ofEpochMilli(Long.parseLong(text));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
