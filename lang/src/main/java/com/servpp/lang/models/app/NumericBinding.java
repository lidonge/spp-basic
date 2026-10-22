package com.servpp.lang.models.app;

import java.util.Set;

/** Physical representation; never replaces the business declaration. */
public record NumericBinding(String target, String representation, String adapter) {
    public NumericBinding(String target, String representation) { this(target, representation, null); }
    public static boolean isNumeric(String type) {
        return Set.of("short", "int", "long", "float", "double", "decimal", "BigDecimal", "BigInteger", "java.math.BigDecimal", "java.math.BigInteger").contains(base(type));
    }

    public String javaType() {
        dimensions(representation);
        if (adapter != null) {
            if (!isQualifiedJavaName(adapter) || !isQualifiedJavaName(representation)) {
                throw new IllegalArgumentException("Custom PHY numeric type and adapter must be fully qualified Java class names.");
            }
            return representation;
        }
        return switch (base(representation)) {
            case "BigDecimal", "java.math.BigDecimal", "decimal" -> "java.math.BigDecimal";
            case "BigInteger", "java.math.BigInteger" -> "java.math.BigInteger";
            case "short", "int", "long", "float", "double" -> representation;
            default -> throw new IllegalArgumentException("Unsupported PHY numeric representation " + representation);
        };
    }
    public static String base(String specification) {
        return specification.split("\\(", 2)[0];
    }
    public static int[] dimensions(String specification) {
        if (!specification.contains("(")) return null;
        if (!specification.matches("(?:BigDecimal|java\\.math\\.BigDecimal|decimal)\\([0-9]+,[0-9]+\\)"))
            throw new IllegalArgumentException("Only decimal numeric defaults accept (precision, scale): " + specification);
        String[] values = specification.substring(specification.indexOf('(') + 1, specification.length() - 1).split(",");
        int precision = Integer.parseInt(values[0]), scale = Integer.parseInt(values[1]);
        if (precision < 1 || scale < 0 || scale > precision)
            throw new IllegalArgumentException("Invalid numeric precision/scale: " + specification);
        return new int[]{precision, scale};
    }
    public static String canonical(String specification) {
        String base = base(specification);
        if (base.equals("decimal") || base.equals("java.math.BigDecimal")) base = "BigDecimal";
        if (base.equals("java.math.BigInteger")) base = "BigInteger";
        return base + (specification.contains("(") ? specification.substring(specification.indexOf('(')) : "");
    }
    public boolean isGlobal() { return isNumeric(target); }
    public boolean matches(String original) {
        return isGlobal() && (target.contains("(") ? canonical(target).equals(canonical(original))
                : canonical(target).equals(canonical(base(original))));
    }
    private static boolean isQualifiedJavaName(String value) {
        return value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    }
}
