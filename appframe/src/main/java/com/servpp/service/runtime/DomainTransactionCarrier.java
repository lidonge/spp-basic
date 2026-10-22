package com.servpp.service.runtime;

import java.util.Map;
import java.util.function.Supplier;
import com.servpp.service.transaction.TransactionMode;

/** Distributed coordinators implement propagation through the internal domain protocol. */
public interface DomainTransactionCarrier {
    boolean supports(TransactionMode mode);
    Map<String,String> capture(TransactionMode mode);
    /** Completed global transactions may replay stored results, but cannot perform new writes. */
    default boolean permitsExecution() { return true; }
    /** The coordinator owns stable business-call identity in addition to its native phase-two retries. */
    default DomainServiceProtocol.Result executeUpdate(DomainServiceProtocol.Call call,
            com.servpp.service.Service service,String caller,String serial,
            Supplier<DomainServiceProtocol.Result> action) {
        throw new IllegalStateException("DOMAIN_MANAGED_CALL_PROTOCOL_REQUIRED");
    }
    <T> T receive(Map<String,String> context, Supplier<T> action);
}
