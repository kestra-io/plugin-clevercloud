package io.kestra.plugin.clevercloud.applications.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(title = "Instance variant ID")
        private String id;

        @Schema(title = "Instance variant slug")
        private String slug;

        @Schema(title = "Instance variant name")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Flavor {
        @Schema(title = "Flavor name")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instance {
        @Schema(title = "Instance type")
        private String type;

        @Schema(title = "Instance version")
        private String version;

        @Schema(title = "Instance variant")
        private Variant variant;

        @Schema(title = "Minimum instance count")
        private Integer minInstances;

        @Schema(title = "Maximum instance count")
        private Integer maxInstances;

        @Schema(title = "Minimum flavor")
        private Flavor minFlavor;

        @Schema(title = "Maximum flavor")
        private Flavor maxFlavor;
    }

    @Schema(title = "Application ID")
    private String id;

    @Schema(title = "Application name")
    private String name;

    @Schema(title = "Application description")
    private String description;

    @Schema(title = "Deployment zone")
    private String zone;

    @Schema(title = "Deployment zone ID")
    private String zoneId;

    @Schema(title = "Instance configuration")
    private Instance instance;

    @Schema(title = "Application state")
    private String state;

    @Schema(title = "Git deployment URL")
    private String deployUrl;

    /** Epoch milliseconds, as returned by the API (an int64 number, not a string). */
    @Schema(title = "Creation timestamp", description = "Epoch milliseconds.")
    private Long creationDate;
}
