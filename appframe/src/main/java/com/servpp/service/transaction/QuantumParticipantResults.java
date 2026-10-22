package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Live participant destinations for this settlement; never a continuously refreshed view. */
public final class QuantumParticipantResults {
    private record Key(String attribute, String object) { }
    private record Destination(Key key, Consumer<BigDecimal> writer) { }
    private final Map<Object,Map<String,List<Destination>>> destinations = new IdentityHashMap<>();
    private static QuantumParticipantResults of(TransactionContext context) {
        return context.getOrCreateAttribute(QuantumParticipantResults.class.getName(), QuantumParticipantResults::new);
    }
    static void register(TransactionContext context, Object participant, String property,
                         String attribute, String identity, Consumer<BigDecimal> writer) {
        if (participant == null) return;
        of(context).destinations.computeIfAbsent(participant, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(property, ignored -> new ArrayList<>()).add(new Destination(new Key(attribute,identity),writer));
    }
    public static void link(Object source, Object target, String property, Consumer<BigDecimal> writer) {
        TransactionContext context = DefaultTransactionManager.getLastContext();
        if (context == null || context.getQuantumEntanglementBoundary() == null) return;
        var values = of(context).destinations.get(source);
        if (values == null || !values.containsKey(property)) return;
        for (var destination : List.copyOf(values.get(property)))
            register(context,target,property,destination.key().attribute(),destination.key().object(),writer);
    }
    static void refill(TransactionContext context, List<QuantumJournalEntry> entries) {
        Map<Key,BigDecimal> settled = new LinkedHashMap<>();
        for (var entry : entries) settled.put(new Key(entry.getAttributeId(),entry.getObjectId()),entry.getAfterValue());
        for (var properties : of(context).destinations.values()) for (var writers : properties.values())
            for (var destination : writers) if (settled.containsKey(destination.key()))
                destination.writer().accept(settled.get(destination.key()));
    }
}
