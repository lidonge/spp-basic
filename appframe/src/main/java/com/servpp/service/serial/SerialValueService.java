package com.servpp.service.serial;

import com.servpp.service.runtime.PhysicalRuntimeCatalog;
import com.servpp.service.technical.FieldValueContext;
import com.servpp.service.technical.FieldValueInterceptor;
import com.servpp.service.technical.RecordInterceptors;
import com.servpp.service.transaction.BusinessTransactionRejection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built-in technical service behind {@code auto Entity.field serial rule}. It is an ordinary
 * {@link FieldValueInterceptor}: the generated binding carries the APP rule ({@code rule}, {@code scope}, {@code min},
 * {@code max}, {@code wrap}, {@code format}, {@code gapless}) and the engine binding ({@code engine.class},
 * {@code engine.config.*}).
 */
public final class SerialValueService implements FieldValueInterceptor {
    private static final Pattern PART = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}");
    private String rule, format;
    private List<String> scope;
    private long min, max;
    private boolean wrap, gapless;
    private SerialEngine engine;

    @Override public void configure(Map<String, String> configuration) {
        rule = require(configuration, "rule");
        String scopeText = configuration.getOrDefault("scope", "");
        scope = scopeText.isBlank() ? List.of() : List.of(scopeText.split(","));
        min = Long.parseLong(configuration.getOrDefault("min", "1"));
        max = Long.parseLong(configuration.getOrDefault("max", Long.toString(Long.MAX_VALUE)));
        wrap = Boolean.parseBoolean(configuration.getOrDefault("wrap", "false"));
        gapless = Boolean.parseBoolean(configuration.getOrDefault("gapless", "false"));
        format = configuration.get("format");
        var engineConfig = new LinkedHashMap<String, String>();
        configuration.forEach((key, value) -> { if (key.startsWith("engine.config.")) engineConfig.put(key.substring("engine.config.".length()), value); });
        engine = (SerialEngine) RecordInterceptors.instance(new RecordInterceptors.Binding(
                require(configuration, "engine.name"), require(configuration, "engine.class"), engineConfig), SerialEngine.class);
        if (gapless && !(engine instanceof GaplessSerialEngine))
            throw new IllegalArgumentException("SERIAL_GAPLESS_UNSUPPORTED: engine " + configuration.get("engine.name") + " of rule " + rule);
        if (max < min) throw new IllegalArgumentException("SERIAL_RANGE_INVALID: " + rule);
    }

    @Override public Object produce(FieldValueContext context) {
        var values = new LinkedHashMap<String, Object>();
        Optional<LocalDate> date = Optional.empty();
        var key = new StringBuilder(rule);
        for (String field : scope) {
            Object value = context.value(field);
            if (value == null || value.toString().isBlank()) throw new BusinessTransactionRejection("SERIAL_SCOPE_VALUE_REQUIRED");
            // A date-time scope value resets per day: the counter is keyed by the calendar date.
            if (value instanceof java.time.temporal.TemporalAccessor moment && moment.isSupported(java.time.temporal.ChronoField.EPOCH_DAY)) {
                LocalDate day = LocalDate.from(moment);
                date = Optional.of(day);
                value = day;
            }
            values.put(field, value);
            key.append('/').append(escape(value.toString()));
        }
        long raw = engine.next(new SerialRequest(rule, key.toString(), java.util.Collections.unmodifiableMap(values), date, min, max,
                gapless, context.connection()));
        if (raw < 1) throw new IllegalStateException("SERIAL_ENGINE_VALUE_INVALID: " + raw);
        long size = max - min + 1;
        long offset = raw - 1;
        if (size > 0 && offset >= size) {
            if (!wrap) throw new BusinessTransactionRejection("SERIAL_EXHAUSTED");
            offset %= size;
        }
        long number = min + offset;
        return format == null ? (Object) number : render(format, number, values);
    }

    static String render(String pattern, long number, Map<String, Object> scope) {
        Matcher matcher = PART.matcher(pattern);
        var out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(pattern, last, matcher.start());
            String name = matcher.group(1), argument = matcher.group(2);
            if (name.equals("seq")) {
                String digits = Long.toString(number);
                int width = argument == null || argument.isBlank() ? 0 : Integer.parseInt(argument);
                out.append("0".repeat(Math.max(0, width - digits.length()))).append(digits);
            } else {
                Object value = scope.get(name);
                if (value instanceof LocalDate day && argument != null) out.append(day.format(DateTimeFormatter.ofPattern(argument)));
                else out.append(value);
            }
            last = matcher.end();
        }
        return out.append(pattern.substring(last)).toString();
    }
    /** The counter key of {@code rule} for the given scope values, as engines and seeded test stores see it. */
    public static String counterKey(String rule, Object... scopeValues) {
        var key = new StringBuilder(rule);
        for (Object value : scopeValues) key.append('/').append(escape(value.toString()));
        return key.toString();
    }
    private static String escape(String text) {
        var out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128 || c == '.' || c == '-' || c == '_') out.append(c);
            else out.append('~').append(String.format("%04x", (int) c));
        }
        return out.toString();
    }
    private static String require(Map<String, String> configuration, String key) {
        String value = configuration.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("SERIAL_CONFIGURATION_REQUIRED: " + key);
        return value;
    }
}
