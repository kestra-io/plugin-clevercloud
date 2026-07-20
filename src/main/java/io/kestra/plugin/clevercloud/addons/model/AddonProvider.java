package io.kestra.plugin.clevercloud.addons.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Entry of the public add-on providers catalog, used to resolve a plan slug to its plan_... id. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddonProvider {

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        private String id;
        private String name;
        private String slug;
        private Double price;
    }

    private String id;
    private List<Plan> plans;
}
