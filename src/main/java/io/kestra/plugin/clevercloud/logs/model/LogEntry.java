package io.kestra.plugin.clevercloud.logs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single application runtime log line, as emitted on the
 * GET /v4/logs/organisations/{ownerId}/applications/{applicationId}/logs SSE stream.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {

    private String id;
    private String applicationId;
    private String commitId;
    private String deploymentId;
    private String instanceId;
    private Instant date;
    private String zone;
    private Integer pid;
    private Integer facility;
    private String severity;
    private Integer priority;
    private String version;
    private String service;
    private String message;
}
