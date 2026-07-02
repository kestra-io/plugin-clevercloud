package io.kestra.plugin.clevercloud.organisations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        private String id;
        private String email;
        private String name;
        private String avatar;
        private String preferredMFA;
    }

    private UserInfo member;
    private String role;
    private String job;
}
