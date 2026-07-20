package io.kestra.plugin.clevercloud.addons.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        private String id;
        private String name;
        private String shortDesc;
        private String logoUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        private String id;
        private String name;
        private String slug;
    }

    private String id;
    private String name;
    private String realId;
    private String region;
    private String zoneId;
    private Provider provider;
    private Plan plan;

    /** Epoch milliseconds, as returned by the API (an int64 number, not a string). */
    private Long creationDate;

    private List<String> configKeys;
}
