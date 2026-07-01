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
- `clevercloud.deployments` (deployment tasks and trigger)
- `clevercloud.organisations` (organisation and member management tasks and trigger)

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.clevercloud.AbstractCleverCloudConnection` - shared Bearer-auth base class; owns `apiToken`, `baseUrl()`, URL joining, and error-safe HTTP call handling
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
│       ├── deployments.yaml
│       └── organisations.yaml
├── build.gradle
└── README.md
```

## Local rules

- `apiToken` is the single credential for the whole plugin and must be marked `@PluginProperty(group = "connection", secret = true)`.
- The default base URL is `https://api-bridge.clever-cloud.com/v2`. `baseUrl()` is overridable per class (used by tests to point at WireMock).
- `organisationId` is optional on every task and trigger: when omitted, calls target the personal account endpoint (`/self`) instead of `/organisations/{id}`.
- Base the wording on the implemented packages and classes, not on template README text.
- `Trigger` (deployments) and `MemberChangeTrigger` use a plain `Duration` field for `interval` (not `Property<Duration>`) because `PollingTriggerInterface.getInterval()` returns `Duration`.
- `MemberChangeTrigger` uses `runContext.namespaceKv()` to persist the member ID set between evaluations (no timestamps in the members response).
- `GET /v2/organisations/{orgId}` returns 403 for personal user accounts (user_xxx). Use ListMembers/ListApplications for user accounts.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
