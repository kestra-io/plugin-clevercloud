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
- `clevercloud.logs` (application log fetch/stream, log drain management, and log pattern trigger, backed by APIv4)

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
- `io.kestra.plugin.clevercloud.organisations.ListAddons` - list add-ons in the organisation
- `io.kestra.plugin.clevercloud.organisations.MemberChangeTrigger` - polling trigger that fires when member set changes
- `io.kestra.plugin.clevercloud.logs.AbstractLogsConnection` - shared base for the logs package; owns `organisationId`/`applicationId` (both required, no `/self` fallback), v4 base URL, and the SSE-based `fetchLogs` helper, which enforces a client-side maxDuration/idleTimeout so it never depends on the server closing the connection
- `io.kestra.plugin.clevercloud.logs.Fetch` - fetch application runtime logs in a bounded time window via the v4 logs SSE endpoint, bounded client-side by `maxDuration` (default PT30S) and `idleTimeout` (default PT10S), supports `fetchType`
- `io.kestra.plugin.clevercloud.logs.Stream` - consume live application logs for a bounded duration (defaults to PT1M, capped at PT15M) via the same v4 logs SSE endpoint, `duration` is enforced client-side so it always terminates even if the server never closes the connection
- `io.kestra.plugin.clevercloud.logs.ListDrains` - list log drains configured for an application, supports `fetchType`
- `io.kestra.plugin.clevercloud.logs.CreateDrain` - create a log drain (RAW_HTTP, SYSLOG_TCP, SYSLOG_UDP, DATADOG, ELASTICSEARCH, NEWRELIC)
- `io.kestra.plugin.clevercloud.logs.DeleteDrain` - delete a log drain by ID
- `io.kestra.plugin.clevercloud.logs.LogPatternTrigger` - polling trigger that fires when a log line matches a regex pattern

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
│   │   │   ├── Member.java
│   │   │   └── Addon.java
│   │   ├── Get.java
│   │   ├── ListMembers.java
│   │   ├── AddMember.java
│   │   ├── RemoveMember.java
│   │   ├── ListAddons.java
│   │   └── MemberChangeTrigger.java
│   └── logs/
│       ├── package-info.java
│       ├── model/
│       │   ├── LogEntry.java
│       │   ├── Drain.java
│       │   ├── DrainType.java
│       │   └── DrainKind.java
│       ├── AbstractLogsConnection.java
│       ├── Fetch.java
│       ├── Stream.java
│       ├── ListDrains.java
│       ├── CreateDrain.java
│       ├── DeleteDrain.java
│       └── LogPatternTrigger.java
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
│   │   ├── ListAddonsTest.java
│   │   └── MemberChangeTriggerTest.java
│   └── logs/
│       ├── FetchTest.java
│       ├── StreamTest.java
│       ├── ListDrainsTest.java
│       ├── CreateDrainTest.java
│       ├── DeleteDrainTest.java
│       └── LogPatternTriggerTest.java
├── src/main/resources/
│   ├── doc/io.kestra.plugin.clevercloud.md
│   └── metadata/
│       ├── index.yaml
│       ├── applications.yaml
│       ├── deployments.yaml
│       ├── organisations.yaml
│       └── logs.yaml
├── build.gradle
└── README.md
```

## Local rules

- `apiToken` is the single credential for the whole plugin and must be marked `@PluginProperty(group = "connection", secret = true)`.
- The default base URL is `https://api-bridge.clever-cloud.com/v2`. `baseUrl()` is overridable per class (used by tests to point at WireMock).
- `organisationId` is optional on `Get` and `ListAddons` (organisations package) and on `applications.List`: when omitted, calls target the personal account endpoint (`/self`) instead of `/organisations/{id}`. It is required on `ListMembers`, `AddMember`, `RemoveMember`, and `MemberChangeTrigger` because `/self/members` does not exist on the Clever Cloud API.
- Base the wording on the implemented packages and classes, not on template README text.
- `Trigger` (deployments) and `MemberChangeTrigger` use a plain `Duration` field for `interval` (not `Property<Duration>`) because `PollingTriggerInterface.getInterval()` returns `Duration`.
- `MemberChangeTrigger` uses `runContext.namespaceKv()` to persist the member ID set between evaluations (no timestamps in the members response), keyed by flow id + trigger id + organisation id so triggers with the same id in different flows do not collide.
- `GET /v2/organisations/{orgId}` returns 403 for personal user accounts (user_xxx). Use `applications.List`/`ListAddons` for personal accounts.
- All task/trigger tests extend `io.kestra.plugin.clevercloud.AbstractClevercloudTest` for shared `@KestraTest`/`@WireMockTest` wiring and WireMock helpers. Each test file declares its own nested `Testable*` subclass overriding `baseUrl()`.
- `applications.List` is the single canonical task for listing applications. `organisations.ListApplications` was removed and is now a deprecated alias resolving to `applications.List` via `@Plugin(aliases = "io.kestra.plugin.clevercloud.organisations.ListApplications")`, so existing flows referencing the old type keep working unchanged.
- No `applications.RedeployTrigger` was added: `deployments.Trigger` already polls the deployment list and fires on state changes, which covers the same use case (react to a new deployment reaching a target state) without a second competing trigger.
- The bulk `PUT .../applications/{appId}/env` endpoint's request body is untyped (`string`) in the Clever Cloud OpenAPI spec, so `SetEnv` uses the unambiguous per-variable endpoint `PUT .../env/{envName}` with body `{"value": ...}` instead, one HTTP call per variable.
- `Scale` and `Create` share the `WannabeApplication` PUT/POST target (`.../applications` and `.../applications/{appId}`); `Scale` first `GET`s the current application, rebuilds the full `WannabeApplication` body from it, then overlays only the min/max instance and flavor fields the caller set, so a scale request can never clear name/zone/instance type/version if the API replaces rather than merges the body.
- `Redeploy` and `Restart` share the query-string building for `.../applications/{appId}/instances` via `AbstractCleverCloudConnection.instancesUrl(baseUrl, organisationId, applicationId, queryParams)`.
- `Redeploy` and `Restart` both call `POST .../applications/{appId}/instances`; the only difference is that `Restart` never sends a `commit` query param, so it always redeploys the currently deployed commit instead of a caller-specified one.
- `buildPutRequest` was added to `AbstractCleverCloudConnection` alongside the existing `buildGetRequest`/`buildPostRequest`/`buildDeleteRequest` to support `SetEnv` and `Scale`.
- The `logs` package targets Clever Cloud APIv4, not v2: there is no `GET /v2/logs/{addonId}` or `/v2/organisations/.../applications/.../logs` endpoint reachable on the live API (confirmed with unauthenticated probes returning a generic gateway 404, unlike real v2 routes which return a JSON 401). The only real, live, Bearer-gated application log endpoint is `GET /v4/logs/organisations/{organisationId}/applications/{applicationId}/logs`, confirmed reachable through `api-bridge.clever-cloud.com` (a fake Bearer token returns `401 invalid-token`, matching the rest of this plugin's auth pattern).
- That v4 logs endpoint is SSE-based (`Accept: text/event-stream`) even for a bounded historical fetch. Live testing showed the server does NOT reliably close the connection once `until` is reached (it can behave like a live tail or idle open with no data), so `AbstractLogsConnection#fetchLogs` never depends on that: it runs `HttpClient#sseRequest` on a bounded worker thread and a watchdog forcibly closes the `HttpClient` (unblocking the read) as soon as the limit is reached, an event's date is at/after `until`, the hard `maxDuration` deadline elapses, or `idleTimeout` passes with no new event, whichever comes first. A real server-side close still short-circuits all of the above. Kestra's `io.kestra.core.http.client.HttpClient#sseRequest` (available since kestraVersion 1.3.0, before this plugin's current 1.3.13) is the same primitive `io.kestra.plugin.core.http.SseRequest` uses, so both `logs.Fetch` (bounded) and `logs.Stream` (bounded live tail, capped at PT15M) were implemented rather than skipped.
- `HttpClient#sseRequest` does not enforce allowed status codes the way `HttpClient#request` does, so `AbstractLogsConnection#fetchLogs` manually checks the response status after the SSE body is consumed and throws the same body-free `HttpClientResponseException` as `AbstractCleverCloudConnection#makeCall` on a non-2xx response. This still works with the client-side timeout: a non-2xx response has nothing left to stream, so the server closes it quickly on its own, well before the watchdog would ever need to step in.
- Log drains (`GET`/`POST /v4/drains/organisations/{organisationId}/applications/{applicationId}/drains`, `DELETE .../drains/{drainId}`) are confirmed reachable the same way (fake Bearer token returns `401 invalid-token`). `CreateDrain`'s request body shape (`kind` + `recipient.type`/`url`/credentials) was cross-checked against the official `@clevercloud/client` JS client (`CreateLogDrainCommand`) rather than guessed, since neither endpoint appears in `https://api.clever-cloud.com/v2/openapi.json`.
- There is no `OVHCLOUD` drain type on the real API: `DrainType` only has `RAW_HTTP`, `SYSLOG_TCP`, `SYSLOG_UDP`, `DATADOG`, `ELASTICSEARCH`, `NEWRELIC`, matching the recipient types the API actually accepts.
- `CreateDrain` does not poll for the created drain to reach `ENABLED` (the official JS client does, via `waitForLogDrainEnabled`): it returns as soon as the API responds, consistent with how `applications.Create` doesn't wait for the app to reach `RUNNING` either.
- `LogPatternTrigger` reuses the same bounded-window SSE fetch as `Fetch`/`Stream` (via the package-private static helpers on `AbstractLogsConnection`, accessible because the trigger lives in the same package) and dedups on `date` strictly-after the previous evaluation cutoff, mirroring `deployments.Trigger`'s cutoff pattern rather than `MemberChangeTrigger`'s KV-diff pattern, since log lines carry a timestamp. It passes `AbstractLogsConnection.DEFAULT_MAX_DURATION` (PT30S) and `DEFAULT_IDLE_TIMEOUT` (PT10S) as fixed internal bounds rather than exposing them as trigger properties, since a polling trigger must return well within its own `interval` regardless.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
