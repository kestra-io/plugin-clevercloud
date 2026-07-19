package io.kestra.plugin.clevercloud.logs.model;

/**
 * Which log stream a drain forwards ("kind" field of a Clever Cloud log drain).
 */
public enum DrainKind {
    LOG,
    ACCESSLOG,
    AUDITLOG
}
