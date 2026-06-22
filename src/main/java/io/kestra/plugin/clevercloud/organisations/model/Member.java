package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single entry from GET /v2/organisations/{orgId}/members.
 *
 * Real API shape: {member: {id, email, name, avatar, preferredMFA}, role, job}
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Member {

    /** User info sub-object embedded in each member entry. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        private String id;
        private String email;
        private String name;
        private String avatar;
        private String preferredMFA;
    }

    private UserInfo member;

    /** Organisation-level role: ADMIN, MANAGER, DEVELOPER, ACCOUNTING, or READ_ONLY (from CC docs). */
    private String role;

    /** Job title as reported by the member, e.g. "owner". May be null. */
    private String job;
}
