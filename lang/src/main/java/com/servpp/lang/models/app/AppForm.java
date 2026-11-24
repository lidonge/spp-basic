package com.servpp.lang.models.app;

import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppField;

import java.util.*;

/** A layout is resolved against the completed domain, including inherited fields. */
public record AppForm(String name, String role, String code, int length, String header, List<Item> items) {
    public record Item(String name, Integer position, Integer width, Integer count, Integer precision,
                       Integer scale, boolean date, List<Item> children) {}
    public record Slot(Item item, SppField field, int offset, int width, List<Slot> children) {
        public int end() {
            int count = item.count == null ? 1 : item.count;
            return Math.addExact(offset, Math.multiplyExact(width, count));
        }
    }

    public static void validate(SppDomain domain, List<String> errors) {
        Set<String> names = new HashSet<>();
        for (var form : domain.getRuleBlock().getForms()) {
            try {
                if (!names.add(form.name)) throw new IllegalArgumentException("Duplicate binding name");
                form.layout(domain);
            } catch (IllegalArgumentException | ArithmeticException ex) {
                errors.add("Form " + form.name + ": " + ex.getMessage());
            }
        }
    }

    public List<Slot> layout(SppDomain domain) {
        if (!(domain.getSppClass(role) instanceof SppClass type)
                || !Set.of("role", "entity", "contract").contains(type.getType().name()))
            throw new IllegalArgumentException("Unknown participant " + role);
        if (!code.matches("[!-~]+") || code.contains("\\") || code.contains("\""))
            throw new IllegalArgumentException("Expected printable form code");
        List<Slot> slots = new ArrayList<>();
        if (header != null) {
            var shared = domain.getRuleBlock().getFormHeaders().get(header);
            if (shared == null) throw new IllegalArgumentException("Unknown header " + header);
            resolve(shared, type, slots, true);
        }
        resolve(items, type, slots, false);
        if (extent(slots) != length) throw new IllegalArgumentException("Total length mismatch, expected " + length
                + " but fields end at " + extent(slots));
        return List.copyOf(slots);
    }

    private static int extent(List<Slot> slots) {
        return slots.stream().mapToInt(Slot::end).max().orElse(0);
    }

    private void resolve(List<Item> items, SppClass type, List<Slot> slots, boolean headerFields) {
        for (Item item : items) {
            int offset = item.position == null ? extent(slots) : item.position - 1;
            if (offset < 0 || item.count != null && item.count < 1)
                throw new IllegalArgumentException("Invalid position or occurs count for " + item.name);
            int width;
            SppField field = null;
            List<Slot> children = new ArrayList<>();
            if (headerFields && Set.of("code", "length").contains(item.name)) {
                if (item.width == null || item.count != null || item.precision != null || item.date)
                    throw new IllegalArgumentException("Invalid envelope field " + item.name);
                width = item.width;
                if (item.name.equals("code") ? width < code.length() : width != 2 || length > 65535)
                    throw new IllegalArgumentException("Invalid header width for " + item.name);
            } else {
                field = type.getField(item.name);
                if (field == null) throw new IllegalArgumentException("Unknown field " + type.getName() + "." + item.name);
                if (field.getArrayDimension() != (item.count == null ? 0 : 1))
                    throw new IllegalArgumentException("Expected occurs only for array field " + item.name);
                if (item.width == null) {
                    if (!(field.getType() instanceof SppClass child) || child.getType() == null)
                        throw new IllegalArgumentException("Expected structured participant " + item.name);
                    resolve(item.children, child, children, false);
                    width = extent(children);
                } else {
                    width = item.width;
                    checkScalar(item, field);
                }
            }
            if (width < 1) throw new IllegalArgumentException("Invalid width for " + item.name);
            Slot slot = new Slot(item, field, offset, width, List.copyOf(children));
            int end = slot.end();
            for (var prior : slots) {
                if (prior.item.name.equals(item.name)) throw new IllegalArgumentException("Duplicate field " + item.name);
                if (offset < prior.end() && prior.offset < end)
                    throw new IllegalArgumentException("Overlapping fields " + prior.item.name + " and " + item.name);
            }
            slots.add(slot);
        }
    }

    private static void checkScalar(Item item, SppField field) {
        String type = field.getType().getName();
        long minimum;
        if (item.precision != null) {
            int p = item.precision, s = item.scale;
            if (p < 1 || s > p || !Set.of("decimal", "BigDecimal", "short", "int", "long", "BigInteger").contains(type)
                    || field.getNumericAdapter() != null || Set.of("float", "double").contains(field.getJavaType()))
                throw new IllegalArgumentException("Invalid decimal representation for " + item.name);
            var dims = NumericBinding.dimensions(field.getDeclaredNumericSpecification());
            if (dims != null && (p - s < dims[0] - dims[1] || s < dims[1]))
                throw new IllegalArgumentException("Decimal representation too small for " + item.name);
            int digits = switch (type) { case "short" -> 5; case "int" -> 10; case "long" -> 19; default -> 0; };
            if (digits > p - s) throw new IllegalArgumentException("Insufficient integer digits for " + item.name);
            minimum = 1L + Math.max(1, p - s) + (s == 0 ? 0L : 1L + s);
        } else if (item.date && Set.of("String", "DateTime").contains(type)) {
            minimum = 8;
        } else if (!item.date && type.equals("String")) {
            minimum = Objects.requireNonNullElse(field.getDefaultStringLength(), 1);
        } else throw new IllegalArgumentException("Unsupported representation for " + type + " " + item.name);
        if (item.width < minimum) throw new IllegalArgumentException("Width too small for " + item.name);
    }
}
