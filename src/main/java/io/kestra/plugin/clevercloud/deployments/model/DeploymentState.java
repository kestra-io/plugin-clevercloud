package io.kestra.plugin.clevercloud.deployments.model;

public enum DeploymentState {
    WIP,
    OK,
    FAIL,
    CANCELLED;

    public boolean isTerminal() {
        return this != WIP;
    }
}
