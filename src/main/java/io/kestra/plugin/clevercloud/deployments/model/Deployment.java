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
}
