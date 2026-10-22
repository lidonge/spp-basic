package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.*;
import java.lang.reflect.Method;
import java.util.*;

/** Internal atomic-service protocol; scenarios remain explicit public business entrypoints. */
public final class DomainServiceProtocol {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    public record Call(String service, String signature, List<JsonNode> participants,
                       List<ParticipantWireState.State> state, String globalSerialNumber, Map<String,String> transaction) { }
    public record Result(List<JsonNode> participants, List<ParticipantWireState.State> state, BusinessChecks.Result check) { }
    public record ReplayIdentity(String service, String caller, String serial,Call invocation) {
        public ReplayIdentity(String service,String caller,String serial) { this(service,caller,serial,null); }
    }
    private DomainServiceProtocol() { }

    static Method method(Class<?> type, String name, int count) {
        for (Class<?> contract : type.getInterfaces()) {
            Method value = method(contract,name,count); if (value != null) return value;
        }
        return Arrays.stream(type.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(name)
                && candidate.getParameterCount() == count).findFirst().orElse(null);
    }
    static String signature(Service service) {
        if (!(service instanceof ScopeMetadataProvider metadata)) throw new IllegalStateException("DOMAIN_SERVICE_METADATA_REQUIRED");
        String name = service.getName().substring(service.getName().lastIndexOf('.')+1);
        Method method = method(service.getClass(),name,metadata.getServiceParameterNames().size());
        if (method == null) throw new IllegalStateException("DOMAIN_SERVICE_METHOD_REQUIRED: " + service.getName());
        return metadata.getServiceParameterNames() + "|" + Arrays.toString(method.getGenericParameterTypes())
                + (service instanceof GeneratedServiceKindProvider kind && kind.getGeneratedServiceKind()==GeneratedServiceKind.ATOMIC_UPDATE
                    && service instanceof TransactionalService transaction ? "|"+transaction.getTransactionMode()+"|"+transaction.getTransactionResourceId() : "");
    }
    public static Result execute(Call call, String caller, String serial) {
        if (call.service() == null || !call.service().matches("[A-Za-z_$][\\w$]*\\.[A-Za-z_$][\\w$]*"))
            throw new IllegalArgumentException("DOMAIN_SERVICE_NAME_REQUIRED");
        Service service = DefaultServiceContainer.getInstance().getService(call.service());
        if (!(service instanceof GeneratedServiceKindProvider kind) || kind.getGeneratedServiceKind() == GeneratedServiceKind.SCENARIO)
            throw new IllegalArgumentException("DOMAIN_ATOMIC_SERVICE_REQUIRED");
        if (!Objects.equals(signature(service),call.signature())) throw new IllegalStateException("DOMAIN_SERVICE_SIGNATURE_MISMATCH");
        if (service instanceof QuantumServiceMetadataProvider quantum && !quantum.getQuantumAttributeIds().isEmpty())
            throw new IllegalStateException("REMOTE_DOMAIN_QUANTUM_RECEIPT_PROTOCOL_REQUIRED");
        boolean update = kind.getGeneratedServiceKind() == GeneratedServiceKind.ATOMIC_UPDATE;
        ScopeMetadataProvider replayMetadata = new ScopeMetadataProvider() {
            public boolean isLocalScope() { return false; }
            public boolean isUpdateEntry() { return update; }
            public String getInputHeaderName() { return null; }
            public String getOutputHeaderName() { return null; }
        };
        java.util.function.Supplier<Result> invoke = () -> {
            Result[] result = {null};
            ParticipantIdentity.execute(() -> result[0] = executeOnce(call,service,update));
            return result[0];
        };
        if (update && call.transaction() != null && !call.transaction().isEmpty()) {
            DomainTransactionCarrier carrier = transactionCarrier(call.transaction());
            // A managed branch is owned by the transaction adapter, independently of the APP request switch.
            return InvocationContext.remote(InvocationContext.Origin.SERVICE,caller,serial,() ->
                    ServiceInvocationScope.execute(() -> carrier.executeUpdate(call,service,caller,serial,invoke)));
        }
        java.util.function.Supplier<Object> action = () -> receiveTransaction(call.transaction(),invoke);
        // Request coordination stays outside the propagated business transaction.
        return InvocationContext.remote(InvocationContext.Origin.SERVICE,caller,serial, () -> (Result)
                (update && ((ScopeMetadataProvider) service).isSystemIdempotencyEnabled()
                    ? com.servpp.service.idempotency.BusinessIdempotencyManager.execute("domain:"+call.service(),
                        new ReplayIdentity(call.service(),caller,serial,call),replayMetadata,action)
                    : ServiceInvocationScope.execute(action)));

    }
    private static Result executeOnce(Call call, Service service, boolean update) {
        for (DomainTransactionCarrier carrier : ServiceLoader.load(DomainTransactionCarrier.class))
            if (!carrier.permitsExecution()) throw new IllegalStateException("DOMAIN_TRANSACTION_CLOSED_REPLAY_REQUIRED");
        ScopeMetadataProvider metadata = (ScopeMetadataProvider) service;
        int count = metadata.getServiceParameterNames().size();
        if (call.participants().size() != count || call.state().size() != count) throw new IllegalArgumentException("DOMAIN_PARTICIPANT_ARITY_MISMATCH");
        Method method = method(service.getClass(),service.getName().substring(service.getName().lastIndexOf('.')+1),count);
        Object[] arguments = new Object[count];
        for (int index = 0; index < count; index++) arguments[index] = MAPPER.convertValue(call.participants().get(index),
                MAPPER.getTypeFactory().constructType(method.getGenericParameterTypes()[index]));
        String domain = call.service().substring(0,call.service().indexOf('.'));
        return DomainRpcContext.execute(domain, () -> BusinessTraceContext.execute(call.globalSerialNumber(), () -> {
            for (int index = 0; index < count; index++) ParticipantWireState.restoreForDomain(arguments[index],call.state().get(index),domain);
            BusinessChecks.restoreScenarioResult(null);
            Runnable action = () -> {
                for (Object argument : arguments) GeneratedRoleIdentityContextBinder.attach(argument);
                invoke(method,service,arguments);
            };
            BusinessChecks.Result[] checked = {new BusinessChecks.Result(true, "")};
            if (service instanceof GeneratedServiceKindProvider kind && kind.getGeneratedServiceKind() == GeneratedServiceKind.ATOMIC_CHECK) {
                Runnable check = action;
                action = () -> checked[0] = BusinessChecks.evaluate(check);
            }
            final Runnable invocation = action;
            if (update) {
                if (!(service instanceof TransactionalService transactional)) throw new IllegalStateException("DOMAIN_UPDATE_TRANSACTION_METADATA_REQUIRED");
                TransactionMode mode = transactional.getTransactionMode();
                if (mode == TransactionMode.LOCAL) throw new IllegalStateException("LOCAL_SERVICE_CANNOT_JOIN_REMOTE_DOMAIN_CALL");
                Runnable transaction = () -> DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
                                    public Service getService() { return service; }
                                    public Object[] getArguments() { return arguments; }
                                    public void execute() { invocation.run(); }
                                }));
                SourceTransactionBoundary.execute(TransactionPropagation.REQUIRED, mode, () -> {
                    if (mode == TransactionMode.SAGA)
                        com.servpp.service.repository.RepositoryWriteRuleProvider.executeInRepositoryScope(service, transaction);
                    else DefaultTransactionManager.getInstance().getQuantumAccessAdapter().executeInAccessScope(transaction);
                });
            } else invocation.run();
            List<JsonNode> facts = new ArrayList<>(); List<ParticipantWireState.State> state = new ArrayList<>();
            for (Object argument : arguments) {
                facts.add(MAPPER.valueToTree(argument)); state.add(ParticipantWireState.capture(argument,domain));
            }
            return new Result(facts,state,checked[0]);
        }));
    }
    private static void invoke(Method method, Service service, Object[] arguments) {
        try { method.invoke(service,arguments); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("DOMAIN_PROVIDER_FAILED",failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("DOMAIN_PROVIDER_INVOCATION_FAILED",failure); }
    }
    static Map<String,String> captureTransaction() {
        TransactionMode mode = DeclaredTransactionContext.currentMode();
        if (mode == null || mode == TransactionMode.SAGA || mode == TransactionMode.QUANTUM) return Map.of();
        if (mode == TransactionMode.LOCAL) throw new IllegalStateException("LOCAL_CANNOT_INVOKE_REMOTE_DOMAIN");
        for (DomainTransactionCarrier carrier : ServiceLoader.load(DomainTransactionCarrier.class))
            if (carrier.supports(mode)) return carrier.capture(mode);
        throw new IllegalStateException("DOMAIN_TRANSACTION_CONTEXT_CARRIER_REQUIRED: " + mode);
    }
    private static <T> T receiveTransaction(Map<String,String> context, java.util.function.Supplier<T> action) {
        if (context == null || context.isEmpty()) return action.get();
        return transactionCarrier(context).receive(context,action);
    }
    private static DomainTransactionCarrier transactionCarrier(Map<String,String> context) {
        TransactionMode mode = TransactionMode.valueOf(context.get("mode"));
        if (mode != TransactionMode.AT && mode != TransactionMode.XA)
            throw new IllegalStateException("DOMAIN_MANAGED_TRANSACTION_MODE_INVALID: " + mode);
        DomainTransactionCarrier selected = null;
        for (DomainTransactionCarrier carrier : ServiceLoader.load(DomainTransactionCarrier.class))
            if (carrier.supports(mode)) {
                if (selected != null) throw new IllegalStateException("DOMAIN_TRANSACTION_CARRIER_AMBIGUOUS: " + mode);
                selected = carrier;
            }
        if (selected == null) throw new IllegalStateException("DOMAIN_TRANSACTION_CONTEXT_CARRIER_REQUIRED: " + mode);
        return selected;
    }
}
