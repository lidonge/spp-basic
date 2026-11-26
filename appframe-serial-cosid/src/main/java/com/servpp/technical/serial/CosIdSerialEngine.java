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
import javax.sql.DataSource;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributorFactory;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

/**
 * {@link SerialEngine} over the open-source CosId library (Apache-2.0). It has no special standing: it is bound
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
 * <li>{@code mode}: {@code segment} (default) keeps one CosId {@link DefaultSegmentId} per counter over the JDBC
 * segment distributor (table {@code cosid}); {@code snowflake} keeps one CosId {@link MillisecondSnowflakeId} (no
 * per-scope order, globally unique).</li>
 * <li>segment: {@code step} (default 100), {@code namespace} (default {@code spp}), {@code initTable} (default
 * {@code true}: create the CosId table), {@code datasourceUrl} (required), {@code driverClassName},
 * {@code username}, {@code password}, {@code maxCounters} (default 10000 live generators).</li>
 * <li>snowflake: {@code machine} (required, 0..1023).</li>
 * </ul>
 * A segment counter pre-allocates {@code step} numbers, so a restart skips the unused rest of a segment: this engine
 * is not gapless. A scope that carries a date gets one CosId name per date, which is the daily reset. (CosId's own
 * grouped reset follows the wall clock; a business date is data, so it is not used.)
 */
public final class CosIdSerialEngine implements SerialEngine, AutoCloseable {
    private static final int NAME_LIMIT = 90; // CosId stores "{namespace}.{name}" in varchar(100)
    private IdGenerator snowflake;
    private JdbcIdSegmentDistributorFactory factory;
    private String namespace;
    private long step;
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
                var initializer = new JdbcIdSegmentInitializer(dataSource);
                if (Boolean.parseBoolean(configuration.getOrDefault("initTable", "true"))) initializer.tryInitCosIdTable();
                factory = new JdbcIdSegmentDistributorFactory(dataSource, true, initializer,
                        JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, Duration.ofSeconds(10));
                step = positive(configuration, "step", 100);
                namespace = configuration.getOrDefault("namespace", "spp");
                maxCounters = (int) positive(configuration, "maxCounters", 10_000);
            }
            case "snowflake" -> {
                String machine = configuration.get("machine");
                if (machine == null) throw new IllegalArgumentException("SERIAL_CONFIGURATION_REQUIRED: machine of cosid snowflake mode");
                int id;
                try { id = Integer.parseInt(machine.trim()); }
                catch (NumberFormatException failure) { throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: machine " + machine, failure); }
                if (id < 0 || id >= (1 << MillisecondSnowflakeId.DEFAULT_MACHINE_BIT))
                    throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: machine " + machine + " is outside 0.." + ((1 << MillisecondSnowflakeId.DEFAULT_MACHINE_BIT) - 1));
                snowflake = new MillisecondSnowflakeId(id);
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
            eldest.next();
            eldest.remove();
        }
        var distributor = factory.create(new IdSegmentDistributorDefinition(namespace, name(counter), 0, step));
        var created = new DefaultSegmentId(distributor);
        generators.put(counter, created);
        return created;
    }

    private static String name(String counter) {
        if (counter.length() <= NAME_LIMIT - 12) return counter;
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(counter.getBytes(StandardCharsets.UTF_8)));
            return counter.substring(0, NAME_LIMIT - 12 - 17) + "~" + digest.substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public synchronized void close() { generators.clear(); }

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
