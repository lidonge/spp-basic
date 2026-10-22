package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.repository.RepositoryParticipantSnapshots;
import java.beans.Introspector;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Technical identity and optimistic-read evidence accompany facts across a domain boundary. */
public final class ParticipantWireState {
    public record State(boolean identified, RepositoryParticipantSnapshots.WireBaseline baseline, Map<String,State> children,
                        java.util.Set<String> modified) {
        public State {
            children = children == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(children));
            modified = modified == null ? null : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(modified));
        }
        public State(boolean identified, RepositoryParticipantSnapshots.WireBaseline baseline, Map<String,State> children) {
            this(identified,baseline,children,null);
        }
    }
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private ParticipantWireState() { }
    public static State capture(Object value, String domain) { return capture(value, domain, new IdentityHashMap<>()); }
    private static State capture(Object value, String domain, IdentityHashMap<Object,Boolean> visited) {
        if (scalar(value) || visited.put(value, true) != null) return null;
        Map<String,State> children = new LinkedHashMap<>();
        visit(value, (name,child) -> { State state = capture(child, domain, visited); if (state != null) children.put(name,state); });
        return new State(ParticipantIdentity.isIdentified(value), RepositoryParticipantSnapshots.exportBaseline(value,DomainServiceRouter.localOrigin(value,domain)),children,PropertyChanges.paths(value));
    }
    public static void restore(Object value, State state) {
        if (value == null || state == null) return;
        PropertyChanges.restore(value,state.modified());
        ParticipantIdentity.confirm(value,state.identified());
        if (state.baseline() != null) RepositoryParticipantSnapshots.importBaseline(value,state.baseline(),
                MAPPER.convertValue(state.baseline().facts(),value.getClass()));
        visit(value, (name,child) -> restore(child,state.children().get(name)));
    }
    static void restoreForDomain(Object value, State state, String domain) {
        if (value == null || state == null) return;
        var baseline = state.baseline();
        if (baseline != null) baseline = new RepositoryParticipantSnapshots.WireBaseline(baseline.facts(),baseline.platform(),
                baseline.revisions().stream().filter(revision -> domain.equals(revision.origin())).toList());
        PropertyChanges.restore(value,state.modified());
        ParticipantIdentity.confirm(value,state.identified());
        if (baseline != null) RepositoryParticipantSnapshots.importBaseline(value,baseline,MAPPER.convertValue(baseline.facts(),value.getClass()));
        visit(value,(name,child) -> restoreForDomain(child,state.children().get(name),domain));
    }
    private static boolean scalar(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value.getClass().isEnum() || value instanceof java.time.temporal.Temporal;
    }
    private static void visit(Object value, java.util.function.BiConsumer<String,Object> action) {
        if (value instanceof Iterable<?> items) {
            int index = 0; for (Object item : items) action.accept(Integer.toString(index++),item);
        } else if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) action.accept(Integer.toString(index),Array.get(value,index));
        } else if (!value.getClass().getName().startsWith("java.")) {
            try {
                for (var property : Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors())
                    if (property.getReadMethod() != null) action.accept(property.getName(),property.getReadMethod().invoke(value));
            } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
                throw new IllegalStateException("PARTICIPANT_WIRE_STATE_FAILED",failure);
            }
        }
    }
}
