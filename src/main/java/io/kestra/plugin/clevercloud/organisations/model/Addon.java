package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        private String slug;
        private String name;
    }

    private String id;
    private String name;
    private String realId;
    private String region;
    private Provider provider;
    private Plan plan;
}
