package com.servpp.service.runtime;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Byte operations shared by generated APP form bindings. Positions here are zero based. */
public final class FormRecord {
    private FormRecord() {}

    public static byte[] create(int length) {
        byte[] record = new byte[length];
        Arrays.fill(record, (byte) ' ');
        return record;
    }

    public static void checkLength(byte[] record, int length) {
        if (record.length != length) throw new IllegalArgumentException("Expected " + length + " form bytes");
    }

    public static void putLength(byte[] record, int offset) {
        ByteBuffer.wrap(record).putShort(offset, (short) record.length);
    }

    public static void checkLengthField(byte[] record, int offset) {
        checkLength(record, Short.toUnsignedInt(ByteBuffer.wrap(record).getShort(offset)));
    }

    public static void putText(byte[] record, int offset, int width, String value) {
        if (value == null) return;
        try {
            var bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            if (bytes.remaining() > width) throw new IllegalArgumentException("Text exceeds width at byte " + offset);
            bytes.get(record, offset, bytes.remaining());
        } catch (CharacterCodingException ex) { throw new IllegalArgumentException("Invalid text at byte " + offset, ex); }
    }

    public static String text(byte[] record, int offset, int width, String blank) {
        int end = offset + width;
        while (end > offset && record[end - 1] == ' ') end--;
        if (end == offset) return blank;
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(record, offset, end - offset)).toString();
        } catch (CharacterCodingException ex) { throw new IllegalArgumentException("Invalid UTF-8 at byte " + offset, ex); }
    }

    public static void checkCode(byte[] record, int offset, int width, String code) {
        if (!code.equals(text(record, offset, width, ""))) throw new IllegalArgumentException("Expected form " + code);
    }

    private static BigDecimal exact(BigDecimal value, int precision, int scale) {
        var scaled = value.setScale(scale);
        if (scaled.precision() > precision) throw new IllegalArgumentException("Form decimal precision exceeded");
        return scaled;
    }

    public static void putDecimal(byte[] record, int offset, int width, Number value, int precision, int scale) {
        String number = exact(new BigDecimal(value.toString()), precision, scale).toPlainString();
        putText(record, offset + width - number.length(), number.length(), number);
    }

    public static BigDecimal decimal(byte[] record, int offset, int width, int precision, int scale) {
        String number = text(record, offset, width, "").stripLeading();
        if (!number.matches("-?[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("Expected decimal digits at byte " + offset);
        var parsed = new BigDecimal(number);
        if (!parsed.toPlainString().equals(number) || parsed.scale() != scale)
            throw new IllegalArgumentException("Expected fixed-point decimal at byte " + offset);
        return exact(parsed, precision, scale);
    }

    public static void putDate(byte[] record, int offset, int width, String isoDate) {
        var date = LocalDate.parse(isoDate);
        putText(record, offset, width, DateTimeFormatter.BASIC_ISO_DATE.format(date));
    }

    public static String date(byte[] record, int offset, int width) {
        String digits = text(record, offset, width, "");
        if (digits.length() != 8) throw new IllegalArgumentException("Expected eight date digits at byte " + offset);
        return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE).toString();
    }

    public static void checkCount(List<?> items, int count) {
        if (items == null || items.size() != count) throw new IllegalArgumentException("Expected " + count + " form items");
    }
}
