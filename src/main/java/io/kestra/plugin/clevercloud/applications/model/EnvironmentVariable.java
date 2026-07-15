package io.kestra.plugin.clevercloud.applications.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentVariable {
    private String name;
    private String value;
}
