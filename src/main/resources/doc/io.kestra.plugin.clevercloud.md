# How to use the Clever Cloud plugin

This plugin integrates Kestra with [Clever Cloud](https://www.clever-cloud.com/), a Platform-as-a-Service provider. It exposes tasks and triggers for managing application deployments via the [Clever Cloud API v4](https://api.clever-cloud.com/v4/).

## Authentication

All tasks and triggers require four OAuth 1.0a credentials. These map to the following properties on every component:

| Property | Description | Secret |
|---|---|---|
| `consumerKey` | OAuth consumer key | yes |
| `consumerSecret` | OAuth consumer secret | yes |
| `token` | OAuth access token | yes |
| `tokenSecret` | OAuth access token secret | yes |

Store all four values as [Kestra secrets](https://kestra.io/docs/concepts/secret) and reference them with `{{ secret('NAME') }}`. You can set them once using [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults) to avoid repeating them in every task.

You can generate an access token from the Clever Cloud console under **Profile > API tokens** or via the `clever` CLI (`clever login`).

## Tasks

### deployments

Tasks for listing, fetching, and waiting on application deployments.

**`io.kestra.plugin.clevercloud.deployments.List`**

Lists the deployment history for an application. Required: `organisationId`, `applicationId`. Optional: `limit` (integer, caps the number of deployments returned). Outputs: `deployments` (list of deployment objects), `total` (count).

**`io.kestra.plugin.clevercloud.deployments.Get`**

Fetches a single deployment by ID. Required: `organisationId`, `applicationId`, `deploymentId`. Outputs: `deploymentId`, `state`, `commit`, `startDate`, `endDate`.

**`io.kestra.plugin.clevercloud.deployments.WaitForState`**

Polls a deployment until it reaches the configured `targetState`. Throws when the deployment reaches a different terminal state or when `timeout` elapses. Required: `organisationId`, `applicationId`, `deploymentId`, `targetState`. Optional: `pollInterval` (default PT15S), `timeout` (default PT30M). Outputs: `deploymentId`, `state`.

Common target states: `DEPLOY_OK`, `DEPLOY_FAILED`, `WIP`.

## Triggers

**`io.kestra.plugin.clevercloud.deployments.DeploymentTrigger`**

Polls the deployment list for an application at each `interval` and fires when any deployment matches `targetState`. Only the most recent ten deployments are checked per poll. Outputs accessible via `{{ trigger.* }}`: `deploymentId`, `state`, `commit`.

The minimum recommended `interval` is PT30S to avoid rate-limiting the Clever Cloud API.

Note: `organisationId` follows the pattern `orga_<uuid>` for organisation-owned applications and `user_<uuid>` for personal applications.
