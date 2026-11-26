package com.servpp.technical.serial;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.serial.SerialEngine;
import com.servpp.service.serial.SerialRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.jdbc.JdbcStore;
import me.ahoo.cosid.time.TimeSource;

/**
 * {@link SerialEngine} over the open-source CosId 5 library (Apache-2.0). It has no special standing: it is bound
 * like any other engine with
 * <pre>
 * technical service movementSerialEngine {
 *     implementation: "com.servpp.technical.serial.CosIdSerialEngine";
 *     config mode = "segment"; config step = "100";
 *     config datasourceUrl = "jdbc:..."; config driverClassName = "...";
 * }
 * </pre>
 * Configuration (validated here, not by the grammar):
 * <ul>
 * <li>{@code mode}: {@code segment} (default) keeps one CosId {@link SegmentGenerator} per counter over the JDBC
 * {@link RangeStore} (table {@code cosid_range}, key {@code namespace/counter}); {@code snowflake} keeps one CosId
 * runtime snowflake generator on a static machine slot (no per-scope order, globally unique).</li>
 * <li>segment: {@code step} (default 100), {@code namespace} (default {@code spp}), {@code initTable} (default
 * {@code true}: create the CosId tables), {@code datasourceUrl} (required), {@code driverClassName},
 * {@code username}, {@code password}, {@code maxCounters} (default 10000 live generators).</li>
 * <li>snowflake: {@code machine} (required, 0..1023).</li>
 * </ul>
 * A segment counter pre-allocates {@code step} numbers, so a restart skips the unused rest of a segment: this engine
 * is not gapless. A scope that carries a date gets one CosId name per date, which is the daily reset. (CosId's own
 * grouped reset follows the wall clock; a business date is data, so it is not used.)
 */
public final class CosIdSerialEngine implements SerialEngine, AutoCloseable {
    private static final int KEY_LIMIT = 255; // CosId stores the range key "{namespace}/{name}" in varchar(255)
    private static final int MACHINE_BITS = 10; // the default node bits of the CosId snowflake layout
    private CosIdRuntime runtime;
    private IdGenerator snowflake;
    private JdbcStore store;
    private RangeStore ranges;
    private ExecutorService refills;
    private BatchPolicy policy;
    private String namespace;
    private int maxCounters;
    private final Map<String, IdGenerator> generators = new LinkedHashMap<>(16, 0.75f, true);

    @Override public synchronized void configure(Map<String, String> configuration) {
        String mode = configuration.getOrDefault("mode", "segment");
        switch (mode) {
            case "segment" -> {
                String url = configuration.get("datasourceUrl");
                if (url == null || url.isBlank()) throw new IllegalArgumentException("SERIAL_CONFIGURATION_REQUIRED: datasourceUrl of cosid segment mode");
                DataSource dataSource = new DriverManagerJdbcDataSource(configuration.get("driverClassName"), url,
                        configuration.get("username"), configuration.get("password"));
                store = JdbcStore.of(dataSource, Duration.ofSeconds(10));
                if (Boolean.parseBoolean(configuration.getOrDefault("initTable", "true"))) store.initSchema();
                ranges = store.ranges();
                // lookahead 0: reserve only when the current range is used up, so a restart skips at most one step
                policy = BatchPolicy.builder().step(positive(configuration, "step", 100)).lookahead(Duration.ZERO).build();
                refills = Executors.newVirtualThreadPerTaskExecutor();
                namespace = configuration.getOrDefault("namespace", "spp");
                maxCounters = (int) positive(configuration, "maxCounters", 10_000);
            }
            case "snowflake" -> {
                String machine = configuration.get("machine");
                if (machine == null) throw new IllegalArgumentException("SERIAL_CONFIGURATION_REQUIRED: machine of cosid snowflake mode");
                int id;
                try { id = Integer.parseInt(machine.trim()); }
                catch (NumberFormatException failure) { throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: machine " + machine, failure); }
                if (id < 0 || id >= (1 << MACHINE_BITS))
                    throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: machine " + machine + " is outside 0.." + ((1 << MACHINE_BITS) - 1));
                namespace = configuration.getOrDefault("namespace", "spp");
                var spec = RuntimeSpec.builder(namespace)
                        .machine(MachineSpec.Static.builder(id).bits(MACHINE_BITS).build())
                        .generator(RuntimeSpec.DEFAULT_GENERATOR, GeneratorSpec.Snowflake.builder().build())
                        .build();
                runtime = CosIdRuntime.start(spec, RuntimeOptions.defaults());
                snowflake = runtime.generator(RuntimeSpec.DEFAULT_GENERATOR);
            }
            default -> throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: mode " + mode + " (segment or snowflake)");
        }
    }

    @Override public long next(SerialRequest request) {
        if (snowflake != null) return snowflake.generate();
        return generator(request.counter()).generate();
    }

    private synchronized IdGenerator generator(String counter) {
        IdGenerator existing = generators.get(counter);
        if (existing != null) return existing;
        if (generators.size() >= maxCounters) { // bounded: the least recently used counter (an old date) is dropped
            var eldest = generators.entrySet().iterator();
            ((SegmentGenerator) eldest.next().getValue()).close();
            eldest.remove();
        }
        var created = new SegmentGenerator(namespace, name(counter), ranges, policy, refills, TimeSource.SYSTEM, GeneratorListener.NOOP);
        generators.put(counter, created);
        return created;
    }

    private String name(String counter) {
        int limit = KEY_LIMIT - namespace.length() - 1 - 12;
        if (counter.length() <= limit) return counter;
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(counter.getBytes(StandardCharsets.UTF_8)));
            return counter.substring(0, limit - 17) + "~" + digest.substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public synchronized void close() {
        generators.values().forEach(generator -> ((SegmentGenerator) generator).close());
        generators.clear();
        if (refills != null) refills.close();
        if (store != null) store.close(); // does not close the DataSource it was given
        if (runtime != null) runtime.close();
        refills = null; store = null; runtime = null;
    }

    private static long positive(Map<String, String> configuration, String key, long fallback) {
        String value = configuration.get(key);
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) { /* reported below */ }
        throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: " + key + " must be a positive integer: " + value);
    }
}
