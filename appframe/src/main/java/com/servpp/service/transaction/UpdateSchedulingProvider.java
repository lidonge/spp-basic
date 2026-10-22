package com.servpp.service.transaction;

import com.servpp.service.scenario.IExecutor;
import java.util.List;

/** Optional optimization. Each supplied operation already carries the owner's transaction context. */
public interface UpdateSchedulingProvider {
    record Work(Object[] arguments, Runnable prepare, java.util.function.Supplier<Runnable> action) {
        public Work { arguments = arguments == null ? new Object[0] : arguments.clone(); }
    }
    /** A ready segment contains no calculation, query or independent-boundary barrier. */
    List<IExecutor> prioritizeReady(List<IExecutor> ready);
    /** Prepare on the owner thread before binding actions; join every started operation even after failure. */
    void executePrepared(List<Work> work);
}
