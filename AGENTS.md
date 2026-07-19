# Kestra Clever Cloud Plugin

## What

Provides Kestra tasks and triggers for the [Clever Cloud](https://www.clever-cloud.com/) PaaS platform.
Components live under `io.kestra.plugin.clevercloud`.

## Why

Teams deploying applications on Clever Cloud can integrate deployment lifecycle events directly into
Kestra workflows: list deployments, fetch their details, wait for a specific outcome, or react to
state changes without writing glue scripts.

## How

### Architecture

Single-module plugin. All components share a Bearer-token auth base class, `AbstractCleverCloudConnection`.
HTTP requests are built and sent with Kestra's internal `io.kestra.core.http.client.HttpClient`.
There is no OAuth signing, OkHttp, or ScribeJava dependency: authentication is a single `apiToken`
sent as an `Authorization: Bearer` header.

Source packages under `io.kestra.plugin`:

- `clevercloud` (root: shared Bearer-auth base class)
- `clevercloud.applications` (application lifecycle tasks: list, get, env, create, scale, redeploy, restart, stop, delete)
- `clevercloud.deployments` (deployment tasks and trigger)
- `clevercloud.organisations` (organisation and member management tasks and trigger)
- `clevercloud.addons` (add-on provisioning, inspection, application linking tasks and trigger)

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.clevercloud.AbstractCleverCloudConnection` - shared Bearer-auth base class; owns `apiToken`, `baseUrl()`, URL joining, `buildGetRequest`/`buildPostRequest`/`buildPutRequest`/`buildDeleteRequest`, and error-safe HTTP call handling
- `io.kestra.plugin.clevercloud.applications.List` - list applications, full `ApplicationView` shape, supports `fetchType` (canonical listing task, aliases the removed `organisations.ListApplications`)
- `io.kestra.plugin.clevercloud.applications.Get` - get a single application by ID (zone, instance type/version, state, deploy URL, scaling bounds)
- `io.kestra.plugin.clevercloud.applications.GetEnv` - get all environment variables of an application as a map
- `io.kestra.plugin.clevercloud.applications.SetEnv` - create or update environment variables, one `PUT .../env/{envName}` call per variable
- `io.kestra.plugin.clevercloud.applications.Create` - create an application (name required; zone, instance type/version, min/max instances, min/max flavor optional)
- `io.kestra.plugin.clevercloud.applications.Scale` - update instance count/flavor bounds via a partial `PUT .../applications/{appId}`, only set fields are sent
- `io.kestra.plugin.clevercloud.applications.Redeploy` - trigger a new deployment via `POST .../instances`, optional `commit` and `useCache`
- `io.kestra.plugin.clevercloud.applications.Restart` - same endpoint as Redeploy but never sends `commit`, restarts the currently deployed commit
- `io.kestra.plugin.clevercloud.applications.Stop` - stop running instances via `DELETE .../instances` without deleting the application
- `io.kestra.plugin.clevercloud.applications.Delete` - delete an application via `DELETE .../applications/{appId}`
- `io.kestra.plugin.clevercloud.deployments.List` - list deployments for an application, supports `fetchType` (FETCH, FETCH_ONE, STORE, NONE)
- `io.kestra.plugin.clevercloud.deployments.Get` - get a single deployment by ID
- `io.kestra.plugin.clevercloud.deployments.WaitForState` - poll until a deployment reaches a target state, with `failOnUnreached` to control whether an unreached target throws or just returns the last observed state
- `io.kestra.plugin.clevercloud.deployments.Trigger` - polling trigger that fires on deployment state changes
- `io.kestra.plugin.clevercloud.organisations.Get` - get organisation details (orga_xxx only)
- `io.kestra.plugin.clevercloud.organisations.ListMembers` - list organisation members
- `io.kestra.plugin.clevercloud.organisations.AddMember` - invite a user to the organisation
- `io.kestra.plugin.clevercloud.organisations.RemoveMember` - remove a user from the organisation
- `io.kestra.plugin.clevercloud.organisations.MemberChangeTrigger` - polling trigger that fires when member set changes
- `io.kestra.plugin.clevercloud.addons.List` - list add-ons, full `AddonView` shape, supports `fetchType` (canonical listing task, aliases the removed `organisations.ListAddons`)
- `io.kestra.plugin.clevercloud.addons.Get` - get a single add-on by ID (plan, provider, region, creationDate, configKeys)
- `io.kestra.plugin.clevercloud.addons.Create` - provision a new add-on (providerId, plan, region required by the API; name, version optional)
- `io.kestra.plugin.clevercloud.addons.GetEnv` - get all environment variables (connection credentials) of an add-on as a map
- `io.kestra.plugin.clevercloud.addons.LinkToApplication` - attach an add-on to an application via `POST .../applications/{appId}/addons`
- `io.kestra.plugin.clevercloud.addons.UnlinkFromApplication` - detach an add-on from an application via `DELETE .../applications/{appId}/addons/{addonId}`
- `io.kestra.plugin.clevercloud.addons.Delete` - delete an add-on via `DELETE .../addons/{addonId}`
- `io.kestra.plugin.clevercloud.addons.AddonProvisionedTrigger` - polling trigger that fires when a new add-on appears in the add-on list

### Project Structure

```
plugin-clevercloud/
├── src/main/java/io/kestra/plugin/clevercloud/
│   ├── AbstractCleverCloudConnection.java
│   ├── package-info.java
│   ├── applications/
│   │   ├── package-info.java
│   │   ├── model/
│   │   │   ├── Application.java
│   │   │   ├── EnvironmentVariable.java
│   │   │   └── Message.java
│   │   ├── List.java
│   │   ├── Get.java
│   │   ├── GetEnv.java
│   │   ├── SetEnv.java
│   │   ├── Create.java
│   │   ├── Scale.java
│   │   ├── Redeploy.java
│   │   ├── Restart.java
│   │   ├── Stop.java
│   │   └── Delete.java
│   ├── deployments/
│   │   ├── package-info.java
│   │   ├── model/
│   │   │   ├── Deployment.java
│   │   │   └── DeploymentState.java
│   │   ├── List.java
│   │   ├── Get.java
│   │   ├── WaitForState.java
│   │   └── Trigger.java
│   ├── organisations/
│   │   ├── package-info.java
│   │   ├── model/
│   │   │   ├── Organisation.java
│   │   │   └── Member.java
│   │   ├── Get.java
│   │   ├── ListMembers.java
│   │   ├── AddMember.java
│   │   ├── RemoveMember.java
│   │   └── MemberChangeTrigger.java
│   └── addons/
│       ├── package-info.java
│       ├── model/
│       │   ├── Addon.java
│       │   ├── EnvironmentVariable.java
│       │   └── Message.java
│       ├── List.java
│       ├── Get.java
│       ├── Create.java
│       ├── GetEnv.java
│       ├── LinkToApplication.java
│       ├── UnlinkFromApplication.java
│       ├── Delete.java
│       └── AddonProvisionedTrigger.java
├── src/test/java/io/kestra/plugin/clevercloud/
│   ├── AbstractClevercloudTest.java
│   ├── applications/
│   │   ├── ListTest.java
│   │   ├── GetTest.java
│   │   ├── GetEnvTest.java
│   │   ├── SetEnvTest.java
│   │   ├── CreateTest.java
│   │   ├── ScaleTest.java
│   │   ├── RedeployTest.java
│   │   ├── RestartTest.java
│   │   ├── StopTest.java
│   │   └── DeleteTest.java
│   ├── deployments/
│   │   ├── ListTest.java
│   │   ├── GetTest.java
│   │   ├── WaitForStateTest.java
│   │   └── TriggerTest.java
│   ├── organisations/
│   │   ├── GetTest.java
│   │   ├── ListMembersTest.java
│   │   ├── AddMemberTest.java
│   │   ├── RemoveMemberTest.java
│   │   └── MemberChangeTriggerTest.java
│   └── addons/
│       ├── ListTest.java
│       ├── GetTest.java
│       ├── CreateTest.java
│       ├── GetEnvTest.java
│       ├── LinkToApplicationTest.java
│       ├── UnlinkFromApplicationTest.java
│       ├── DeleteTest.java
│       └── AddonProvisionedTriggerTest.java
├── src/main/resources/
│   ├── doc/io.kestra.plugin.clevercloud.md
│   └── metadata/
│       ├── index.yaml
│       ├── applications.yaml
│       ├── deployments.yaml
│       ├── organisations.yaml
│       └── addons.yaml
├── build.gradle
└── README.md
```

## Local rules

- `apiToken` is the single credential for the whole plugin and must be marked `@PluginProperty(group = "connection", secret = true)`.
- The default base URL is `https://api-bridge.clever-cloud.com/v2`. `baseUrl()` is overridable per class (used by tests to point at WireMock).
- `organisationId` is optional on `Get` (organisations package), `applications.List`, and `addons.List`/`Get`/`Create`/`GetEnv`/`LinkToApplication`/`UnlinkFromApplication`/`Delete`: when omitted, calls target the personal account endpoint (`/self`) instead of `/organisations/{id}`. It is required on `ListMembers`, `AddMember`, `RemoveMember`, and `MemberChangeTrigger` because `/self/members` does not exist on the Clever Cloud API.
- Base the wording on the implemented packages and classes, not on template README text.
- `Trigger` (deployments), `MemberChangeTrigger`, and `AddonProvisionedTrigger` use a plain `Duration` field for `interval` (not `Property<Duration>`) because `PollingTriggerInterface.getInterval()` returns `Duration`.
- `MemberChangeTrigger` uses `runContext.namespaceKv()` to persist the member ID set between evaluations (no timestamps in the members response), keyed by flow id + trigger id + organisation id so triggers with the same id in different flows do not collide.
- `GET /v2/organisations/{orgId}` returns 403 for personal user accounts (user_xxx). Use `applications.List`/`addons.List` for personal accounts.
- All task/trigger tests extend `io.kestra.plugin.clevercloud.AbstractClevercloudTest` for shared `@KestraTest`/`@WireMockTest` wiring and WireMock helpers. Each test file declares its own nested `Testable*` subclass overriding `baseUrl()`.
- `applications.List` is the single canonical task for listing applications. `organisations.ListApplications` was removed and is now a deprecated alias resolving to `applications.List` via `@Plugin(aliases = "io.kestra.plugin.clevercloud.organisations.ListApplications")`, so existing flows referencing the old type keep working unchanged.
- `addons.List` is the single canonical task for listing add-ons. `organisations.ListAddons` was removed and is now a deprecated alias resolving to `addons.List` via `@Plugin(aliases = "io.kestra.plugin.clevercloud.organisations.ListAddons")`, mirroring the `applications.List`/`organisations.ListApplications` precedent so existing flows keep working unchanged.
- No `applications.RedeployTrigger` was added: `deployments.Trigger` already polls the deployment list and fires on state changes, which covers the same use case (react to a new deployment reaching a target state) without a second competing trigger.
- The bulk `PUT .../applications/{appId}/env` endpoint's request body is untyped (`string`) in the Clever Cloud OpenAPI spec, so `SetEnv` uses the unambiguous per-variable endpoint `PUT .../env/{envName}` with body `{"value": ...}` instead, one HTTP call per variable.
- The real `AddonView` schema (verified against `https://api.clever-cloud.com/v2/openapi.json`) has no status/state field at all, unlike `SuperNovaInstanceView` (application instances) which does have a `READY` state. Add-ons are provisioned synchronously by the API: there is nothing to poll for readiness. `addons.AddonProvisionedTrigger` therefore detects newly provisioned add-ons via `creationDate` (present on `AddonView`), the same cutoff-based dedup pattern as `deployments.Trigger`, instead of a nonexistent "READY" status.
- `addons.Create`'s request body is `WannabeAddonProvision`: only `providerId`, `plan`, and `region` are `required` in the real OpenAPI schema (`name`, `version`, `linkedApp`, `options`, and payment fields are all optional). `linkedApp` is intentionally not exposed on `Create` since `addons.LinkToApplication` already covers attaching an add-on to an application.
- `addons.LinkToApplication`'s `POST .../applications/{appId}/addons` request body is a bare JSON string (the add-on ID), not an object, per the OpenAPI schema (`"type": "string"`). `LinkToApplication`/`UnlinkFromApplication` responses are untyped (`default` response, no schema) in the spec, so both tasks return `VoidOutput` rather than parsing an unspecified body.
- `Scale` and `Create` share the `WannabeApplication` PUT/POST target (`.../applications` and `.../applications/{appId}`); `Scale` first `GET`s the current application, rebuilds the full `WannabeApplication` body from it, then overlays only the min/max instance and flavor fields the caller set, so a scale request can never clear name/zone/instance type/version if the API replaces rather than merges the body.
- `Redeploy` and `Restart` share the query-string building for `.../applications/{appId}/instances` via `AbstractCleverCloudConnection.instancesUrl(baseUrl, organisationId, applicationId, queryParams)`.
- `Redeploy` and `Restart` both call `POST .../applications/{appId}/instances`; the only difference is that `Restart` never sends a `commit` query param, so it always redeploys the currently deployed commit instead of a caller-specified one.
- `buildPutRequest` was added to `AbstractCleverCloudConnection` alongside the existing `buildGetRequest`/`buildPostRequest`/`buildDeleteRequest` to support `SetEnv` and `Scale`.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
