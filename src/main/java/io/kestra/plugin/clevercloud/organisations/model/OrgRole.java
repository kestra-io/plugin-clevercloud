package io.kestra.plugin.clevercloud.organisations.model;

/**
 * Organisation-level roles as defined by the Clever Cloud v2 API.
 * ADMIN has full control. DEVELOPER can deploy. READ_ONLY has view-only access.
 */
public enum OrgRole {
    ADMIN,
    MANAGER,
    DEVELOPER,
    ACCOUNTING,
    READ_ONLY
}
