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
- `clevercloud.logs` (application log fetch/stream, log drain management, and log pattern trigger, backed by APIv4)

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.clevercloud.AbstractCleverCloudConnection` - shared Bearer-auth base class; owns `apiToken`, `baseUrl()`, URL joining, `buildGetRequest`/`buildPostRequest`/`buildPutRequest`/`buildDeleteRequest`, and error-safe HTTP call handling
- `io.kestra.plugin.clevercloud.applications.List` - list applications, full `ApplicationView` shape, supports `fetchType` (canonical listing task, aliases the removed `organisations.ListApplications`)
- `io.kestra.plugin.clevercloud.applications.Get` - get a single application by ID (zone, instance type/version, state, deploy URL, scaling bounds)
- `io.kestra.plugin.clevercloud.applications.GetEnv` - get all environment variables of an application as a map
- `io.kestra.plugin.clevercloud.applications.SetEnv` - create or update environment variables, one `PUT .../env/{envName}` call per variable
- `io.kestra.plugin.clevercloud.applications.Create` - create an application (name, instance type, instance version required; zone, min/max instances, min/max flavor optional)
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
- `io.kestra.plugin.clevercloud.addons.Create` - provision a new add-on (providerId, region required by the API; plan accepts a slug or raw id and defaults to the cheapest plan when omitted; name, version optional)
- `io.kestra.plugin.clevercloud.addons.GetEnv` - get all environment variables (connection credentials) of an add-on as a map
- `io.kestra.plugin.clevercloud.addons.LinkToApplication` - attach an add-on to an application via `POST .../applications/{appId}/addons`
- `io.kestra.plugin.clevercloud.addons.UnlinkFromApplication` - detach an add-on from an application via `DELETE .../applications/{appId}/addons/{addonId}`
- `io.kestra.plugin.clevercloud.addons.Delete` - delete an add-on via `DELETE .../addons/{addonId}`
- `io.kestra.plugin.clevercloud.addons.AddonProvisionedTrigger` - polling trigger that fires when a new add-on appears in the add-on list
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
│   │   │   └── Member.java
│   │   ├── Get.java
│   │   ├── ListMembers.java
│   │   ├── AddMember.java
│   │   ├── RemoveMember.java
│   │   └── MemberChangeTrigger.java
│   ├── addons/
│   │   ├── package-info.java
│   │   ├── model/
│   │   │   ├── Addon.java
│   │   │   └── EnvironmentVariable.java
│   │   ├── List.java
│   │   ├── Get.java
│   │   ├── Create.java
│   │   ├── GetEnv.java
│   │   ├── LinkToApplication.java
│   │   ├── UnlinkFromApplication.java
│   │   ├── Delete.java
│   │   └── AddonProvisionedTrigger.java
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
│   │   └── MemberChangeTriggerTest.java
│   ├── addons/
│   │   ├── ListTest.java
│   │   ├── GetTest.java
│   │   ├── CreateTest.java
│   │   ├── GetEnvTest.java
│   │   ├── LinkToApplicationTest.java
│   │   ├── UnlinkFromApplicationTest.java
│   │   ├── DeleteTest.java
│   │   └── AddonProvisionedTriggerTest.java
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
│       ├── addons.yaml
│       └── logs.yaml
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
- The real `AddonView` schema (verified against `https://api.clever-cloud.com/v2/openapi.json`) has no status/state field at all, unlike `SuperNovaInstanceView` (application instances) which does have a `READY` state. Add-ons are provisioned synchronously by the API: there is nothing to poll for readiness. `addons.AddonProvisionedTrigger` therefore detects newly provisioned add-ons via a KV-backed set diff of add-on IDs between evaluations, the same pattern as `organisations.MemberChangeTrigger`, instead of the timestamp-cutoff approach still used by `deployments.Trigger` (a known flaw there, tracked separately: a just-created deployment can fall outside the `context.getDate()` cutoff window and never fire).
- `addons.Create`'s request body is `WannabeAddonProvision`: only `providerId`, `plan`, and `region` are `required` in the real OpenAPI schema (`name`, `version`, `linkedApp`, `options`, and payment fields are all optional). `linkedApp` is intentionally not exposed on `Create` since `addons.LinkToApplication` already covers attaching an add-on to an application.
- `addons.Create`'s `plan` property accepts either a plan slug (e.g. `free`, `dev`) or a raw `plan_...` id, matching how the Clever Cloud CLI (`clever addon create --plan`) and console speak in slugs while the API's `WannabeAddonProvision.plan` field only accepts an id. A value already starting with `plan_` is passed through as-is with no lookup, so existing flows keep working unchanged. Otherwise it is resolved against the public add-on providers catalog (`GET .../products/addonproviders`), matching by slug (or name as a fallback); when `plan` is omitted, the cheapest plan for the provider is used, mirroring the CLI's default. The catalog is fetched through the plugin's own `baseUrl()`/`makeCall` first (the api-bridge host serves it fine when authenticated with a valid `apiToken`) and falls back to a direct unauthenticated `GET https://api.clever-cloud.com/v2/products/addonproviders` (verified reachable and identical in content) if that does not return data, since the catalog is public.
- `addons.LinkToApplication`'s `POST .../applications/{appId}/addons` request body is a bare JSON string (the add-on ID), not an object, per the OpenAPI schema (`"type": "string"`). `LinkToApplication`/`UnlinkFromApplication` responses are untyped (`default` response, no schema) in the spec, so both tasks return `VoidOutput` rather than parsing an unspecified body.
- `Scale` and `Create` share the `WannabeApplication` PUT/POST target (`.../applications` and `.../applications/{appId}`); `Scale` first `GET`s the current application, rebuilds the full `WannabeApplication` body from it, then overlays only the min/max instance and flavor fields the caller set, so a scale request can never clear name/zone/instance type/version if the API replaces rather than merges the body.
- `Redeploy` and `Restart` share the query-string building for `.../applications/{appId}/instances` via `AbstractCleverCloudConnection.instancesUrl(baseUrl, organisationId, applicationId, queryParams)`.
- `Redeploy` and `Restart` both call `POST .../applications/{appId}/instances`; the only difference is that `Restart` never sends a `commit` query param, so it always redeploys the currently deployed commit instead of a caller-specified one.
- `buildPutRequest` was added to `AbstractCleverCloudConnection` alongside the existing `buildGetRequest`/`buildPostRequest`/`buildDeleteRequest` to support `SetEnv` and `Scale`.
- `addons.AddonProvisionedTrigger.evaluate()` delegates the actual add-on fetch to `addons.List` (built via `buildListTask()`, `fetchType` forced to `FETCH`) instead of hand-rolling its own HTTP call, so both entry points share one implementation. `buildListTask()` is a protected hook overridden only in `AddonProvisionedTriggerTest` (not in main source) to route the delegate at a WireMock base URL: an earlier attempt at wiring a `baseUrl()`-overriding `List` subclass directly into the trigger's main source broke Kestra's plugin registry scan for the whole module (every test failed with `No storage interface can be found for 'kestra.storage.type=local'. Supported types are: []`, since the registry scan for the module's own classes choked on an unregistered `RunnableTask` subclass with no `@Plugin`/`@Schema` metadata). Keeping the override confined to test source (mirroring the existing `Testable*` pattern already proven safe by `ListTest.TestableList`) avoids that entirely.
- `addons.Delete` returns `VoidOutput` instead of parsing the delete confirmation message: the response body carries no information a flow needs to act on, so the `addons.model.Message` class (previously used only here) was removed along with the parsing.
- The `logs` package targets Clever Cloud APIv4, not v2: there is no `GET /v2/logs/{addonId}` or `/v2/organisations/.../applications/.../logs` endpoint reachable on the live API (confirmed with unauthenticated probes returning a generic gateway 404, unlike real v2 routes which return a JSON 401). The only real, live, Bearer-gated application log endpoint is `GET /v4/logs/organisations/{organisationId}/applications/{applicationId}/logs`, confirmed reachable through `api-bridge.clever-cloud.com` (a fake Bearer token returns `401 invalid-token`, matching the rest of this plugin's auth pattern).
- That v4 logs endpoint is SSE-based (`Accept: text/event-stream`) even for a bounded historical fetch. Live testing showed the server does NOT reliably close the connection once `until` is reached (it can behave like a live tail or idle open with no data), so `AbstractLogsConnection#fetchLogs` never depends on that: it runs `HttpClient#sseRequest` on a bounded worker thread and a watchdog forcibly closes the `HttpClient` (unblocking the read) as soon as the limit is reached, an event's date is at/after `until`, the hard `maxDuration` deadline elapses, or `idleTimeout` passes with no new event, whichever comes first. A real server-side close still short-circuits all of the above. Kestra's `io.kestra.core.http.client.HttpClient#sseRequest` (available since kestraVersion 1.3.0, before this plugin's current 1.3.13) is the same primitive `io.kestra.plugin.core.http.SseRequest` uses, so both `logs.Fetch` (bounded) and `logs.Stream` (bounded live tail, capped at PT15M) were implemented rather than skipped.
- `HttpClient#sseRequest` does not enforce allowed status codes the way `HttpClient#request` does, so `AbstractLogsConnection#fetchLogs` manually checks the response status after the SSE body is consumed and throws the same body-free `HttpClientResponseException` as `AbstractCleverCloudConnection#makeCall` on a non-2xx response. This still works with the client-side timeout: a non-2xx response has nothing left to stream, so the server closes it quickly on its own, well before the watchdog would ever need to step in.
- Reaching `limit`/`until` inside the SSE event callback also force-closes the `HttpClient` before throwing `SseStopSignal`, the same way the watchdog does. Apache's classic HTTP client quietly drains the rest of the response body before letting a handler exception propagate (so the connection can be reused), which would otherwise block for as long as a still-open, slowly-trickling connection keeps sending. Closing first makes that drain hit an already-closed connection and return immediately, confirmed by `FetchTest`/`StreamTest`'s `limitReachedMidStreamReturnsEarly`/`untilReachedMidStreamReturnsEarly` tests, which use a WireMock `withChunkedDribbleDelay` stub sized so the first chunk (containing the events) arrives in about a second while later chunks keep the connection open for well over the test's assertion bound.
- Log drains (`GET`/`POST /v4/drains/organisations/{organisationId}/applications/{applicationId}/drains`, `DELETE .../drains/{drainId}`) are confirmed reachable the same way (fake Bearer token returns `401 invalid-token`). `CreateDrain`'s request body shape (`kind` + `recipient.type`/`url`/credentials) was cross-checked against the official `@clevercloud/client` JS client (`CreateLogDrainCommand`) rather than guessed, since neither endpoint appears in `https://api.clever-cloud.com/v2/openapi.json`.
- There is no `OVHCLOUD` drain type on the real API: `DrainType` only has `RAW_HTTP`, `SYSLOG_TCP`, `SYSLOG_UDP`, `DATADOG`, `ELASTICSEARCH`, `NEWRELIC`, matching the recipient types the API actually accepts.
- `CreateDrain` does not poll for the created drain to reach `ENABLED` (the official JS client does, via `waitForLogDrainEnabled`): it returns as soon as the API responds, consistent with how `applications.Create` doesn't wait for the app to reach `RUNNING` either.
- `LogPatternTrigger` reuses the same bounded-window SSE fetch as `Fetch`/`Stream` (via the package-private static helpers on `AbstractLogsConnection`, accessible because the trigger lives in the same package) and dedups on `date` strictly-after the previous evaluation cutoff, mirroring `deployments.Trigger`'s cutoff pattern rather than `MemberChangeTrigger`'s KV-diff pattern, since log lines carry a timestamp. It passes `AbstractLogsConnection.DEFAULT_MAX_DURATION` (PT30S) and `DEFAULT_IDLE_TIMEOUT` (PT10S) as fixed internal bounds rather than exposing them as trigger properties, since a polling trigger must return well within its own `interval` regardless.
- `Drain.status` is a nested object on the real v4 API (`{"date": ..., "status": "CREATED|ENABLED|ENABLING|DISABLING|DISABLED|DELETED", "authorId": ...}`), not a bare string: confirmed live (a real `RAW_HTTP` drain creation returned this shape and crashed deserialization when `status` was typed as `String`) and cross-checked against `CleverCloud/clever-client.js`'s raw `ApiLogDrainPayload` type, which the JS client itself flattens client-side before handing a `LogDrainStatus` string to its own callers. `Drain.Status` models the object with its real fields (`date`, `state` mapped from the JSON key `status` via `@JsonProperty`, `authorId`); `CreateDrain.Output.status` stays a plain `String` and extracts `drain.getStatus().getState()`, so the task's own output contract is unaffected by the nested shape underneath.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
