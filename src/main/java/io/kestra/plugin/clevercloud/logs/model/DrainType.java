package io.kestra.plugin.clevercloud.logs.model;

/**
 * Recipient type for a Clever Cloud log drain (POST /v4/drains/.../drains "recipient.type").
 * There is no dedicated OVHCLOUD type on the Clever Cloud API: forward to OVHcloud (or any other
 * generic HTTP or syslog ingestion endpoint) via RAW_HTTP or SYSLOG_TCP/SYSLOG_UDP instead.
 */
public enum DrainType {
    RAW_HTTP,
    SYSLOG_TCP,
    SYSLOG_UDP,
    DATADOG,
    ELASTICSEARCH,
    NEWRELIC
}
