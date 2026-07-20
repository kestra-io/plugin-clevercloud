# How to use the Clever Cloud plugin

This plugin integrates Kestra with [Clever Cloud](https://www.clever-cloud.com/), a Platform-as-a-Service provider. It exposes tasks and triggers for managing applications, application deployments, organisations, and add-ons via the [Clever Cloud API v2](https://api-bridge.clever-cloud.com/v2/).

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

### applications

Tasks for managing applications: list, fetch, configure, scale, deploy, and delete.

**`io.kestra.plugin.clevercloud.applications.List`**

Lists all applications in the organisation or personal account. Optional: `organisationId` (defaults to /self when omitted), `fetchType` (enum: FETCH, FETCH_ONE, STORE, NONE, defaults to FETCH). Outputs: `total`, plus `applications` (FETCH), `application` (FETCH_ONE), or `uri` to an ion file in internal storage (STORE). Each application entry contains: `id`, `name`, `description`, `zone`, `zoneId`, `state`, `deployUrl`, `creationDate`, `instance` (with `type`, `version`, `variant`, `minInstances`, `maxInstances`, `minFlavor`, `maxFlavor`).

**`io.kestra.plugin.clevercloud.applications.Get`**

Fetches a single application by ID. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `id`, `name`, `description`, `zone`, `state`, `deployUrl`, `instanceType`, `instanceVersion`, `minInstances`, `maxInstances`, `minFlavor`, `maxFlavor`.

**`io.kestra.plugin.clevercloud.applications.GetEnv`**

Fetches the application's environment variables. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `variables` (map of name to value), `total`. GetEnv returns variable values in plain text: avoid logging or persisting its output if any variable holds a credential.

**`io.kestra.plugin.clevercloud.applications.SetEnv`**

Creates or updates environment variables one at a time via `PUT .../env/{envName}`. This is not atomic: a failure partway through the list leaves the earlier variables already applied. Required: `applicationId`, `vars` (map of name to value, at least one entry). Optional: `organisationId` (defaults to /self when omitted). Outputs: `updatedCount`.

**`io.kestra.plugin.clevercloud.applications.Create`**

Creates a new application. Required: `name`, `instanceType`, `instanceVersion` (the Clever Cloud API rejects creation without a valid instance type and version; valid values come from GET /v2/products/instances). Optional: `organisationId` (defaults to /self when omitted), `applicationDescription`, `zone`, `minInstances`, `maxInstances`, `minFlavor`, `maxFlavor`, `deploy` (deployment method, "git" or "ftp", defaults to "git", required by the Clever Cloud API on creation and always sent). Outputs: `id`, `name`, `zone`, `deployUrl`, `instanceType`, `instanceVersion`.

**`io.kestra.plugin.clevercloud.applications.Scale`**

Updates instance count and flavor bounds via `PUT .../applications/{appId}`. Reads the current application first (`GET .../applications/{appId}`), then re-sends its full definition with only your scaling fields overlaid on top, so unset fields such as name or zone are never cleared. Required: `applicationId`, and at least one of `minInstances`, `maxInstances`, `minFlavor`, `maxFlavor`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `minInstances`, `maxInstances`, `minFlavor`, `maxFlavor`.

**`io.kestra.plugin.clevercloud.applications.Redeploy`**

Triggers a new deployment via `POST .../instances`. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted), `commit` (redeploys the last pushed commit when omitted), `useCache` (defaults to true on the API side). Returns no output; track the resulting deployment with `deployments.List`, `deployments.Get`, or `deployments.WaitForState`.

**`io.kestra.plugin.clevercloud.applications.Restart`**

Restarts the application's instances on the currently deployed commit. Calls the same endpoint as Redeploy but never sends a `commit` parameter. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted), `useCache`. Returns no output.

**`io.kestra.plugin.clevercloud.applications.Stop`**

Stops all running instances via `DELETE .../instances`, without deleting the application. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `message`.

**`io.kestra.plugin.clevercloud.applications.Delete`**

Permanently deletes the application via `DELETE .../applications/{appId}`. Does not delete linked add-ons. Required: `applicationId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `message`.

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

`io.kestra.plugin.clevercloud.organisations.ListApplications` was removed and is now a deprecated alias of `applications.List`: existing flows using the old type keep working, new flows should use `applications.List` directly.

`io.kestra.plugin.clevercloud.organisations.ListAddons` was removed and is now a deprecated alias of `addons.List`: existing flows using the old type keep working, new flows should use `addons.List` directly.

### addons

Tasks for provisioning, inspecting, and linking add-ons (databases, caches, and other managed services).

**`io.kestra.plugin.clevercloud.addons.List`**

Lists all add-ons provisioned in the organisation or personal account. Optional: `organisationId` (defaults to /self when omitted), `fetchType` (enum: FETCH, FETCH_ONE, STORE, NONE, defaults to FETCH). Outputs: `total`, plus `addons` (FETCH), `addon` (FETCH_ONE), or `uri` to an ion file in internal storage (STORE). Each add-on entry contains: `id`, `name`, `realId`, `region`, `zoneId`, `provider` (with `id`, `name`, `shortDesc`, `logoUrl`), `plan` (with `id`, `name`, `slug`), `creationDate` (epoch milliseconds), `configKeys`.

**`io.kestra.plugin.clevercloud.addons.Get`**

Fetches a single add-on by ID. Required: `addonId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `id`, `name`, `realId`, `region`, `providerId`, `providerName`, `planId`, `planName`, `creationDate`, `configKeys`.

**`io.kestra.plugin.clevercloud.addons.Create`**

Provisions a new add-on. Required: `providerId`, `plan`, `region` (these three are mandatory on the Clever Cloud API). Optional: `organisationId` (defaults to /self when omitted), `name`, `addonVersion`. Provisioning is synchronous: the task returns once the add-on is created and usable. Outputs: `id`, `name`, `region`, `providerId`, `planId`, `creationDate`.

**`io.kestra.plugin.clevercloud.addons.GetEnv`**

Fetches the add-on's environment variables (connection credentials). Required: `addonId`. Optional: `organisationId` (defaults to /self when omitted). Outputs: `variables` (map of name to value), `total`. Returns credentials in plain text: avoid logging or persisting its output.

**`io.kestra.plugin.clevercloud.addons.LinkToApplication`**

Attaches an existing add-on to an application via `POST .../applications/{appId}/addons`. Required: `applicationId`, `addonId`. Optional: `organisationId` (defaults to /self when omitted). Returns no output.

**`io.kestra.plugin.clevercloud.addons.UnlinkFromApplication`**

Detaches an add-on from an application via `DELETE .../applications/{appId}/addons/{addonId}`. The add-on itself is not deleted. Required: `applicationId`, `addonId`. Optional: `organisationId` (defaults to /self when omitted). Returns no output.

**`io.kestra.plugin.clevercloud.addons.Delete`**

Permanently deletes the add-on via `DELETE .../addons/{addonId}`. Required: `addonId`. Optional: `organisationId` (defaults to /self when omitted). Returns no output.

## Triggers

**`io.kestra.plugin.clevercloud.deployments.Trigger`**

Polls the deployment list for an application at each `interval` and fires when any DEPLOY action deployment matches `targetState` (enum: OK, FAIL, CANCELLED, WIP). UNDEPLOY records are ignored. The number of deployments checked per poll is controlled by `maxDeployments` (default 25). Optional: `organisationId` (defaults to /self when omitted). Outputs accessible via `{{ trigger.* }}`: `deploymentId`, `state`, `commit`.

The minimum recommended `interval` is PT30S to avoid rate-limiting the Clever Cloud API.

**`io.kestra.plugin.clevercloud.organisations.MemberChangeTrigger`**

Polls the member list of an organisation at each `interval` and fires when the member set changes. Uses KV store to persist the member ID set between evaluations (the members endpoint has no timestamps). The first evaluation always establishes a baseline and never fires. Subsequent polls fire only when a change is detected. Requires `organisationId`, `event` (MEMBER_ADDED, MEMBER_REMOVED, or MEMBER_CHANGED). Outputs via `{{ trigger.* }}`: `organisationId`, `addedMembers` (list of user IDs), `removedMembers` (list of user IDs).

**`io.kestra.plugin.clevercloud.addons.AddonProvisionedTrigger`**

Polls the add-on list for an organisation or personal account at each `interval` and fires when a new add-on appears, detected by diffing the add-on ID set against the previous evaluation (uses KV store, the same approach as `MemberChangeTrigger`, since add-ons have no "READY" status field to poll for). The first evaluation always establishes a baseline and never fires. Optional: `organisationId` (defaults to /self when omitted). Outputs via `{{ trigger.* }}`: `addonIds` (list of newly appeared add-on IDs), `addonId`, `name`, `providerId`, `planId`, `region` (the latter four describe the first newly appeared add-on).
