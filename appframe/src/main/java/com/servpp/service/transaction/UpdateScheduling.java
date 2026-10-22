package com.servpp.service.transaction;

import com.servpp.service.scenario.IExecutor;
import com.servpp.service.runtime.QuantumServiceMetadataProvider;
import java.util.*;

/** Serial basic execution plus an explicit SPI for optional automatic update scheduling. */
public final class UpdateScheduling {
    private static final UpdateSchedulingProvider PROVIDER = load();
    private UpdateScheduling() { }
    private static UpdateSchedulingProvider load() {
        var providers = ServiceLoader.load(UpdateSchedulingProvider.class).iterator();
        if (!providers.hasNext()) return null;
        var selected = providers.next();
        if (providers.hasNext()) throw new IllegalStateException("UPDATE_SCHEDULING_PROVIDER_AMBIGUOUS");
        return selected;
    }
    public static boolean isAvailable() { return PROVIDER != null; }
    public static List<IExecutor> order(List<IExecutor> calls) {
        if (PROVIDER == null) return new ArrayList<>(calls);
        var ordered = new ArrayList<IExecutor>();
        var ready = new ArrayList<IExecutor>();
        for (var call : calls) {
            if (call.isPreparedOrdinaryUpdate() || call.isQuantumUpdate()
                    || call.getService() instanceof QuantumServiceMetadataProvider quantum && !quantum.getQuantumAttributeIds().isEmpty()) ready.add(call);
            else { appendReady(ready, ordered); ordered.add(call); }
        }
        appendReady(ready, ordered); return ordered;
    }
    private static void appendReady(List<IExecutor> ready, List<IExecutor> ordered) {
        if (ready.isEmpty()) return;
        var supplied = List.copyOf(PROVIDER.prioritizeReady(List.copyOf(ready)));
        var remaining = new IdentityHashMap<IExecutor,Integer>();
        ready.forEach(call -> remaining.merge(call, 1, Integer::sum));
        for (var call : supplied) {
            Integer count = remaining.get(call);
            if (count == null) throw new IllegalStateException("UPDATE_SCHEDULING_INVALID_PLAN");
            if (count == 1) remaining.remove(call); else remaining.put(call, count - 1);
        }
        if (!remaining.isEmpty()) throw new IllegalStateException("UPDATE_SCHEDULING_INVALID_PLAN");
        ordered.addAll(supplied); ready.clear();
    }
    public static void executePrepared(List<UpdateSchedulingProvider.Work> work) {
        if (PROVIDER == null) work.forEach(item -> item.action().get().run());
        else PROVIDER.executePrepared(List.copyOf(work));
    }
}
