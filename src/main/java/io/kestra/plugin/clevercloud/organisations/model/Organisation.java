package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Organisation object as returned by GET /v2/organisations/{orgId}.
 * Only available for orga_xxx identifiers. Personal accounts (user_xxx) return 403 on this endpoint.
 *
 * Field names match the real API: id, name, description, zip, city, country, avatar, email, VAT, billingEmail, cleverEnterprise.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Organisation {

    private String id;
    private String name;
    private String description;
    private String zip;
    private String city;
    private String country;
    private String avatar;
    private String email;
    private String VAT;
    private String billingEmail;
    private boolean cleverEnterprise;
}
