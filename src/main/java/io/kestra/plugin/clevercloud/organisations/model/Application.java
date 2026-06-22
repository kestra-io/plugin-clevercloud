package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of an application as returned by GET /v2/organisations/{orgId}/applications.
 *
 * The API returns a large object; we capture the fields most useful to orchestration workflows.
 * Real API shape includes: id, name, description, zone, zoneId, instance (with type/version/variant).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Application {

    /** Variant info nested inside instance. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Variant {
        private String id;
        private String slug;
        private String name;
    }

    /** Instance configuration nested inside the application object. */
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
