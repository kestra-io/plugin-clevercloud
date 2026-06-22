package io.kestra.plugin.clevercloud.deployments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single deployment object as returned by the Clever Cloud API.
 * Unknown fields are silently ignored so new API fields do not break deserialization.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deployment {

    @JsonProperty("uuid")
    private String id;

    @JsonProperty("state")
    private String state;

    @JsonProperty("commit")
    private String commit;

    @JsonProperty("date")
    private String startDate;

    @JsonProperty("endDate")
    private String endDate;

    @JsonProperty("action")
    private String action;

    @JsonProperty("cause")
    private String cause;
}
