package io.kestra.plugin.clevercloud.deployments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deployment {

    private String uuid;

    private String date;

    private String state;

    private String action;

    private String cause;

    private String commit;
}
