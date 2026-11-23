package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppConstant;
import com.servpp.lang.models.SppLocalVar;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** Infers result scales from SPP types, independently of PHY bindings. */
final class NumericRoundingWarnings {
    private record Scale(Long places, boolean arithmetic, boolean division) {}

    private final Function<String, SppLocalVar> resolve;

    NumericRoundingWarnings(Function<String, SppLocalVar> resolve) { this.resolve = resolve; }

    boolean needsRounding(SppLocalVar target, IExpression value, String compound) {
        if (target.getDeclaredRoundingMode() != null) return false;
        Integer targetScale = target.getDeclaredNumericScale();
        Scale result = scale(value);
        if (compound != null && !compound.isBlank())
            result = arithmetic(compound, declaredScale(target), result);
        return result.division || (targetScale != null && result.arithmetic
                && (result.places == null || result.places > targetScale));
    }

    private Scale scale(IExpression value) {
        if (value instanceof Literal literal) return literalScale(literal.getValue());
        if (value instanceof Reference reference) {
            var field = resolve.apply(reference.getValue());
            if (field instanceof SppConstant constant) return literalScale(constant.getLiteral());
            if (field != null) return declaredScale(field);
        }
        if (value instanceof ConditionalExpression choice)
            return join(scale(choice.getWhenTrue()), scale(choice.getWhenFalse()));
        if (value instanceof CalculationFunction function) return functionScale(function);
        if (value instanceof IOperationExpression operation) {
            Scale right = scale(operation.getRight());
            if (operation.getLeft() == null || "(".equals(operation.getOperation().getOperator())) return right;
            return arithmetic(operation.getOperation().getOperator(), scale(operation.getLeft()), right);
        }
        return new Scale(null, false, false);
    }

    private static Scale declaredScale(SppLocalVar field) {
        Integer places = field.getDeclaredNumericScale();
        return new Scale(places == null ? null : places.longValue(), false, false);
    }

    private static Scale literalScale(String text) {
        try {
            BigDecimal value = new BigDecimal(NumericJavaRenderer.normalizeLiteral(text));
            return new Scale((long) value.stripTrailingZeros().scale(), false, false);
        } catch (NumberFormatException invalidLiteral) {
            return new Scale(null, false, false);
        }
    }

    private static Scale arithmetic(String operator, Scale left, Scale right) {
        Scale joined = join(left, right);
        return switch (operator) {
            case "/" -> new Scale(null, true, true);
            case "*" -> new Scale(left.places == null || right.places == null ? null : left.places + right.places,
                    true, joined.division);
            case "+", "-" -> new Scale(joined.places, true, joined.division);
            default -> joined;
        };
    }

    private static Scale join(Scale left, Scale right) {
        return new Scale(left.places == null || right.places == null ? null : Math.max(left.places, right.places),
                left.arithmetic || right.arithmetic, left.division || right.division);
    }

    private Scale functionScale(CalculationFunction function) {
        List<IExpression> args = function.getArguments();
        return switch (function.getName()) {
            case "floor", "ceil", "parseUnsignedInteger", "addUtcMonths", "dateYear", "dateMonth", "dateDay", "dateEpochDay"
                    -> new Scale(0L, false, false);
            case "roundDown", "roundUp", "roundHalfUp", "roundHalfEven" -> new Scale(
                    args.size() == 2 ? roundingPlaces(args.get(1)) : null, false, false);
            case "abs" -> args.size() == 1 ? scale(args.get(0)) : new Scale(null, false, false);
            case "min", "max" -> args.size() == 2 ? join(scale(args.get(0)), scale(args.get(1))) : new Scale(null, false, false);
            default -> new Scale(null, false, false);
        };
    }

    private Long roundingPlaces(IExpression expression) {
        String text = null;
        if (expression instanceof Literal literal) text = literal.getValue();
        if (expression instanceof Reference reference && resolve.apply(reference.getValue()) instanceof SppConstant constant)
            text = constant.getLiteral();
        if (text != null) {
            try {
                return (long) new BigDecimal(NumericJavaRenderer.normalizeLiteral(text)).intValueExact();
            } catch (IllegalArgumentException | ArithmeticException invalidPlaces) {
                return null;
            }
        }
        return null;
    }
}
