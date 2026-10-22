package example.technical;

import com.servpp.service.technical.TechnicalInvocation;
import com.servpp.service.technical.TechnicalService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Explicitly bound demonstration observer; neither persists requests nor changes business facts. */
public final class CallCounter implements TechnicalService {
    private static final ConcurrentHashMap<String,LongAdder> COUNTS=new ConcurrentHashMap<>();
    @Override public void handle(TechnicalInvocation event) {
        COUNTS.computeIfAbsent(event.serviceName()+":"+event.phase(),ignored -> new LongAdder()).increment();
    }
    public static long count(String service,TechnicalInvocation.Phase phase) {
        var counter=COUNTS.get(service+":"+phase);return counter==null?0:counter.sum();
    }
    public static void reset(){COUNTS.clear();}
}
