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

Single-module plugin. All components share an OAuth 1.0a signing layer in `AbstractCleverCloudConnection`.
HTTP requests are built with OkHttp and signed via ScribeJava (HMAC-SHA1).

Source packages under `io.kestra.plugin`:

- `clevercloud` (root: shared base class, placeholder task, API descriptor)
- `clevercloud.deployments` (deployment tasks and trigger)

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.clevercloud.AbstractCleverCloudConnection` - shared OAuth 1.0a auth base class
- `io.kestra.plugin.clevercloud.CleverCloudApi` - ScribeJava API descriptor for Clever Cloud
- `io.kestra.plugin.clevercloud.deployments.List` - list deployments for an application
- `io.kestra.plugin.clevercloud.deployments.Get` - get a single deployment by ID
- `io.kestra.plugin.clevercloud.deployments.WaitForState` - poll until a deployment reaches a target state
- `io.kestra.plugin.clevercloud.deployments.DeploymentTrigger` - polling trigger that fires on deployment state changes

### Project Structure

```
plugin-clevercloud/
├── src/main/java/io/kestra/plugin/clevercloud/
│   ├── AbstractCleverCloudConnection.java
│   ├── CleverCloudApi.java
│   ├── ClevercloudTask.java
│   ├── package-info.java
│   └── deployments/
│       ├── package-info.java
│       ├── model/
│       │   └── Deployment.java
│       ├── List.java
│       ├── Get.java
│       ├── WaitForState.java
│       └── DeploymentTrigger.java
├── src/test/java/io/kestra/plugin/clevercloud/
│   └── deployments/
│       ├── ListTest.java
│       ├── GetTest.java
│       ├── WaitForStateTest.java
│       ├── TestableList.java
│       ├── TestableGet.java
│       └── TestableWaitForState.java
├── src/main/resources/
│   ├── doc/io.kestra.plugin.clevercloud.md
│   └── metadata/
│       ├── index.yaml
│       └── deployments.yaml
├── build.gradle
└── README.md
```

## Local rules

- All credential properties (`consumerKey`, `consumerSecret`, `token`, `tokenSecret`) must be marked `@PluginProperty(secret = true)`.
- Base the wording on the implemented packages and classes, not on template README text.
- `DeploymentTrigger` uses a plain `Duration` field for `interval` (not `Property<Duration>`) because `PollingTriggerInterface.getInterval()` returns `Duration`.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
