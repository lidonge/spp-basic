package com.servpp.service.technical;

import com.servpp.service.Service;
import com.servpp.service.DelegatingService;
import com.servpp.service.SourceScenarioService;
import com.servpp.service.runtime.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One interception frame per logical service invocation, outside its runtime decorators. */
public final class ServiceInterceptors {
    public record Binding(TechnicalInvocation.Phase phase, String name, String implementation, Map<String,String> configuration) {
        public Binding { configuration = Map.copyOf(configuration); }
    }
    private record Hook(TechnicalInvocation.Phase phase, TechnicalService service) { }
    private static final ServiceInterceptors NONE = new ServiceInterceptors(List.of());
    private static final Logger DIAGNOSTICS = Logger.getLogger("spp.technical.interceptors");
    private final List<Hook> hooks;
    private final ThreadLocal<Boolean> active = new ThreadLocal<>();
    private ServiceInterceptors(List<Hook> hooks) { this.hooks = List.copyOf(hooks); }
    public static ServiceInterceptors none() { return NONE; }
    /** Entry protocols may bind an invocation-owned service instance to all three lifecycle phases. */
    public static ServiceInterceptors lifecycle(TechnicalService service) {
        Objects.requireNonNull(service);
        return new ServiceInterceptors(Arrays.stream(TechnicalInvocation.Phase.values()).map(phase -> new Hook(phase,service)).toList());
    }
    public static ServiceInterceptors create(ClassLoader loader, List<Binding> bindings) {
        if (bindings.isEmpty()) return NONE;
        var services = new LinkedHashMap<String,TechnicalService>();
        var definitions = new HashMap<String,Binding>();
        var hooks = new ArrayList<Hook>();
        for (var binding : bindings) {
            Objects.requireNonNull(binding.phase());
            var previous = definitions.putIfAbsent(binding.name(), binding);
            if (previous != null && (!previous.implementation().equals(binding.implementation())
                    || !previous.configuration().equals(binding.configuration())))
                throw new IllegalArgumentException("Conflicting technical service implementation: " + binding.name());
            var service = services.computeIfAbsent(binding.name(), ignored -> instantiate(loader, binding));
            hooks.add(new Hook(binding.phase(), service));
        }
        return new ServiceInterceptors(hooks);
    }
    private static TechnicalService instantiate(ClassLoader loader, Binding binding) {
        try {
            Class<?> type = Class.forName(binding.implementation(), true, loader);
            if (!TechnicalService.class.isAssignableFrom(type)) throw new IllegalArgumentException("TECHNICAL_SERVICE_INTERFACE_REQUIRED: " + binding.name());
            TechnicalService service = (TechnicalService)type.getConstructor().newInstance();
            var resolved = new LinkedHashMap<String,String>();
            binding.configuration().forEach((name, value) -> resolved.put(name, PhysicalRuntimeCatalog.resolveConfigValue(value)));
            service.configure(Map.copyOf(resolved));
            return service;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TECHNICAL_SERVICE_INITIALIZATION_FAILED: " + binding.name(), failure);
        }
    }
    public Object invoke(String name, GeneratedServiceKind kind, Supplier<Object> action) {
        return invoke(name,kind,new Object[0],action);
    }
    public Object invoke(String name, GeneratedServiceKind kind, Object[] arguments, Supplier<Object> action) {
        if (hooks.isEmpty()) return UpdateFieldPlans.invoke(action);
        if (Boolean.TRUE.equals(active.get())) return action.get();
        active.set(true);
        try (var evidence = com.servpp.service.transaction.ScenarioCommitEvidence.begin()) {
            return UpdateFieldPlans.invoke(() -> invokeFrame(name,kind,arguments,action,evidence));
        } finally { active.remove(); }
    }
    private Object invokeFrame(String name, GeneratedServiceKind kind, Object[] arguments, Supplier<Object> action,
                               com.servpp.service.transaction.ScenarioCommitEvidence.Scope evidence) {
        var header = InvocationContext.current();
        var call = new TechnicalCall(arguments,evidence);
        long start = System.nanoTime();
        try {
            try {
                call.phase(TechnicalInvocation.Phase.BEFORE);
                run(new TechnicalInvocation(name, TechnicalInvocation.Phase.BEFORE, kind, header, 0,
                        new BusinessChecks.Result(true, ""), null, call));
                Object result;
                if (call.completed()) {
                    if (kind == GeneratedServiceKind.ATOMIC_CHECK) BusinessChecks.restoreCheckResult(call.completedBusinessResult());
                    else BusinessChecks.restoreScenarioResult(call.completedBusinessResult());
                    result = call.returnedValue();
                } else result = action.get();
                call.returned(result);
                call.phase(TechnicalInvocation.Phase.AFTER);
                run(new TechnicalInvocation(name, TechnicalInvocation.Phase.AFTER, kind, header,
                        System.nanoTime() - start, kind == GeneratedServiceKind.ATOMIC_CHECK
                            ? BusinessChecks.currentCheckResult() : BusinessChecks.scenarioResult(), null, call));
                return result;
            } catch (RuntimeException | Error original) {
                call.phase(TechnicalInvocation.Phase.FAILURE);
                run(new TechnicalInvocation(name, TechnicalInvocation.Phase.FAILURE, kind, header,
                        System.nanoTime() - start, BusinessChecks.scenarioResult(), original, call));
                throw original;
            }
        } finally { call.phase(null); }
    }
    private void run(TechnicalInvocation event) {
        for (var hook : hooks) if (hook.phase() == event.phase()) {
            try { hook.service().handle(event); }
            catch (RuntimeException | Error failure) {
                if (event.phase() == TechnicalInvocation.Phase.BEFORE) throw failure;
                if (event.phase() == TechnicalInvocation.Phase.AFTER && hook.service().critical()) {
                    if (event.call().hasCommittedStage())
                        com.servpp.service.transaction.TransactionOutcomes.record(failure,
                                com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN);
                    throw failure;
                }
                if (event.failure() != null) {
                    if (failure != event.failure()) event.failure().addSuppressed(failure);
                } else {
                    // Observer failure cannot turn already committed business into a new failed transaction.
                    try { DIAGNOSTICS.log(Level.WARNING, "Technical after hook failed for " + event.serviceName(), failure); }
                    catch (RuntimeException | Error ignored) { /* Diagnostics must not replace the business outcome. */ }
                }
            }
        }
    }
    public static Service decorate(Service service) {
        if (!(service instanceof TechnicalInterceptionProvider provider)) return service;
        ServiceInterceptors interceptors = provider.getTechnicalInterceptors();
        var installed = new ArrayList<Binding>();
        for (var system : ServiceLoader.load(SystemTechnicalServiceProvider.class)) installed.addAll(system.bindings(service));
        if (!installed.isEmpty()) {
            var combined = new ArrayList<>(create(service.getClass().getClassLoader(),installed).hooks);
            combined.addAll(interceptors.hooks);
            interceptors = new ServiceInterceptors(combined);
        }
        if (interceptors.hooks.isEmpty() && (!(service instanceof GeneratedServiceKindProvider kind)
                || kind.getGeneratedServiceKind() != GeneratedServiceKind.ATOMIC_UPDATE)) return service;
        if (service instanceof SourceScenarioService scene) {
            scene.configureTechnicalInterceptors(interceptors);
            return scene;
        }
        var interfaces = new LinkedHashSet<Class<?>>();
        collectInterfaces(service.getClass(), interfaces); interfaces.add(Service.class); interfaces.add(DelegatingService.class);
        Set<String> businessMethods = new HashSet<>();
        if (service instanceof GeneratedServiceKindProvider) {
            String simpleName = service.getName().substring(service.getName().lastIndexOf('.') + 1);
            businessMethods.add(simpleName);
        }
        ServiceInterceptors selected = interceptors;
        return (Service)Proxy.newProxyInstance(service.getClass().getClassLoader(), interfaces.toArray(Class[]::new),
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                        case "equals" -> proxy == arguments[0]; case "hashCode" -> System.identityHashCode(proxy);
                        default -> service.toString();
                    };
                    if (method.getName().equals("getDelegateService")) return service;
                    Supplier<Object> action = () -> invokeMethod(service, method, arguments);
                    return method.getName().equals("execute") || businessMethods.contains(method.getName())
                            ? selected.invoke(service.getName(), service instanceof GeneratedServiceKindProvider kind
                                ? kind.getGeneratedServiceKind() : GeneratedServiceKind.UNKNOWN,
                                invocationArguments(method,arguments), action)
                            : action.get();
                });
    }
    private static Object[] invocationArguments(Method method, Object[] arguments) {
        if ("execute".equals(method.getName()) && arguments != null && arguments.length == 1
                && arguments[0] instanceof Object[] values) return values;
        return arguments;
    }
    private static Object invokeMethod(Service service, Method method, Object[] arguments) {
        try { return method.invoke(service, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("TECHNICAL_SERVICE_INVOCATION_FAILED", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void collectInterfaces(Class<?> type, Set<Class<?>> result) {
        if (type == null) return;
        for (var item : type.getInterfaces()) if (result.add(item)) collectInterfaces(item, result);
        collectInterfaces(type.getSuperclass(), result);
    }
}
