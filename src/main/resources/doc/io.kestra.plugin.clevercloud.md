# How to use the Clever Cloud plugin

This plugin integrates Kestra with [Clever Cloud](https://www.clever-cloud.com/), a Platform-as-a-Service provider. It exposes tasks and triggers for managing application deployments via the [Clever Cloud API v2](https://api-bridge.clever-cloud.com/v2/).

## Authentication

All tasks and triggers authenticate with a single Bearer token sent as `Authorization: Bearer <token>`.

| Property | Required | Description | Secret |
|---|---|---|---|
| `apiToken` | yes | Clever Cloud API token | yes |

Store `apiToken` as a [Kestra secret](https://kestra.io/docs/concepts/secret) and reference it with `{{ secret('CC_API_TOKEN') }}`. You can set it once using [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults) to avoid repeating it in every task.

Generate an API token from the Clever Cloud console under **Profile > API tokens**.

## Personal vs organisation accounts

Most tasks accept an optional `organisationId`. When omitted, the plugin targets the personal account via the `/self` API endpoint instead of `/organisations/{id}`. This means the same tasks work for both personal accounts and organisation-owned resources:

- With `organisationId`: calls `organisations/{id}/applications/{appId}/deployments`
- Without `organisationId`: calls `self/applications/{appId}/deployments`

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

Lists the deployment history for an application. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted), `limit` (integer, caps the number of deployments returned, defaults to 50). Outputs: `deployments` (list of deployment objects), `total` (count).

Each deployment object exposes: `uuid`, `state`, `action`, `cause`, `date` (epoch milliseconds string), `commit` (null for non-Git triggers).

**`io.kestra.plugin.clevercloud.deployments.Get`**

Fetches a single deployment by ID. Required: `applicationId`, `deploymentId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `deploymentId`, `state`, `action`, `cause`, `date`, `commit`.

**`io.kestra.plugin.clevercloud.deployments.WaitForState`**

Polls a deployment until it reaches the configured `targetState`. By default it never fails: if the deployment reaches a different terminal state or `timeout` elapses, it logs a warning and returns the last observed state with `reachedTarget` set to false. Set `failOnUnreached` to true to throw instead. Required: `applicationId`, `deploymentId`. Optional: `targetState` (enum: OK, FAIL, CANCELLED, WIP, defaults to OK), `failOnUnreached` (default false), `organisationId` (defaults to /self when omitted), `pollInterval` (default PT15S), `timeout` (default PT30M). Outputs: `deploymentId`, `state`, `reachedTarget`.

## Triggers

**`io.kestra.plugin.clevercloud.deployments.Trigger`**

Polls the deployment list for an application at each `interval` and fires when any DEPLOY action deployment matches `targetState` (enum: OK, FAIL, CANCELLED, WIP). UNDEPLOY records are ignored. The number of deployments checked per poll is controlled by `maxDeployments` (default 25). Optional: `organisationId` (defaults to /self when omitted). Outputs accessible via `{{ trigger.* }}`: `deploymentId`, `state`, `commit`.

The minimum recommended `interval` is PT30S to avoid rate-limiting the Clever Cloud API.
