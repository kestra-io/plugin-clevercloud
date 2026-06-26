package io.kestra.plugin.clevercloud.organisations;

import com.fasterxml.jackson.core.type.TypeReference;
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
import lombok.*;
import lombok.experimental.SuperBuilder;

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
                    consumerKey: "{{ secret('CC_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('CC_CONSUMER_SECRET') }}"
                    token: "{{ secret('CC_TOKEN') }}"
                    tokenSecret: "{{ secret('CC_TOKEN_SECRET') }}"
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

    @Schema(
        title = "OAuth consumer key",
        description = "Store as a Kestra secret and reference with {{ secret('CC_CONSUMER_KEY') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> consumerKey;

    @Schema(
        title = "OAuth consumer secret",
        description = "Store as a Kestra secret and reference with {{ secret('CC_CONSUMER_SECRET') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> consumerSecret;

    @Schema(
        title = "OAuth access token",
        description = "Store as a Kestra secret and reference with {{ secret('CC_TOKEN') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> token;

    @Schema(
        title = "OAuth access token secret",
        description = "Store as a Kestra secret and reference with {{ secret('CC_TOKEN_SECRET') }}."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> tokenSecret;

    @Schema(title = "Override the Clever Cloud API base URL", description = "Used in tests to point at a mock server. Do not set in production flows.")
    @PluginProperty(group = "advanced", hidden = true)
    private Property<String> apiBaseUrl;

    @Schema(title = "Organisation ID (orga_xxx or user_xxx for personal accounts)")
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
    private Property<MemberEvent> event = Property.of(MemberEvent.MEMBER_CHANGED);

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

        var rConsumerKey = runContext.render(consumerKey).as(String.class).orElseThrow();
        var rConsumerSecret = runContext.render(consumerSecret).as(String.class).orElseThrow();
        var rToken = runContext.render(token).as(String.class).orElseThrow();
        var rTokenSecret = runContext.render(tokenSecret).as(String.class).orElseThrow();
        var rOrgId = runContext.render(organisationId).as(String.class).orElseThrow();
        var rEvent = runContext.render(event).as(MemberEvent.class).orElse(MemberEvent.MEMBER_CHANGED);

        var rBaseUrl = runContext.render(apiBaseUrl).as(String.class).orElse(AbstractCleverCloudConnection.BASE_URL);
        var client = AbstractCleverCloudConnection.signedClient(rConsumerKey, rConsumerSecret, rToken, rTokenSecret);

        var url = rBaseUrl + "organisations/" + rOrgId + "/members";

        logger.debug("Polling members for organisation {}", rOrgId);
        var body = client.get(url);
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
