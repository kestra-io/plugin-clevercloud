package io.kestra.plugin.clevercloud.logs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(title = "Log line ID")
    private String id;

    @Schema(title = "Application ID")
    private String applicationId;

    @Schema(title = "Deployed commit SHA")
    private String commitId;

    @Schema(title = "Deployment ID")
    private String deploymentId;

    @Schema(title = "Instance ID")
    private String instanceId;

    @Schema(title = "Log timestamp")
    private Instant date;

    @Schema(title = "Zone")
    private String zone;

    @Schema(title = "Process ID")
    private Integer pid;

    @Schema(title = "Syslog facility code")
    private Integer facility;

    @Schema(title = "Log severity")
    private String severity;

    @Schema(title = "Syslog priority value")
    private Integer priority;

    @Schema(title = "Application version")
    private String version;

    @Schema(title = "Service name")
    private String service;

    @Schema(title = "Log message")
    private String message;
}
