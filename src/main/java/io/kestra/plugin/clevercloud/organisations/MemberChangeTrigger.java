package io.kestra.plugin.clevercloud.organisations;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.clevercloud.AbstractCleverCloudConnection;
import io.kestra.plugin.clevercloud.organisations.model.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger when a member is added or removed from a Clever Cloud organisation",
    description = """
        Polls the member list of the given organisation at each interval and fires when the
        member set changes relative to the previous evaluation.

        Dedup strategy: the Clever Cloud members endpoint returns no timestamps, so the trigger
        persists the current set of member IDs in the namespace KV store after each evaluation
        and compares against it on the next poll. This avoids any dependency on wall-clock skew.

        The first evaluation always stores the baseline and never fires an execution. Subsequent
        evaluations fire only when the member set differs from the stored baseline.

        Set the `event` property to MEMBER_ADDED to fire only on additions, MEMBER_REMOVED to fire
        only on removals, or MEMBER_CHANGED to fire on any change.

        organisationId is required: the /self/members endpoint does not exist on the Clever Cloud API.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fire when a member is added to an organisation",
            full = true,
            code = """
                id: on_member_added
                namespace: company.team

                triggers:
                  - id: watch_members
                    type: io.kestra.plugin.clevercloud.organisations.MemberChangeTrigger
                    apiToken: "{{ secret('CC_API_TOKEN') }}"
                    organisationId: "orga_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    event: MEMBER_ADDED
                    interval: PT1M

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Member added: {{ trigger.addedMembers }}"
                """
        )
    }
)
public class MemberChangeTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<MemberChangeTrigger.Output> {

    @NotNull
    @Schema(title = "API token", description = "Bearer token for the Clever Cloud API. Store as a Kestra secret and reference with {{ secret('CC_API_TOKEN') }}.")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> apiToken;

    @Schema(title = "HTTP client options", description = "Optional HttpConfiguration applied to every Clever Cloud API call, including timeouts and proxy settings.")
    @PluginProperty(group = "advanced")
    HttpConfiguration options;

    protected String baseUrl() {
        return AbstractCleverCloudConnection.DEFAULT_BASE_URL;
    }

    @Schema(
        title = "Organisation ID",
        description = "Required. The /self/members endpoint does not exist on the Clever Cloud API."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> organisationId;

    @Schema(
        title = "Which membership event fires the trigger",
        description = """
            MEMBER_ADDED fires when a new member appears in the list.
            MEMBER_REMOVED fires when a member disappears from the list.
            MEMBER_CHANGED fires on either addition or removal.
            """
    )
    @PluginProperty(group = "main")
    @NotNull
    @Builder.Default
    private Property<MemberEvent> event = Property.ofValue(MemberEvent.MEMBER_CHANGED);

    @Schema(
        title = "How often to check for membership changes",
        description = "ISO-8601 duration. Defaults to PT1M."
    )
    @PluginProperty(group = "reliability")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(1);

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rApiToken = runContext.render(apiToken).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("organisationId is required for MemberChangeTrigger")
        );
        var rEvent = runContext.render(event).as(MemberEvent.class).orElse(MemberEvent.MEMBER_CHANGED);

        var url = AbstractCleverCloudConnection.join(baseUrl(), "organisations/" + rOrgId + "/members");

        logger.debug("Polling members for organisation {}", rOrgId);

        String body;
        try (var client = new HttpClient(runContext, options)) {
            var request = HttpRequest.builder()
                .uri(URI.create(url))
                .method("GET")
                .addHeader("Authorization", "Bearer " + rApiToken)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .build();
            var response = client.request(request, String.class);
            body = response.getBody() != null ? response.getBody() : "[]";
        } catch (HttpClientResponseException e) {
            var status = e.getResponse() != null ? e.getResponse().getStatus().getCode() : -1;
            logger.debug("Clever Cloud API returned {} on GET {}", status, url);
            throw new HttpClientResponseException(
                "Clever Cloud API error " + status + " polling members for " + rOrgId
                    + ": check apiToken and that the organisation exists",
                e.getResponse(),
                e
            );
        }

        var members = AbstractCleverCloudConnection.MAPPER.readValue(
            body, new TypeReference<ArrayList<Member>>() {});

        var currentIds = members.stream()
            .filter(m -> m.getMember() != null && m.getMember().getId() != null)
            .map(m -> m.getMember().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // Persist state in namespace KV keyed by trigger ID so multiple triggers on the same org
        // do not interfere with each other.
        var kvKey = "member-trigger-" + context.getTriggerId() + "-" + rOrgId;
        var kv = runContext.namespaceKv(context.getNamespace());

        var previousIdsOptional = kv.getValue(kvKey);

        if (previousIdsOptional.isEmpty()) {
            // First evaluation: establish baseline, do not fire.
            logger.info("Establishing member baseline for organisation {} ({} member(s))", rOrgId, currentIds.size());
            kv.put(kvKey, new KVValueAndMetadata(new KVMetadata(null, (java.time.Duration) null),
                serializeIds(currentIds)));
            return Optional.empty();
        }

        Set<String> previousIds = deserializeIds(previousIdsOptional.get().value());

        var added = new LinkedHashSet<>(currentIds);
        added.removeAll(previousIds);

        var removed = new LinkedHashSet<>(previousIds);
        removed.removeAll(currentIds);

        boolean hasChange = !added.isEmpty() || !removed.isEmpty();
        boolean matches = switch (rEvent) {
            case MEMBER_ADDED -> !added.isEmpty();
            case MEMBER_REMOVED -> !removed.isEmpty();
            case MEMBER_CHANGED -> hasChange;
        };

        // Always persist the latest member set so the next poll diffs against it.
        if (hasChange) {
            kv.put(kvKey, new KVValueAndMetadata(new KVMetadata(null, (java.time.Duration) null),
                serializeIds(currentIds)));
        }

        if (!matches) {
            return Optional.empty();
        }

        logger.info("Member change detected in organisation {}: added={}, removed={}", rOrgId, added, removed);

        var output = Output.builder()
            .organisationId(rOrgId)
            .addedMembers(List.copyOf(added))
            .removedMembers(List.copyOf(removed))
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private static String serializeIds(Set<String> ids) throws Exception {
        return AbstractCleverCloudConnection.MAPPER.writeValueAsString(ids);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> deserializeIds(Object value) throws Exception {
        if (value instanceof String s) {
            return AbstractCleverCloudConnection.MAPPER.readValue(s, new TypeReference<LinkedHashSet<String>>() {});
        }
        if (value instanceof List<?> list) {
            return new LinkedHashSet<>((List<String>) list);
        }
        return new LinkedHashSet<>();
    }

    public enum MemberEvent {
        MEMBER_ADDED,
        MEMBER_REMOVED,
        MEMBER_CHANGED
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Organisation ID that was polled")
        private final String organisationId;

        @Schema(title = "User IDs of members who were added since the last evaluation")
        private final List<String> addedMembers;

        @Schema(title = "User IDs of members who were removed since the last evaluation")
        private final List<String> removedMembers;
    }
}
