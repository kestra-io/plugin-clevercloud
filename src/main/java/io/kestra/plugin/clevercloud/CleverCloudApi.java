package io.kestra.plugin.clevercloud;

import com.github.scribejava.core.builder.api.DefaultApi10a;

/**
 * ScribeJava API descriptor for Clever Cloud OAuth 1.0a endpoints.
 * Only used to produce HMAC-SHA1 Authorization headers; we never go through the full OAuth dance here.
 */
public class CleverCloudApi extends DefaultApi10a {

    @Override
    public String getRequestTokenEndpoint() {
        return "https://api.clever-cloud.com/v1/oauth/request_token";
    }

    @Override
    public String getAccessTokenEndpoint() {
        return "https://api.clever-cloud.com/v1/oauth/access_token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return "https://api.clever-cloud.com/v1/oauth/authorize";
    }
}
