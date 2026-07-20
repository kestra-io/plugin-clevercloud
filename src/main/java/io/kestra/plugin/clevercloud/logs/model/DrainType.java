package io.kestra.plugin.clevercloud.logs.model;

/**
 * Recipient type for a Clever Cloud log drain ("recipient.type"). No dedicated OVHCLOUD type: use RAW_HTTP or SYSLOG_TCP/SYSLOG_UDP instead.
 */
public enum DrainType {
    RAW_HTTP,
    SYSLOG_TCP,
    SYSLOG_UDP,
    DATADOG,
    ELASTICSEARCH,
    NEWRELIC
}
