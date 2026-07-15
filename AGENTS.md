# Kestra Clevercloud Plugin

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

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.clevercloud.AbstractCleverCloudConnection` - shared Bearer-auth base class; owns `apiToken`, `baseUrl()`, URL joining, `buildGetRequest`/`buildPostRequest`/`buildPutRequest`/`buildDeleteRequest`, and error-safe HTTP call handling
- `io.kestra.plugin.clevercloud.applications.List` - list applications, full `ApplicationView` shape, supports `fetchType` (overlaps `organisations.ListApplications`, same endpoint, richer output)
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
- `io.kestra.plugin.clevercloud.organisations.ListApplications` - list applications in the organisation
- `io.kestra.plugin.clevercloud.organisations.ListAddons` - list add-ons in the organisation
- `io.kestra.plugin.clevercloud.organisations.MemberChangeTrigger` - polling trigger that fires when member set changes

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
│   └── organisations/
│       ├── package-info.java
│       ├── model/
│       │   ├── Organisation.java
│       │   ├── Member.java
│       │   ├── Application.java
│       │   └── Addon.java
│       ├── Get.java
│       ├── ListMembers.java
│       ├── AddMember.java
│       ├── RemoveMember.java
│       ├── ListApplications.java
│       ├── ListAddons.java
│       └── MemberChangeTrigger.java
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
│   └── organisations/
│       ├── GetTest.java
│       ├── ListMembersTest.java
│       ├── AddMemberTest.java
│       ├── RemoveMemberTest.java
│       ├── ListApplicationsTest.java
│       ├── ListAddonsTest.java
│       └── MemberChangeTriggerTest.java
├── src/main/resources/
│   ├── doc/io.kestra.plugin.clevercloud.md
│   └── metadata/
│       ├── index.yaml
│       ├── applications.yaml
│       ├── deployments.yaml
│       └── organisations.yaml
├── build.gradle
└── README.md
```

## Local rules

- `apiToken` is the single credential for the whole plugin and must be marked `@PluginProperty(group = "connection", secret = true)`.
- The default base URL is `https://api-bridge.clever-cloud.com/v2`. `baseUrl()` is overridable per class (used by tests to point at WireMock).
- `organisationId` is optional on `Get`, `ListApplications`, and `ListAddons`: when omitted, calls target the personal account endpoint (`/self`) instead of `/organisations/{id}`. It is required on `ListMembers`, `AddMember`, `RemoveMember`, and `MemberChangeTrigger` because `/self/members` does not exist on the Clever Cloud API.
- Base the wording on the implemented packages and classes, not on template README text.
- `Trigger` (deployments) and `MemberChangeTrigger` use a plain `Duration` field for `interval` (not `Property<Duration>`) because `PollingTriggerInterface.getInterval()` returns `Duration`.
- `MemberChangeTrigger` uses `runContext.namespaceKv()` to persist the member ID set between evaluations (no timestamps in the members response), keyed by flow id + trigger id + organisation id so triggers with the same id in different flows do not collide.
- `GET /v2/organisations/{orgId}` returns 403 for personal user accounts (user_xxx). Use ListApplications/ListAddons for personal accounts.
- All task/trigger tests extend `io.kestra.plugin.clevercloud.AbstractClevercloudTest` for shared `@KestraTest`/`@WireMockTest` wiring and WireMock helpers. Each test file declares its own nested `Testable*` subclass overriding `baseUrl()`.
- `applications.List` overlaps `organisations.ListApplications` (identical endpoint, `GET .../applications`) but returns the full `ApplicationView` shape (state, deployUrl, instance scaling bounds) instead of the summary fields. Both are kept: `organisations.ListApplications` groups with other org-scoped listings (members, add-ons), `applications.List` groups with the rest of the application lifecycle tasks.
- No `applications.RedeployTrigger` was added: `deployments.Trigger` already polls the deployment list and fires on state changes, which covers the same use case (react to a new deployment reaching a target state) without a second competing trigger.
- The bulk `PUT .../applications/{appId}/env` endpoint's request body is untyped (`string`) in the Clever Cloud OpenAPI spec, so `SetEnv` uses the unambiguous per-variable endpoint `PUT .../env/{envName}` with body `{"value": ...}` instead, one HTTP call per variable.
- `Scale` and `Create` share the `WannabeApplication` PUT/POST target (`.../applications` and `.../applications/{appId}`); `Scale` first `GET`s the current application, rebuilds the full `WannabeApplication` body from it, then overlays only the min/max instance and flavor fields the caller set, so a scale request can never clear name/zone/instance type/version if the API replaces rather than merges the body.
- `Redeploy` and `Restart` share the query-string building for `.../applications/{appId}/instances` via `AbstractCleverCloudConnection.instancesUrl(baseUrl, organisationId, applicationId, queryParams)`.
- `Redeploy` and `Restart` both call `POST .../applications/{appId}/instances`; the only difference is that `Restart` never sends a `commit` query param, so it always redeploys the currently deployed commit instead of a caller-specified one.
- `buildPutRequest` was added to `AbstractCleverCloudConnection` alongside the existing `buildGetRequest`/`buildPostRequest`/`buildDeleteRequest` to support `SetEnv` and `Scale`.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
