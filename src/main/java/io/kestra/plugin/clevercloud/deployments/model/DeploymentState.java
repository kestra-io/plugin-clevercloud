package io.kestra.plugin.clevercloud.deployments.model;

/**
 * Terminal and in-progress states returned by the Clever Cloud v2 deployments API.
 * WIP is the only non-terminal state. OK, FAIL, and CANCELLED are all terminal.
 */
public enum DeploymentState {
    WIP,
    OK,
    FAIL,
    CANCELLED;

    public boolean isTerminal() {
        return this != WIP;
    }
}
