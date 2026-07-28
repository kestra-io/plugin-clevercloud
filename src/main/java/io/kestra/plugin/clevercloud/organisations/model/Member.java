package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Member {

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        @Schema(title = "User ID")
        private String id;

        @Schema(title = "User email")
        private String email;

        @Schema(title = "User display name")
        private String name;

        @Schema(title = "Avatar URL")
        private String avatar;

        @Schema(title = "Preferred multi-factor authentication method")
        private String preferredMFA;
    }

    @Schema(title = "User details")
    private UserInfo member;

    @Schema(title = "Member role in the organisation", description = "One of ADMIN, MANAGER, DEVELOPER, ACCOUNTING, or READ_ONLY.")
    private String role;

    @Schema(title = "Member job title")
    private String job;
}
