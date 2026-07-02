package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

// Personal accounts (user_xxx) return 403 on GET /organisations/{id}, hence the /self routing in Get.
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Organisation {

    private String id;
    private String name;
    private String description;
    private String city;
    private String country;
    private String avatar;
    private String email;
    private boolean cleverEnterprise;
}
