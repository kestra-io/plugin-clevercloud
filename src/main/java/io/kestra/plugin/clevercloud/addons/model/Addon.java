package io.kestra.plugin.clevercloud.addons.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Addon {

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Provider {
        @Schema(title = "Add-on provider ID")
        private String id;

        @Schema(title = "Add-on provider name")
        private String name;

        @Schema(title = "Add-on provider short description")
        private String shortDesc;

        @Schema(title = "Add-on provider logo URL")
        private String logoUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        @Schema(title = "Plan ID")
        private String id;

        @Schema(title = "Plan name")
        private String name;

        @Schema(title = "Plan slug")
        private String slug;
    }

    @Schema(title = "Add-on ID")
    private String id;

    @Schema(title = "Add-on name")
    private String name;

    @Schema(title = "Add-on real ID", description = "Provider-specific resource identifier.")
    private String realId;

    @Schema(title = "Region")
    private String region;

    @Schema(title = "Zone ID")
    private String zoneId;

    @Schema(title = "Add-on provider")
    private Provider provider;

    @Schema(title = "Add-on plan")
    private Plan plan;

    /** Epoch milliseconds, as returned by the API (an int64 number, not a string). */
    @Schema(title = "Creation timestamp", description = "Epoch milliseconds.")
    private Long creationDate;

    @Schema(title = "Configuration keys exposed by the add-on")
    private List<String> configKeys;
}
