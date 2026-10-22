package com.servpp.service.technical;

import com.servpp.service.*;
import com.servpp.service.runtime.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceInterceptorsTest {
    private static final List<TechnicalInvocation> EVENTS = new CopyOnWriteArrayList<>();
    public static class Recorder implements TechnicalService {
        public void handle(TechnicalInvocation event) { EVENTS.add(event); }
    }
    public static class Broken implements TechnicalService {
        public void handle(TechnicalInvocation event) { throw new IllegalStateException("BROKEN_" + event.phase()); }
    }
    public static class ConfiguredRecorder extends Recorder {
        static Map<String,String> configuration;
        public void configure(Map<String,String> values) { configuration = values; }
    }
    private ServiceInterceptors.Binding binding(TechnicalInvocation.Phase phase, String name, Class<?> type) {
        return new ServiceInterceptors.Binding(phase, name, type.getName(), Map.of());
    }
    private ServiceInterceptors plan(ServiceInterceptors.Binding... bindings) {
        return ServiceInterceptors.create(getClass().getClassLoader(), List.of(bindings));
    }
    private ServiceInterceptors recording() {
        return plan(binding(TechnicalInvocation.Phase.BEFORE,"trace",Recorder.class),
                binding(TechnicalInvocation.Phase.AFTER,"trace",Recorder.class), binding(TechnicalInvocation.Phase.FAILURE,"trace",Recorder.class));
    }
    @Test public void orderedHooksSeeTrustedHeadersAndNormalBusinessRejections() {
        EVENTS.clear(); var plan = recording();
        Object result = InvocationContext.remote(InvocationContext.Origin.SERVICE, "caller", "serial", () -> plan.invoke("demo.preview", GeneratedServiceKind.SCENARIO, () -> {
            BusinessChecks.recordRejection("NOT_ALLOWED"); return "normal response";
        }));
        assertEquals("normal response", result); assertEquals(2, EVENTS.size());
        assertEquals(TechnicalInvocation.Phase.BEFORE, EVENTS.get(0).phase());
        assertEquals(TechnicalInvocation.Phase.AFTER, EVENTS.get(1).phase());
        assertEquals("caller", EVENTS.get(1).header().callerId()); assertEquals("serial", EVENTS.get(1).header().requestSerialNumber());
        assertFalse(EVENTS.get(1).businessResult().passed()); assertEquals("NOT_ALLOWED", EVENTS.get(1).businessResult().code());
        assertTrue(EVENTS.get(1).elapsedNanos() >= 0);
        BusinessChecks.restoreScenarioResult(null);
    }
    @Test public void beforeCanRejectAndFailureErrorsPreserveOriginal() {
        EVENTS.clear(); var plan = plan(binding(TechnicalInvocation.Phase.BEFORE,"guard",Broken.class),
                binding(TechnicalInvocation.Phase.FAILURE,"broken",Broken.class), binding(TechnicalInvocation.Phase.FAILURE,"trace",Recorder.class));
        boolean[] executed = {false};
        var failure = assertThrows(IllegalStateException.class, () -> plan.invoke("demo.save", GeneratedServiceKind.ATOMIC_UPDATE, () -> { executed[0]=true; return null; }));
        assertFalse(executed[0]); assertEquals("BROKEN_BEFORE", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length); assertEquals("BROKEN_FAILURE", failure.getSuppressed()[0].getMessage());
        assertSame(failure, EVENTS.get(0).failure());
    }
    @Test public void afterFailureDoesNotChangeSuccessfulResultOrSkipObservers() {
        EVENTS.clear(); var plan = plan(binding(TechnicalInvocation.Phase.AFTER,"broken",Broken.class), binding(TechnicalInvocation.Phase.AFTER,"trace",Recorder.class));
        assertEquals("committed", plan.invoke("demo.save", GeneratedServiceKind.ATOMIC_UPDATE, () -> "committed"));
        assertEquals(1, EVENTS.size()); assertEquals(TechnicalInvocation.Phase.AFTER, EVENTS.get(0).phase());
    }
    public interface Compute extends Service { void compute(); }
    public static class ComputeService implements Compute, GeneratedServiceKindProvider {
        int calls;
        public String getName() { return "demo.compute"; }
        public GeneratedServiceKind getGeneratedServiceKind() { return GeneratedServiceKind.ATOMIC_CALCULATE; }
        public ServiceInterceptors getTechnicalInterceptors() { return new ServiceInterceptorsTest().recording(); }
        public void compute() { calls++; }
        public Object execute(Object... args) { compute(); return null; }
    }
    @Test public void typedAtomicAndExecuteAreInterceptedOnceAndMetadataIsNot() {
        EVENTS.clear(); var original = new ComputeService(); var decorated = (Compute)ServiceInterceptors.decorate(original);
        assertEquals("demo.compute", decorated.getName()); assertEquals(0, EVENTS.size());
        decorated.compute(); assertEquals(2, EVENTS.size());
        decorated.execute(); assertEquals(4, EVENTS.size()); assertEquals(2, original.calls);
        assertSame(original, ((DelegatingService)decorated).getDelegateService());
    }
    public static class Scene extends SourceScenarioService implements GeneratedServiceKindProvider {
        public String getName() { return "demo.scene"; }
        public GeneratedServiceKind getGeneratedServiceKind() { return GeneratedServiceKind.SCENARIO; }
        public ServiceInterceptors getTechnicalInterceptors() { return new ServiceInterceptorsTest().recording(); }
        public Object execute(Object... args) { return executeWithTechnicalInterceptors(() -> executeWithTechnicalInterceptors(() -> "done")); }
        public String startTransaction() { return null; } public void endTransaction(String id) { }
    }
    @Test public void sceneRetainsItsClassAndNestedEntryDoesNotDoubleIntercept() {
        EVENTS.clear(); var scene = new Scene(); assertSame(scene, ServiceInterceptors.decorate(scene));
        assertEquals("done", scene.execute()); assertEquals(2, EVENTS.size());
    }
    @Test public void concurrentCallsKeepTheirOwnHeadersAndFrames() throws Exception {
        EVENTS.clear(); var plan = recording(); var pool = Executors.newFixedThreadPool(4);
        try {
            var work = new ArrayList<Callable<Object>>();
            for (int index=0; index<16; index++) { String serial="r"+index;
                work.add(() -> InvocationContext.remote(InvocationContext.Origin.SERVICE, "caller", serial, () -> plan.invoke("demo.scene", GeneratedServiceKind.SCENARIO, () -> serial))); }
            for (var result : pool.invokeAll(work)) assertNotNull(result.get());
            assertEquals(32, EVENTS.size());
            for (int index=0; index<16; index++) { String serial="r"+index;
                assertEquals(2, EVENTS.stream().filter(event -> serial.equals(event.header().requestSerialNumber())).count()); }
        } finally { pool.shutdownNow(); }
    }
    @Test public void invalidPhysicalImplementationsFailBeforeBusinessExecution() {
        assertThrows(IllegalArgumentException.class, () -> plan(binding(TechnicalInvocation.Phase.BEFORE,"bad",String.class)));
        assertThrows(IllegalStateException.class, () -> ServiceInterceptors.create(getClass().getClassLoader(), List.of(
                new ServiceInterceptors.Binding(TechnicalInvocation.Phase.BEFORE,"missing","no.such.TechnicalService",Map.of()))));
    }
    public interface Check extends Service { void check(); }
    public static class CheckService implements Check, GeneratedServiceKindProvider {
        public String getName() { return "demo.check"; }
        public GeneratedServiceKind getGeneratedServiceKind() { return GeneratedServiceKind.ATOMIC_CHECK; }
        public ServiceInterceptors getTechnicalInterceptors() { return new ServiceInterceptorsTest().recording(); }
        public void check() { BusinessChecks.fail("DENIED"); }
        public Object execute(Object... args) { check(); return null; }
    }
    @Test public void checkObserverReadsTheActiveCheckFrame() {
        EVENTS.clear(); var check=(Check)ServiceInterceptors.decorate(new CheckService());
        assertFalse(BusinessChecks.evaluate(check::check).passed());
        assertEquals("DENIED",EVENTS.get(1).businessResult().code());
        assertFalse(EVENTS.get(1).businessResult().passed());
    }
    public static class RemoteCompute extends ComputeService implements RemoteGeneratedService { }
    @Test public void remoteMetadataDoesNotInvokeTheTransport() {
        EVENTS.clear(); int[] calls={0};
        RemoteServiceAdapter adapter=new RemoteServiceAdapter() {
            public Set<String> serviceNames() { return Set.of("demo.compute"); }
            public Object execute(String name,Object... arguments) { calls[0]++; return null; }
        };
        var remote=RemoteServiceProxy.decorate(new RemoteCompute(),name->Optional.of(adapter));
        var decorated=(Compute)ServiceInterceptors.decorate(remote);
        assertEquals(0,calls[0]); decorated.compute(); assertEquals(1,calls[0]);
        assertEquals(2,EVENTS.size()); assertEquals(TechnicalInvocation.Phase.AFTER,EVENTS.get(1).phase());
    }
    @Test public void configurationResolvesPhysicalPlaceholders() {
        String key="spp.test.technical.logger"; String old=System.getProperty(key);
        try {
            System.setProperty(key,"configured");
            ServiceInterceptors.create(getClass().getClassLoader(),List.of(new ServiceInterceptors.Binding(
                    TechnicalInvocation.Phase.BEFORE,"config",ConfiguredRecorder.class.getName(),Map.of("logger","${"+key+":fallback}"))));
            assertEquals("configured",ConfiguredRecorder.configuration.get("logger"));
            assertThrows(UnsupportedOperationException.class,()->ConfiguredRecorder.configuration.put("bad","value"));
        } finally { if (old==null)System.clearProperty(key); else System.setProperty(key,old); }
    }
}
