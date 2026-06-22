package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of an add-on as returned by GET /v2/organisations/{orgId}/addons.
 *
 * Real API shape includes: id, name, realId, region, provider (with id/name/shortDesc/logoUrl),
 * plan (with id/slug/name), configKeys.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Addon {

    /** Add-on provider info nested inside each add-on. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Provider {
        private String id;
        private String name;
        private String shortDesc;
        private String logoUrl;
    }

    /** Plan selection info. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        private String id;
        private String slug;
        private String name;
    }

    private String id;

    /** Human-readable name assigned to the add-on at creation. */
    private String name;

    /** Internal resource identifier used by the provider. */
    private String realId;

    private String region;
    private Provider provider;
    private Plan plan;
}
