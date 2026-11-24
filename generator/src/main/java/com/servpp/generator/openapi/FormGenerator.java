package com.servpp.generator.openapi;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.app.AppForm;
import com.servpp.lang.models.app.BlankPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Emits direct model access so the generated Java compiler also checks every field binding. */
final class FormGenerator {
    private final SppDomain domain;
    private final StringBuilder source = new StringBuilder();
    private int indent;

    private FormGenerator(SppDomain domain) { this.domain = domain; }

    static void generate(SppDomain domain, Path root, String basePackage) throws IOException {
        for (AppForm form : domain.getRuleBlock().getForms()) {
            var generator = new FormGenerator(domain);
            String text = generator.binding(form, basePackage + "." + domain.getName());
            var directory = Files.createDirectories(root.resolve("forms"));
            Files.writeString(directory.resolve(form.name() + "Form.java"), text);
        }
    }

    private void line(String text) { source.append("    ".repeat(indent)).append(text).append('\n'); }
    private void open(String text) { line(text.isEmpty() ? "{" : text + " {"); indent++; }
    private void close() { indent--; line("}"); }

    private String binding(AppForm form, String pkg) {
        var slots = form.layout(domain);
        line("package " + pkg + ".forms;");
        line("");
        line("import " + pkg + ".model.*;");
        line("import com.servpp.service.runtime.FormRecord;");
        line("");
        String name = form.name() + "Form";
        open("public final class " + name);
        line("private " + name + "() {}");
        line("public static final String CODE = \"" + form.code() + "\";");
        line("public static final int LENGTH = " + form.length() + ";");
        open("public static byte[] write(" + form.role() + " model)");
        line("var record = FormRecord.create(LENGTH);");
        fields(slots, "model", "0", false, 0);
        line("return record;");
        close();
        open("public static " + form.role() + " read(byte[] record)");
        line("FormRecord.checkLength(record, LENGTH);");
        line("var model = new " + form.role() + "();");
        fields(slots, "model", "0", true, 0);
        line("return model;");
        close();
        close();
        return source.toString();
    }

    private void fields(List<AppForm.Slot> slots, String owner, String base, boolean read, int depth) {
        for (var slot : slots) {
            var item = slot.item();
            String offset = base.equals("0") ? Integer.toString(slot.offset())
                    : slot.offset() == 0 ? base : base + " + " + slot.offset();
            if (slot.field() == null) {
                String call = item.name().equals("length")
                        ? (read ? "checkLengthField" : "putLength") + "(record, " + offset
                        : (read ? "checkCode" : "putText") + "(record, " + offset + ", " + slot.width() + ", CODE";
                line("FormRecord." + call + ");");
                continue;
            }
            String property = slot.field().getUpperName();
            String value = owner + ".get" + property + "()";
            String setter = owner + ".set" + property;
            boolean scope = item.count() != null || !slot.children().isEmpty();
            if (scope) open("");
            if (item.count() != null) {
                String list = "items" + depth;
                String index = "i" + depth;
                if (read) line("var " + list + " = new java.util.ArrayList<" + javaType(slot) + ">();");
                else {
                    line("var " + list + " = " + value + ";");
                    line("FormRecord.checkCount(" + list + ", " + item.count() + ");");
                }
                open("for (int " + index + " = 0; " + index + " < " + item.count() + "; " + index + "++)");
                element(slot, list + ".get(" + index + ")", list + ".add", offset + " + " + index
                        + " * " + slot.width(), read, depth);
                close();
                if (read) line(setter + "(" + list + ");");
            } else element(slot, value, setter, offset, read, depth);
            if (scope) close();
        }
    }

    private static String javaType(AppForm.Slot slot) {
        String type = slot.field().getJavaType();
        return switch (type) {
            case "short", "int" -> "Integer";
            case "long" -> "Long";
            case "decimal", "BigDecimal" -> "java.math.BigDecimal";
            case "BigInteger" -> "java.math.BigInteger";
            case "DateTime" -> "String";
            default -> type;
        };
    }

    private void element(AppForm.Slot slot, String value, String setter, String offset, boolean read, int depth) {
        var item = slot.item();
        if (!slot.children().isEmpty()) {
            String child = "child" + depth;
            line("var " + child + " = " + (read ? "new " + javaType(slot) + "()" : value) + ";");
            fields(slot.children(), child, offset, read, depth + 1);
            if (read) line(setter + "(" + child + ");");
            return;
        }
        String args = "record, " + offset + ", " + slot.width();
        String call;
        if (item.precision() != null) {
            String dimensions = ", " + item.precision() + ", " + item.scale();
            String conversion = switch (javaType(slot)) {
                case "Integer" -> ".intValueExact()";
                case "Long" -> ".longValueExact()";
                case "java.math.BigInteger" -> ".toBigIntegerExact()";
                default -> "";
            };
            call = read ? "decimal(" + args + dimensions + ")" + conversion
                    : "putDecimal(" + args + ", " + value + dimensions + ")";
        } else if (item.date()) {
            call = read ? "date(" + args + ")" : "putDate(" + args + ", " + value + ")";
        } else {
            String blank = BlankPolicy.formBlankJava(domain, slot.field(), null, slot.width());
            call = read ? "text(" + args + ", " + blank + ")" : "putText(" + args + ", " + value + ")";
        }
        String expression = "FormRecord." + call;
        line((read ? setter + "(" + expression + ")" : expression) + ";");
    }
}
