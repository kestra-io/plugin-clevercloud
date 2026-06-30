# How to use the Clever Cloud plugin

This plugin integrates Kestra with [Clever Cloud](https://www.clever-cloud.com/), a Platform-as-a-Service provider. It exposes tasks and triggers for managing application deployments and organisations via the [Clever Cloud API v2](https://api-bridge.clever-cloud.com/v2/).

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

Tasks that require an organisation (ListMembers, AddMember, RemoveMember) will throw a clear error when `organisationId` is omitted, because the `/self/members` endpoint does not exist on the Clever Cloud API.

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

Lists the deployment history for an application. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted), `limit` (integer, caps the number of deployments returned, defaults to 50), `fetchType` (enum: FETCH, FETCH_ONE, STORE, NONE, defaults to FETCH). Outputs: `total` (count), plus `deployments` (FETCH), `deployment` (FETCH_ONE), or `uri` to an ion file in internal storage (STORE).

Each deployment object exposes: `uuid`, `state`, `action`, `cause`, `date` (epoch milliseconds string), `commit` (null for non-Git triggers).

**`io.kestra.plugin.clevercloud.deployments.Get`**

Fetches a single deployment by ID. Required: `applicationId`, `deploymentId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `deploymentId`, `state`, `action`, `cause`, `date`, `commit`.

**`io.kestra.plugin.clevercloud.deployments.WaitForState`**

Polls a deployment until it reaches the configured `targetState`. By default it never fails: if the deployment reaches a different terminal state or `timeout` elapses, it logs a warning and returns the last observed state with `reachedTarget` set to false. Set `failOnUnreached` to true to throw instead. Required: `applicationId`, `deploymentId`. Optional: `targetState` (enum: OK, FAIL, CANCELLED, WIP, defaults to OK), `failOnUnreached` (default false), `organisationId` (defaults to /self when omitted), `pollInterval` (default PT15S), `timeout` (default PT30M). Outputs: `deploymentId`, `state`, `reachedTarget`.

### organisations

Tasks for managing organisations, members, applications, and add-ons.

**`io.kestra.plugin.clevercloud.organisations.Get`**

Fetches organisation or personal account details. Optional: `organisationId` (defaults to /self when omitted, returning personal account info). Outputs: `id`, `name`, `description`, `city`, `country`, `avatar`, `email`, `cleverEnterprise`.

**`io.kestra.plugin.clevercloud.organisations.ListMembers`**

Lists all members of an organisation. Requires `organisationId` (the /self/members endpoint does not exist). Optional: `fetchType` (enum: FETCH, FETCH_ONE, STORE, NONE, defaults to FETCH). Outputs: `total`, plus `members` (FETCH), `member` (FETCH_ONE), or `uri` to an ion file in internal storage (STORE). Each member entry contains a `member` sub-object (`id`, `email`, `name`, `avatar`, `preferredMFA`) plus `role` and `job`.

**`io.kestra.plugin.clevercloud.organisations.AddMember`**

Invites a user to the organisation by email and assigns a role. Requires `organisationId`, `email`, `role`. Valid roles: `ADMIN`, `MANAGER`, `DEVELOPER`, `ACCOUNTING`, `READ_ONLY`. Returns no output.

**`io.kestra.plugin.clevercloud.organisations.RemoveMember`**

Removes a user from the organisation. Requires `organisationId`, `userId`. Obtain the user ID from `ListMembers` output (`members[i].member.id`). Returns no output.

**`io.kestra.plugin.clevercloud.organisations.ListApplications`**

Lists all applications in the organisation or personal account. Optional: `organisationId` (defaults to /self when omitted), `fetchType` (enum: FETCH, FETCH_ONE, STORE, NONE, defaults to FETCH). Outputs: `total`, plus `applications` (FETCH), `application` (FETCH_ONE), or `uri` to an ion file in internal storage (STORE). Each application entry contains: `id`, `name`, `description`, `zone`, `zoneId`, `instance` (with `type`, `version`, `variant.slug`).

**`io.kestra.plugin.clevercloud.organisations.ListAddons`**

Lists all add-ons provisioned in the organisation or personal account. Optional: `organisationId` (defaults to /self when omitted), `fetchType` (enum: FETCH, FETCH_ONE, STORE, NONE, defaults to FETCH). Outputs: `total`, plus `addons` (FETCH), `addon` (FETCH_ONE), or `uri` to an ion file in internal storage (STORE). Each add-on entry contains: `id`, `name`, `realId`, `region`, `provider` (with `id`, `name`, `shortDesc`), `plan` (with `id`, `slug`, `name`).

## Triggers

**`io.kestra.plugin.clevercloud.deployments.Trigger`**

Polls the deployment list for an application at each `interval` and fires when any DEPLOY action deployment matches `targetState` (enum: OK, FAIL, CANCELLED, WIP). UNDEPLOY records are ignored. The number of deployments checked per poll is controlled by `maxDeployments` (default 25). Optional: `organisationId` (defaults to /self when omitted). Outputs accessible via `{{ trigger.* }}`: `deploymentId`, `state`, `commit`.

The minimum recommended `interval` is PT30S to avoid rate-limiting the Clever Cloud API.

**`io.kestra.plugin.clevercloud.organisations.MemberChangeTrigger`**

Polls the member list of an organisation at each `interval` and fires when the member set changes. Uses KV store to persist the member ID set between evaluations (the members endpoint has no timestamps). The first evaluation always establishes a baseline and never fires. Subsequent polls fire only when a change is detected. Requires `organisationId`, `event` (MEMBER_ADDED, MEMBER_REMOVED, or MEMBER_CHANGED). Outputs via `{{ trigger.* }}`: `organisationId`, `addedMembers` (list of user IDs), `removedMembers` (list of user IDs).
