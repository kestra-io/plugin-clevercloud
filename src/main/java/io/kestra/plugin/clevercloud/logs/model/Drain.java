package io.kestra.plugin.clevercloud.logs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        private String type;
        private String url;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Execution {
        private String status;
        private String lastError;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Backlog {
        private Long msgRateOut;
        private Long msgBacklog;
    }

    private String id;
    private String applicationId;
    private Recipient recipient;
    private String kind;
    private String updatedAt;
    private String status;
    private String updatedBy;
    private Execution execution;
    private Backlog backlog;
}
