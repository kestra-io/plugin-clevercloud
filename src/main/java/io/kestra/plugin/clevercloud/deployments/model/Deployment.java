package io.kestra.plugin.clevercloud.deployments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single deployment object as returned by the Clever Cloud v2 API.
 * Unknown fields are silently ignored so new API fields do not break deserialization.
 *
 * Field names match the real API exactly: uuid, date (epoch millis string),
 * state (WIP | OK | FAIL | CANCELLED), action (DEPLOY | UNDEPLOY), cause, commit.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deployment {

    /** Deployment identifier, e.g. deployment_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx */
    private String uuid;

    /** Epoch milliseconds as a string, e.g. "1782127329927" */
    private String date;

    /** WIP (in-progress), OK (success), FAIL (error), or CANCELLED */
    private String state;

    /** DEPLOY or UNDEPLOY */
    private String action;

    private String cause;

    /** Git commit SHA. Null for non-Git triggers. */
    private String commit;
}
