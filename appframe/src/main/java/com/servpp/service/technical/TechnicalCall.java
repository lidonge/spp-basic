package com.servpp.service.technical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.repository.RepositoryParticipantSnapshots;
import com.servpp.service.runtime.ChangeTrackedParticipant;
import com.servpp.service.runtime.PropertyChanges;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.transaction.ScenarioCommitEvidence;
import java.beans.Introspector;
import java.lang.reflect.Array;
import java.util.*;

/** Invocation control and read-only participant facts; never exposes mutable business objects. */
public final class TechnicalCall {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final Object[] arguments;
    private final ScenarioCommitEvidence.Scope evidence;
    private List<Participant> participants;
    private TechnicalInvocation.Phase phase;
    private boolean completed;
    private Object result;
    private BusinessChecks.Result businessResult;

    TechnicalCall(Object[] arguments, ScenarioCommitEvidence.Scope evidence) {
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
        this.evidence = evidence;
    }
    void phase(TechnicalInvocation.Phase phase) { this.phase = phase; participants = null; }
    boolean completed() { return completed; }
    Object returnedValue() { return result; }
    void returned(Object result) { this.result = result; }
    BusinessChecks.Result completedBusinessResult() { return businessResult; }

    /** A before service may supply a replay result, including null, without executing the body. */
    public void complete(Object response) {
        complete(response, new BusinessChecks.Result(true,""));
    }
    public void complete(Object response, BusinessChecks.Result businessResult) {
        requireBefore();
        if (completed) throw new IllegalStateException("TECHNICAL_RESULT_ALREADY_COMPLETED");
        this.businessResult = Objects.requireNonNull(businessResult);
        completed = true; result = response;
    }
    /** Includes fresh/unidentified inputs as well as queried participants; values are detached. */
    public JsonNode argumentFacts() { return JSON.valueToTree(arguments); }
    public boolean replayed() { return completed; }
    public JsonNode resultFacts() { return JSON.valueToTree(result); }
    public String resultType() { return result == null ? "" : result.getClass().getName(); }
    public boolean hasCommittedStage() { return evidence != null && evidence.hasCommittedStage(); }
    private void requireBefore() {
        if (phase != TechnicalInvocation.Phase.BEFORE) throw new IllegalStateException("TECHNICAL_BEFORE_REQUIRED");
    }

    public List<Participant> participants() {
        if (participants == null) {
            var values = new ArrayList<Participant>();
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object argument : arguments) collect(argument, values, visited);
            participants = List.copyOf(values);
        }
        return participants;
    }
    public final class Participant {
        private final Object value;
        private final JsonNode facts, baseline;
        private final Set<String> changes;
        private Participant(Object value, Object baseline) {
            this.value = value;
            this.facts = JSON.valueToTree(value);
            this.baseline = baseline == null ? null : JSON.valueToTree(baseline);
            this.changes = Set.copyOf(PropertyChanges.paths(value));
        }
        public JsonNode facts() { return facts.deepCopy(); }
        public Optional<JsonNode> baseline() { return baseline == null ? Optional.empty() : Optional.of(baseline.deepCopy()); }
        public Set<String> changedPaths() { return changes; }
        public boolean tracked() { return value instanceof ChangeTrackedParticipant; }
        public void selectUpdateFields(Set<String> paths) {
            requireBefore(); UpdateFieldPlans.select(value, paths);
        }
    }

    private void collect(Object value, List<Participant> output, Set<Object> visited) {
        if (value == null || !visited.add(value)) return;
        if (value instanceof Iterable<?> items) { items.forEach(item -> collect(item,output,visited)); return; }
        if (value instanceof Map<?,?> items) { items.values().forEach(item -> collect(item,output,visited)); return; }
        if (value.getClass().isArray()) {
            for (int index=0; index<Array.getLength(value); index++) collect(Array.get(value,index),output,visited);
            return;
        }
        if (value.getClass().getName().startsWith("java.") || value instanceof Number || value instanceof Enum<?>) return;
        Object baseline;
        try { baseline = RepositoryParticipantSnapshots.require(value); }
        catch (IllegalStateException absent) {
            if (!"PERSISTENCE_QUERY_SNAPSHOT_REQUIRED".equals(absent.getMessage())) throw absent;
            baseline = null;
        }
        if (baseline != null || value instanceof ChangeTrackedParticipant) {
            output.add(new Participant(value,baseline));
            return; // Nested facts belong to this participant's mapped update paths.
        }
        try {
            for (var property : Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors()) {
                var getter = property.getReadMethod();
                if (getter == null) continue;
                getter.setAccessible(true); collect(getter.invoke(value),output,visited);
            }
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("TECHNICAL_PARTICIPANT_ACCESS_FAILED",failure);
        }
    }
}
