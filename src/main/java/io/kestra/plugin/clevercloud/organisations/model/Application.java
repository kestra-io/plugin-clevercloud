package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Application {

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Variant {
        private String id;
        private String slug;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instance {
        private String type;
        private String version;
        private Variant variant;
    }

    private String id;
    private String name;
    private String description;
    private String zone;
    private String zoneId;
    private Instance instance;
}
