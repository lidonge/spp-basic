package com.servpp.lang.general.app;

import com.servpp.lang.antlr.AppParser;
import java.time.Duration;
import java.util.Locale;

/** Human-readable APP durations are normalized to the existing runtime protocol. */
final class RequestDuration {
    private RequestDuration() { }
    static String value(AppParser.RequestDurationContext context) {
        String value;
        if (context.StringLiteral() != null) {
            String literal = context.StringLiteral().getText();
            value = literal.substring(1, literal.length() - 1);
        } else {
            String number = context.IntegerLiteral().getText();
            if (!number.matches("[0-9]+")) throw new IllegalArgumentException("decimal integer required");
            long amount = Long.parseLong(number);
            value = switch (context.Identifier().getText().toLowerCase(Locale.ROOT)) {
                case "day", "days" -> "P" + amount + "D";
                case "hour", "hours" -> "PT" + amount + "H";
                case "minute", "minutes" -> "PT" + amount + "M";
                case "second", "seconds" -> "PT" + amount + "S";
                case "millisecond", "milliseconds" -> Duration.ofMillis(amount).toString();
                default -> throw new IllegalArgumentException("unsupported duration unit");
            };
        }
        Duration duration = Duration.parse(value);
        if (duration.isZero() || duration.isNegative() || duration.toMillis() < 1)
            throw new IllegalArgumentException("positive duration required");
        return value;
    }
}
