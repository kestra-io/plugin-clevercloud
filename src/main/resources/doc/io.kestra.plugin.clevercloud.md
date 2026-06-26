# How to use the Clever Cloud plugin

This plugin integrates Kestra with [Clever Cloud](https://www.clever-cloud.com/), a Platform-as-a-Service provider. It exposes tasks and triggers for managing application deployments via the [Clever Cloud API v2](https://api.clever-cloud.com/v2/).

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

## Deployment states

The Clever Cloud v2 API uses the following `state` values on deployment records:

- `WIP`: deployment is in progress (not terminal).
- `OK`: deployment completed successfully (terminal).
- `FAIL`: deployment errored (terminal).
- `CANCELLED`: deployment was cancelled (terminal).

Each deployment record also has an `action` field: `DEPLOY` for code pushes and `UNDEPLOY` for infrastructure scale-down or moderation events.

## Tasks

### deployments

Tasks for listing, fetching, and waiting on application deployments.

**`io.kestra.plugin.clevercloud.deployments.List`**

Lists the deployment history for an application. Required: `organisationId`, `applicationId`. Optional: `limit` (integer, caps the number of deployments returned, defaults to 50). Outputs: `deployments` (list of deployment objects), `total` (count).

Each deployment object exposes: `uuid`, `state`, `action`, `cause`, `date` (epoch milliseconds string), `commit` (null for non-Git triggers).

**`io.kestra.plugin.clevercloud.deployments.Get`**

Fetches a single deployment by ID. Required: `organisationId`, `applicationId`, `deploymentId`. Outputs: `deploymentId`, `state`, `action`, `cause`, `date`, `commit`.

**`io.kestra.plugin.clevercloud.deployments.WaitForState`**

Polls a deployment until it reaches the configured `targetState`. Throws when the deployment reaches a different terminal state or when `timeout` elapses. Required: `organisationId`, `applicationId`, `deploymentId`, `targetState` (enum: OK, FAIL, CANCELLED, WIP). Optional: `pollInterval` (default PT15S), `timeout` (default PT30M). Outputs: `deploymentId`, `state`.

Use `targetState: OK` to wait for a successful deploy.

## Triggers

**`io.kestra.plugin.clevercloud.deployments.DeploymentTrigger`**

Polls the deployment list for an application at each `interval` and fires when any DEPLOY action deployment matches `targetState` (enum: OK, FAIL, CANCELLED, WIP). UNDEPLOY records are ignored. The number of deployments checked per poll is controlled by `maxDeployments` (default 25). Outputs accessible via `{{ trigger.* }}`: `deploymentId`, `state`, `commit`.

The minimum recommended `interval` is PT30S to avoid rate-limiting the Clever Cloud API.

Note: `organisationId` follows the pattern `orga_<uuid>` for organisation-owned applications and `user_<uuid>` for personal applications.
