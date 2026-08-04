package io.kestra.plugin.clevercloud.logs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Drain {

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Recipient {
        @Schema(title = "Recipient type")
        private String type;

        @Schema(title = "Recipient URL")
        private String url;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        @Schema(title = "Status change timestamp")
        private String date;

        @Schema(title = "Drain status", description = "One of CREATED, ENABLED, ENABLING, DISABLING, DISABLED, or DELETED.")
        @JsonProperty("status")
        private String state;

        @Schema(title = "ID of the author of the last status change")
        private String authorId;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Execution {
        @Schema(title = "Execution status")
        private String status;

        @Schema(title = "Last execution error")
        private String lastError;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Backlog {
        @Schema(title = "Outbound message rate")
        private Long msgRateOut;

        @Schema(title = "Backlogged message count")
        private Long msgBacklog;
    }

    @Schema(title = "Drain ID")
    private String id;

    @Schema(title = "Application ID")
    private String applicationId;

    @Schema(title = "Drain recipient")
    private Recipient recipient;

    @Schema(title = "Drain kind", description = "One of LOG, ACCESSLOG, or AUDITLOG.")
    private String kind;

    @Schema(title = "Last update timestamp")
    private String updatedAt;

    @Schema(title = "Drain status")
    private Status status;

    @Schema(title = "ID of the user who last updated the drain")
    private String updatedBy;

    @Schema(title = "Execution details")
    private Execution execution;

    @Schema(title = "Backlog details")
    private Backlog backlog;
}
