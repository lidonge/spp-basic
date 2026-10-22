package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppField;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.app.NumericBinding;

/** Lowers the original mathematical AST using the selected physical field types. */
public final class NumericJavaRenderer {
    private static final String NUMBERS = "com.servpp.service.numeric.ExactNumbers";
    private final SppService service;
    private int temporary;

    public NumericJavaRenderer(SppService service) { this.service = service; }

    public String statement(IJavaStatement statement) {
        if (statement instanceof CalculationServiceCall call) {
            String type = call.callee.getBasePackage() + "." + call.callee.getDomainName() + ".handler." + call.callee.getName();
            String args = call.resolvedArguments.stream().map(reference -> {
                String value = expression(reference);
                if (reference.field != null && reference.field.isParticipantCollection())
                    return "com.servpp.service.runtime.ParticipantCollections.list(" + value + ")";
                if (reference.field != null && reference.field.getArrayDimension() == 1 && reference.getValue().contains("."))
                    return "(" + reference.field.getType().getPackageName() + "." + reference.field.getJavaType()
                            + "[]) com.servpp.service.runtime.ParticipantCollections.array(" + value + ", "
                            + reference.field.getType().getPackageName() + "." + reference.field.getJavaType() + ".class)";
                return value;
            }).collect(java.util.stream.Collectors.joining(","));
            return "((" + type + ") com.servpp.service.server.DefaultServiceContainer.getInstance().getService(\""
                    + service.getDomainName() + "\", \"" + call.name + "\"))." + call.name + "(" + args + ");";
        }
        if (statement instanceof CalculationControl control) { return control(control); }
        if (statement instanceof CalculationRule rule) {
            String result = "if (" + condition(rule.getCondition()) + ") {\n" + statements(rule.getWhenTrue()) + "}";
            if (!rule.getWhenFalse().isEmpty()) { result += " else {\n" + statements(rule.getWhenFalse()) + "}"; }
            return result;
        }
        if (statement instanceof RequireStatement require) {
            if (service.isDeclaredQuantumService()) return "com.servpp.service.transaction.QuantumConstraintProvider.check("
                    + condition(require.getCondition()) + ", " + require.getMessage() + ");";
            return "if (!(" + condition(require.getCondition()) + ")) { com.servpp.service.runtime.BusinessChecks.fail("
                    + require.getMessage() + "); return; }";
        }
        if (statement instanceof IOperationExpression operation) { return expression(operation) + ";"; }
        return statement.getJavaStatement();
    }

    public String boundEndpoint(IExpression expression, SppLocalVar field) {
        if (isNull(expression)) return "null";
        if (expression instanceof ConditionalExpression choice)
            return "(" + condition(choice.getCondition()) + " ? " + boundEndpoint(choice.getWhenTrue(), field)
                    + " : " + boundEndpoint(choice.getWhenFalse(), field) + ")";
        String value = field.getNumericAdapter() == null ? decimal(expression) : adapted(expression, field.getNumericAdapter());
        if (expression instanceof CalculationReference reference && reference.field instanceof SppField)
            return "(" + reference.getExpr() + " == null ? null : " + value + ")";
        return value;
    }
    public String boundAdapter(SppLocalVar field) {
        return field.getNumericAdapter() == null ? "null" : adapterCall(field.getNumericAdapter());
    }
    public String condition(IExpression expression) {
        if (expression instanceof IOperationExpression brackets && "(".equals(brackets.getOperation().getOperator()))
            return "(" + condition(brackets.getRight()) + ")";
        if (expression instanceof CalculationReference ref && ref.field != null && ref.field.getArrayDimension() == 0
                && ref.field.getType() instanceof com.servpp.lang.models.SppClass
                && ref.field.getType().getType() != null) {
            return "com.servpp.service.runtime.ParticipantIdentity.isIdentified(" + expression(expression) + ")";
        }
        return expression(expression);
    }

    private String temporary(String prefix) {
        String result;
        do { result = prefix + temporary++; } while (service.getLocalVar(result) != null);
        return result;
    }
    private String referenceName(Reference reference) {
        return reference instanceof CalculationReference ref && ref.local ? "$sppLocal_" + reference.getValue() : reference.getValue();
    }
    private String localType(SppLocalVar local) {
        if ("char".equals(local.getType().getName())) { return "String" + local.getArrayString(); }
        if ("boolean".equals(local.getType().getName()) || "String".equals(local.getType().getName())) {
            return local.getJavaType() + local.getArrayString();
        }
        String type = local.getJavaType();
        if ((local.getType() instanceof SppClass || local.getType() instanceof com.servpp.lang.models.SppEnum) && local.getType().getBasePackage() != null
                && !NumericBinding.isNumeric(local.getType().getName())) {
            type = local.getType().getPackageName() + "." + type;
        }
        return type + local.getArrayString();
    }
    private String declaration(CalculationControl control) {
        String result = localType(control.local) + " $sppLocal_" + control.local.getName();
        if (control.condition == null) { return result; }
        CalculationReference target = new CalculationReference(); target.setValue(control.local.getName());
        target.local = true; target.field = control.local; target.root = control.local; target.setReturnType(control.local.getType());
        AssignExpression assignment = new AssignExpression(); AssignOperation operator = new AssignOperation();
        operator.setType(IOperation.Opers_Type.Double); assignment.setOperation(operator);
        assignment.setLeft(target); assignment.setRight(control.condition);
        return localType(control.local) + " " + expression(assignment);
    }
    private String header(java.util.List<IJavaStatement> statements) {
        return statements.stream().map(item -> {
            if (!(item instanceof CalculationControl c)) { return expression((IExpression)item); }
            String result = declaration(c);
            for (var child : c.body) {
                CalculationControl variable = (CalculationControl)child;
                result += ", " + declaration(variable).substring(localType(variable.local).length() + 1);
            }
            return result;
        }).collect(java.util.stream.Collectors.joining(", "));
    }
    private String control(CalculationControl control) {
        return switch (control.kind) {
            case BLOCK -> "{\n" + statements(control.body) + "}";
            case LOCAL -> declaration(control) + ";\n" + statements(control.body);
            case WHILE -> "while (" + expression(control.condition) + ") {\n" + statements(control.body) + "}";
            case FOR -> "{ for (" + header(control.init) + "; "
                    + (control.condition == null ? "true" : expression(control.condition)) + "; "
                    + header(control.update) + ") {\n" + statements(control.body) + "} }";
            case EACH -> each(control);
            case SWITCH -> switchStatement(control);
            case BREAK -> "break;";
            case CONTINUE -> "continue;";
            case EMPTY -> ";";
            default -> throw new IllegalArgumentException("Unexpected business control " + control.kind);
        };
    }
    private String each(CalculationControl control) {
        String element = temporary("$sppElement");
        String value = element;
        if (NumericBinding.isNumeric(control.local.getType().getName())) {
            value = convertNumber(element, resolve((Reference)control.condition), control.local);
            if ("short".equals(control.local.getJavaType())) { value = "(short) (" + value + ")"; }
        } else if ("char".equals(control.local.getType().getName())) { value = "String.valueOf(" + element + ")"; }
        return "for (var " + element + " : " + expression(control.condition) + ") {\n"
                + localType(control.local) + " $sppLocal_" + control.local.getName() + " = " + value + ";\n"
                + statements(control.body) + "}";
    }

    private String switchStatement(CalculationControl control) {
        String selectorName = temporary("$sppSelector"), caseName = temporary("$sppCase");
        String adapter = findAdapter(control.condition);
        boolean number = numeric(control.condition);
        String selected = number ? (adapter == null ? decimal(control.condition) : adapted(control.condition, adapter))
                : "java.util.Objects.requireNonNull(" + expression(control.condition) + ")";
        SppLocalVar field = new SppLocalVar(control.condition.getReturnType() == null
                ? new SppClass("double") : control.condition.getReturnType(), selectorName);
        field.setPhysicalJavaType(number && adapter == null ? "java.math.BigDecimal" : "String");
        field.setNumericAdapter(adapter);
        CalculationReference reference = new CalculationReference(); reference.setValue(selectorName); reference.field = field;
        reference.setReturnType(field.getType());
        StringBuilder result = new StringBuilder("{\nvar " + selectorName + " = " + selected + ";\nint " + caseName + " = -1;\n");
        int index = 0, defaultIndex = -1;
        for (IJavaStatement item : control.body) {
            CalculationControl entry = (CalculationControl)item;
            if (entry.condition == null) { defaultIndex = index; }
            else {
                OperationExpression comparison = new OperationExpression(); LogicOperation operator = new LogicOperation();
                operator.setOperator("=="); operator.setType(IOperation.Opers_Type.Double);
                comparison.setOperation(operator); comparison.setLeft(reference); comparison.setRight(entry.condition);
                result.append("if (").append(caseName).append(" == -1 && (").append(expression(comparison))
                        .append(")) ").append(caseName).append(" = ").append(index).append(";\n");
            }
            index++;
        }
        if (defaultIndex >= 0) { result.append("if (").append(caseName).append(" == -1) ").append(caseName).append(" = ").append(defaultIndex).append(";\n"); }
        result.append("switch (").append(caseName).append(") {\n"); index = 0;
        for (IJavaStatement item : control.body) {
            result.append("case ").append(index++).append(":\n").append(statements(((CalculationControl)item).body));
        }
        return result.append("}\n}").toString();
    }

    public String expression(IExpression expression) {
        if (expression instanceof CalculationReference reference && reference.field instanceof com.servpp.lang.models.SppConstant constant)
            return constant.getJavaValue();
        if (expression instanceof ParticipantConstruction construction) {
            return "new " + construction.getJavaType() + (construction.isCollection()
                    ? "[] {" + construction.getElements().stream().map(this::expression).collect(java.util.stream.Collectors.joining(", ")) + "}"
                    : "()");
        }
        // OpenAPI models represent business char fields as strings. Primitive parameters stay char.
        if (service.hasBusinessImplementation() && expression instanceof Literal literal && literal.getValue().startsWith("'")) {
            return "String.valueOf(" + literal.getValue() + ")";
        }
        if (service.hasBusinessImplementation() && expression instanceof Reference reference
                && reference.getReturnType() != null && "char".equals(reference.getReturnType().getName())
                && !reference.getValue().contains(".")) {
            return "String.valueOf(" + reference.getExpr() + ")";
        }
        if (expression instanceof ConditionalExpression conditional) {
            if (numeric(conditional)) { return decimal(conditional); }
            return "(" + condition(conditional.getCondition()) + " ? " + expression(conditional.getWhenTrue())
                    + " : " + expression(conditional.getWhenFalse()) + ")";
        }
        if (expression instanceof CalculationFunction function) {
            if (function.getName().equals("append")) {
                return "com.servpp.service.runtime.ParticipantCollections.append(" + expression(function.getArguments().get(0))
                        + ", " + expression(function.getArguments().get(1)) + ")";
            }
            if (function.getName().equals("sameFacts")) { return factsComparison(function); }
            if (function.getName().equals("copy")) {
                return "com.servpp.service.runtime.ParticipantCopies.copy(" + expression(function.getArguments().get(0)) + ")";
            }
            if (function.getName().equals("isGregorianDate")) { return function(function, null); }
            String adapter = findAdapter(function);
            return adapter == null ? decimal(function) : adapted(function, adapter);
        }
        if (expression instanceof IOperationExpression operation) {
            String operator = operation.getOperation().getOperator();
            IExpression left = operation.getLeft(), right = operation.getRight();
            if (operation.getOperation() instanceof IAssignOperation assignment && numeric(left)) {
                if (!(left instanceof Reference reference)) {
                    throw new IllegalArgumentException("PHY numeric assignment requires a field or parameter target.");
                }
                if (isNull(right) && assignment.getAssocOperation() == null && reference.getValue().contains("."))
                    return NameUtil.getNameCall(referenceName(reference), true) + "(null)";
                String adapter = resolve(reference).getNumericAdapter();
                if (adapter == null) { adapter = findAdapter(right); }
                String value = adapter == null ? decimal(right) : adapted(right, adapter);
                String compound = assignment.getAssocOperation();
                if (compound != null && !compound.isBlank()) {
                    value = adapter == null ? arithmetic(compound, decimal(left), value)
                            : adapterCall(adapter) + "." + arithmeticMethod(compound) + "(" + adapted(left, adapter) + ", " + value + ")";
                }
                String targetAdapter = resolve(reference).getNumericAdapter();
                String converted = targetAdapter == null
                        ? convertSpecification(adapter == null ? value : adapterDecimal(value, adapter), resolve(reference))
                        : value;
                if (reference.getValue().contains(".") && "short".equals(resolve(reference).getJavaType())) {
                    converted = "(int) (" + converted + ")";
                }
                return reference.getValue().contains(".")
                        ? NameUtil.getNameCall(referenceName(reference), true) + "(" + converted + ")"
                        : referenceName(reference) + " = " + converted;
            }
            if (operation.getOperation() instanceof IAssignOperation && left instanceof Reference reference) {
                if (entranceCollection(reference))
                    return "com.servpp.service.runtime.ParticipantFacts.hydrateCollection(" + expression(left)
                            + ", com.servpp.service.runtime.ParticipantCollections.list(" + expression(right) + "))";
                String value = right instanceof Literal && left.getReturnType() instanceof com.servpp.lang.models.SppEnum
                        ? new AssignOperation().getRightExpr(left, right) : expression(right);
                if (resolve(reference) != null && resolve(reference).getArrayDimension() == 1
                        && reference.getValue().contains(".") && rootArray(right)) {
                    value = "com.servpp.service.runtime.ParticipantCollections.list(" + value + ")";
                } else if (resolve(reference) != null && resolve(reference).getArrayDimension() == 1
                        && !reference.getValue().contains(".") && modelCollection(right)) {
                    String arrayType=localType(resolve(reference));
                    String componentType=arrayType.substring(0,arrayType.length()-2);
                    value="(" + arrayType + ") com.servpp.service.runtime.ParticipantCollections.array(" + value + ", " + componentType + ".class)";
                }
                if (right instanceof Reference && reference.getValue().contains(".") && resolve(reference) != null && resolve(reference).getArrayDimension() == 0
                        && resolve(reference).getType() instanceof SppClass && resolve(reference).getType().getType() != null)
                    value = "com.servpp.service.runtime.ParticipantFacts.assign(" + expression(left) + ", " + value + ")";
                if (operation.getOperation() instanceof IAssignOperation assignment
                        && assignment.getAssocOperation() != null && !assignment.getAssocOperation().isBlank()) {
                    value = "(" + expression(left) + " " + assignment.getAssocOperation() + " " + value + ")";
                }
                return reference.getValue().contains(".")
                        ? NameUtil.getNameCall(referenceName(reference), true) + "(" + value + ")"
                        : referenceName(reference) + " = " + value;
            }
            if (isComparison(operator) && numeric(left) && numeric(right)) {
                String adapter = findAdapter(left);
                if (adapter == null) { adapter = findAdapter(right); }
                if (adapter != null) {
                    return adapterCall(adapter) + ".compare(" + adapted(left, adapter) + ", " + adapted(right, adapter) + ") " + operator + " 0";
                }
                return "(" + decimal(left) + ").compareTo(" + decimal(right) + ") " + operator + " 0";
            }
            if (numeric(operation)) { return decimal(operation); }
            if ("(".equals(operator)) { return "(" + expression(right) + ")"; }
            if ("&&".equals(operator) || "||".equals(operator)) {
                return "(" + condition(left) + ") " + operator + " (" + condition(right) + ")";
            }
            if ("!".equals(operator)) { return "!(" + condition(right) + ")"; }
            if ("+".equals(operator) && (!numeric(left) || !numeric(right))) {
                return "(" + expression(left) + " + " + expression(right) + ")";
            }
            if (isComparison(operator) && (isNull(left) || isNull(right))) {
                return "(" + expression(left) + " " + operator + " " + expression(right) + ")";
            }
            if (("==".equals(operator) || "!=".equals(operator)) && left != null && right != null
                    && left.getReturnType() instanceof SppClass participant && participant.getType() != null
                    && right.getReturnType() instanceof SppClass other && other.getType() != null) {
                String same = participant.getIdentityPaths().isEmpty()
                        ? factsComparison(new CalculationFunction("sameFacts", java.util.List.of(left, right)))
                        : "com.servpp.service.runtime.ParticipantFacts.sameIdentity("+expression(left)+", "+expression(right)+", java.util.List.of("
                            +participant.getIdentityPaths().stream().map(path -> "\""+path+"\"").collect(java.util.stream.Collectors.joining(", "))+"))";
                return "!=".equals(operator) ? "!(" + same + ")" : same;
            }
            if ("==".equals(operator) || "!=".equals(operator)) {
                String a = expression(left), b = expression(right);
                if (left instanceof Reference && left.getReturnType() instanceof com.servpp.lang.models.SppEnum && right instanceof Literal) {
                    b = new AssignOperation().getRightExpr(left, right);
                } else if (right instanceof Reference && right.getReturnType() instanceof com.servpp.lang.models.SppEnum && left instanceof Literal) {
                    a = new AssignOperation().getRightExpr(right, left);
                }
                if (!valueEquality(left) && !valueEquality(right)) { return "(" + a + " " + operator + " " + b + ")"; }
                return ("!=".equals(operator) ? "!" : "") + "java.util.Objects.equals(" + a + ", " + b + ")";
            }
            if (containsNumeric(operation) && !"=".equals(operator)) {
                throw new IllegalArgumentException("PHY numeric operator " + operator + " has no exact implementation.");
            }
        }
        return expression.getExpr();
    }

    private String decimal(IExpression expression) {
        if (!numeric(expression)) { throw new IllegalArgumentException("PHY numeric operand must be a number."); }
        if (expression instanceof Reference reference) {
            String value = reference.getExpr();
            if (resolve(reference).getNumericAdapter() != null) { return adapterDecimal(value, resolve(reference).getNumericAdapter()); }
            return "java.math.BigDecimal".equals(resolve(reference).getJavaType())
                    ? "(" + value + ")" : NUMBERS + ".decimal(" + value + ")";
        }
        if (expression instanceof Literal literal) {
            return "new java.math.BigDecimal(\"" + normalizeLiteral(literal.getValue()) + "\")";
        }
        if (expression instanceof ConditionalExpression conditional) {
            return "(" + condition(conditional.getCondition()) + " ? " + decimal(conditional.getWhenTrue())
                    + " : " + decimal(conditional.getWhenFalse()) + ")";
        }
        if (expression instanceof CalculationFunction function) { return function(function, null); }
        IOperationExpression operation = (IOperationExpression) expression;
        String operator = operation.getOperation().getOperator();
        if ("(".equals(operator)) { return "(" + decimal(operation.getRight()) + ")"; }
        if (operation.getLeft() == null && ("+".equals(operator) || "-".equals(operator))) {
            String value = decimal(operation.getRight());
            return "-".equals(operator) ? "(" + value + ").negate()" : value;
        }
        return arithmetic(operator, decimal(operation.getLeft()), decimal(operation.getRight()));
    }

    private String arithmetic(String operator, String left, String right) {
        String method = arithmeticMethod(operator);
        return "(" + left + ")." + method + "(" + right + ")";
    }

    private String arithmeticMethod(String operator) {
        return switch (operator) {
            case "+" -> "add";
            case "-" -> "subtract";
            case "*" -> "multiply";
            case "/" -> "divide";
            case "%" -> "remainder";
            default -> throw new IllegalArgumentException("PHY numeric operator " + operator + " has no exact implementation.");
        };
    }

    public static String convertNumber(String value, String targetType) {
        String result = convert(NUMBERS + ".decimal(" + value + ")", targetType);
        return "short".equals(targetType) ? "(int) (" + result + ")" : result;
    }

    public static String convertNumber(String value, com.servpp.lang.models.SppParameter source,
                                       com.servpp.lang.models.SppParameter target) {
        String sourceAdapter = source.getNumericAdapter();
        String targetAdapter = target.getNumericAdapter();
        if (targetAdapter != null) {
            return "com.servpp.service.numeric.NumericAdapters.get(" + targetAdapter + ".class).convert("
                    + (sourceAdapter == null ? NUMBERS + ".decimal(" + value + ")" : value)
                    + ", com.servpp.service.numeric.NumericAdapters.get("
                    + (sourceAdapter == null ? "com.servpp.service.numeric.DecimalNumericAdapter" : sourceAdapter) + ".class))";
        }
        String decimal = sourceAdapter == null ? NUMBERS + ".decimal(" + value + ")"
                : "com.servpp.service.numeric.NumericAdapters.toDecimal(" + value
                    + ", com.servpp.service.numeric.NumericAdapters.get(" + sourceAdapter + ".class))";
        String result = convertSpecification(decimal, target);
        return "short".equals(target.getJavaType()) ? "(int) (" + result + ")" : result;
    }

    private static String convertSpecification(String value, com.servpp.lang.models.SppParameter field) {
        String spec = field.getPhysicalNumericSpecification();
        int[] dimensions = spec == null ? null : NumericBinding.dimensions(spec);
        if (dimensions != null) value = NUMBERS + ".specification(" + value + ", " + dimensions[0] + ", " + dimensions[1] + ")";
        return convert(value, field.getJavaType());
    }

    private static String convert(String value, String type) {
        return switch (type) {
            case "java.math.BigDecimal", "decimal" -> value;
            case "java.math.BigInteger" -> "(" + value + ").toBigIntegerExact()";
            case "short", "int", "long" -> "(" + value + ")." + type + "ValueExact()";
            case "float", "double" -> NUMBERS + "." + type + "Exact(" + value + ")";
            default -> throw new IllegalArgumentException("Unsupported PHY numeric target " + type);
        };
    }

    private boolean numeric(IExpression expression) {
        if (expression instanceof CalculationFunction function) { return !java.util.Set.of("copy", "append", "isGregorianDate", "sameFacts").contains(function.getName()); }
        if (expression instanceof ConditionalExpression conditional) {
            return numeric(conditional.getWhenTrue()) && numeric(conditional.getWhenFalse());
        }
        if (expression instanceof Reference reference) {
            SppLocalVar field = resolve(reference);
            return field != null && field.getArrayDimension() == 0 && NumericBinding.isNumeric(field.getType().getName());
        }
        if (expression instanceof Literal literal) {
            try { normalizeLiteral(literal.getValue()); return true; }
            catch (NumberFormatException e) { return false; }
        }
        if (expression instanceof IOperationExpression operation) {
            String operator = operation.getOperation().getOperator();
            if ("(".equals(operator)) { return numeric(operation.getRight()); }
            return java.util.Set.of("+", "-", "*", "/", "%").contains(operator)
                    && (operation.getLeft() == null || numeric(operation.getLeft())) && numeric(operation.getRight());
        }
        return false;
    }

    private String statements(java.util.List<IJavaStatement> statements) {
        return statements.stream().map(this::statement).collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    }

    private String adapterCall(String adapter) { return "com.servpp.service.numeric.NumericAdapters.get(" + adapter + ".class)"; }
    private String adapterDecimal(String value, String adapter) {
        return "com.servpp.service.numeric.NumericAdapters.toDecimal(" + value + ", " + adapterCall(adapter) + ")";
    }
    private String findAdapter(IExpression expression) {
        if (expression instanceof Reference reference) { return resolve(reference) == null ? null : resolve(reference).getNumericAdapter(); }
        if (expression instanceof CalculationFunction function) {
            for (IExpression argument : function.getArguments()) { String found = findAdapter(argument); if (found != null) { return found; } }
        }
        if (expression instanceof ConditionalExpression conditional) {
            String found = findAdapter(conditional.getWhenTrue()); return found == null ? findAdapter(conditional.getWhenFalse()) : found;
        }
        if (expression instanceof IOperationExpression operation) {
            String found = findAdapter(operation.getLeft()); return found == null ? findAdapter(operation.getRight()) : found;
        }
        return null;
    }
    private String adapted(IExpression expression, String adapter) {
        String call = adapterCall(adapter);
        if (expression instanceof Reference reference) {
            String sourceAdapter = resolve(reference).getNumericAdapter();
            if (adapter.equals(sourceAdapter)) { return reference.getExpr(); }
            return call + ".convert(" + (sourceAdapter == null ? decimal(reference) : reference.getExpr()) + ", "
                    + adapterCall(sourceAdapter == null ? "com.servpp.service.numeric.DecimalNumericAdapter" : sourceAdapter) + ")";
        }
        if (expression instanceof Literal literal) { return call + ".literal(\"" + normalizeLiteral(literal.getValue()) + "\")"; }
        if (expression instanceof ConditionalExpression conditional) {
            return "(" + condition(conditional.getCondition()) + " ? " + adapted(conditional.getWhenTrue(), adapter)
                    + " : " + adapted(conditional.getWhenFalse(), adapter) + ")";
        }
        if (expression instanceof CalculationFunction function) { return function(function, adapter); }
        IOperationExpression operation = (IOperationExpression) expression;
        String operator = operation.getOperation().getOperator();
        if (operator.equals("(")) { return "(" + adapted(operation.getRight(), adapter) + ")"; }
        if (operation.getLeft() == null) {
            String right = adapted(operation.getRight(), adapter);
            return operator.equals("-") ? call + ".negate(" + right + ")" : right;
        }
        return call + "." + arithmeticMethod(operator) + "(" + adapted(operation.getLeft(), adapter) + ", " + adapted(operation.getRight(), adapter) + ")";
    }

    private String function(CalculationFunction function, String adapter) {
        var args = function.getArguments();
        String name = function.getName();
        if (name.equals("addUtcMonths")) {
            if (args.size() != 2) throw new IllegalArgumentException("addUtcMonths requires two arguments.");
            String value = "com.servpp.service.calendar.GregorianDates.addUtcMonths(" + decimal(args.get(0)) + ", " + decimal(args.get(1)) + ")";
            return adapter == null ? value : adapterCall(adapter) + ".literal((" + value + ").toPlainString())";
        }
        if (java.util.Set.of("isGregorianDate", "dateYear", "dateMonth", "dateDay", "dateEpochDay").contains(name)) {
            if (args.size() != 1) { throw new IllegalArgumentException(name + " requires one encoded date."); }
            String value = "com.servpp.service.calendar.GregorianDates." + name + "(" + decimal(args.get(0)) + ")";
            return adapter == null || name.equals("isGregorianDate") ? value
                    : adapterCall(adapter) + ".literal((" + value + ").toPlainString())";
        }
        int expected = java.util.Set.of("abs", "floor", "ceil").contains(name) ? 1 : 2;
        if (args.size() != expected) { throw new IllegalArgumentException(name + " requires " + expected + " arguments."); }
        java.util.function.Function<IExpression, String> render = value -> adapter == null ? decimal(value) : adapted(value, adapter);
        String value = render.apply(args.get(0));
        String call = adapterCall(adapter == null ? "com.servpp.service.numeric.DecimalNumericAdapter" : adapter);
        if (name.equals("abs")) { return call + ".abs(" + value + ")"; }
        if (name.equals("min") || name.equals("max")) { return call + "." + name + "(" + value + ", " + render.apply(args.get(1)) + ")"; }
        String mode = switch (name) {
            case "roundHalfUp" -> "HALF_UP";
            case "roundHalfEven" -> "HALF_EVEN";
            case "roundDown" -> "DOWN";
            case "roundUp" -> "UP";
            case "floor" -> "FLOOR";
            case "ceil" -> "CEILING";
            default -> throw new IllegalArgumentException("Unknown business function " + name);
        };
        String places = args.size() == 1 ? "0" : "(" + decimal(args.get(1)) + ").intValueExact()";
        IExpression input = args.get(0);
        while (input instanceof IOperationExpression brackets && brackets.getOperation().getOperator().equals("(")) { input = brackets.getRight(); }
        if (input instanceof IOperationExpression division && division.getOperation().getOperator().equals("/")) {
            return call + ".divideRounded(" + render.apply(division.getLeft()) + ", " + render.apply(division.getRight())
                    + ", " + places + ", java.math.RoundingMode." + mode + ")";
        }
        return call + ".round(" + value + ", " + places + ", java.math.RoundingMode." + mode + ")";
    }

    private String factsComparison(CalculationFunction function) {
        var arguments = function.getArguments();
        if (arguments.size() != 2 || !(arguments.get(0).getReturnType() instanceof SppClass model)) {
            throw new IllegalArgumentException("sameFacts requires two typed participants.");
        }
        java.util.Map<String, SppClass> types = new java.util.LinkedHashMap<>();
        collectFactTypes(model, types);
        String runtime = "com.servpp.service.runtime.ParticipantFacts";
        java.util.List<String> schemas = new java.util.ArrayList<>();
        for (SppClass type : types.values()) {
            java.util.List<String> fields = new java.util.ArrayList<>();
            for (var field : type.getSppFieldList()) {
                if (field.isSyntheticRolePerformer()) { continue; }
                String getter = "get" + NameUtil.firstToLowerCase(field.getName(), false);
                fields.add("new " + runtime + ".Field(\"" + getter + "\""
                        + (field.getNumericAdapter() == null ? "" : ", " + adapterCall(field.getNumericAdapter())) + ")");
            }
            schemas.add("new " + runtime + ".Type(\"" + type.getName() + "\", java.util.List.of(" + String.join(", ", fields) + "))");
        }
        return runtime + ".same(" + expression(arguments.get(0)) + ", " + expression(arguments.get(1))
                + ", " + String.join(", ", schemas) + ")";
    }

    private void collectFactTypes(SppClass type, java.util.Map<String, SppClass> result) {
        if (result.putIfAbsent(type.getName(), type) != null) { return; }
        for (var field : type.getSppFieldList()) {
            if (!field.isSyntheticRolePerformer() && field.getNumericAdapter() == null
                    && field.getType() instanceof SppClass nested
                    && !(nested instanceof SppService)) {
                collectFactTypes(nested, result);
            }
        }
    }

    private boolean containsNumeric(IExpression expression) {
        if (numeric(expression)) { return true; }
        if (expression instanceof IOperationExpression operation) {
            return containsNumeric(operation.getLeft()) || containsNumeric(operation.getRight());
        }
        return false;
    }

    private boolean isNull(IExpression expression) {
        return expression instanceof Literal literal && "null".equals(literal.getValue());
    }

    private boolean valueEquality(IExpression expression) {
        if (expression instanceof Literal literal) {
            return literal.getValue().startsWith("\"") || literal.getValue().startsWith("'")
                    || literal.getValue().equals("true") || literal.getValue().equals("false");
        }
        return expression != null && expression.getReturnType() != null
                && (expression.getReturnType() instanceof com.servpp.lang.models.SppEnum
                    || java.util.Set.of("String", "char", "boolean").contains(expression.getReturnType().getName()));
    }

    private boolean isComparison(String operator) {
        return java.util.Set.of("<", "<=", ">", ">=", "==", "!=").contains(operator);
    }

    private boolean entranceCollection(Reference reference) {
        return !reference.getValue().contains(".") && !(reference instanceof CalculationReference ref && ref.local)
                && service.getServiceBody().getSppLocalVarList().stream()
                    .anyMatch(parameter -> parameter.getName().equals(reference.getValue()) && parameter.isParticipantCollection());
    }
    private boolean modelCollection(IExpression value) {
        if (value instanceof CalculationFunction function && function.getName().equals("append")) { return true; }
        if (value instanceof CalculationFunction function && function.getName().equals("copy")) {
            return modelCollection(function.getArguments().get(0));
        }
        return value instanceof Reference reference && (reference.getValue().contains(".") || entranceCollection(reference))
                && resolve(reference) != null && resolve(reference).getArrayDimension() == 1;
    }
    private boolean rootArray(IExpression value) {
        if (value instanceof ParticipantConstruction construction) { return construction.isCollection(); }
        if (value instanceof CalculationFunction function && function.getName().equals("copy")) {
            return rootArray(function.getArguments().get(0));
        }
        return value instanceof Reference reference && !reference.getValue().contains(".") && !entranceCollection(reference)
                && resolve(reference) != null && resolve(reference).getArrayDimension() == 1;
    }

    private SppLocalVar resolve(Reference reference) {
        if (reference instanceof CalculationReference ref && ref.field != null) { return ref.field; }
        String[] path = reference.getValue().split("\\.");
        SppLocalVar current = service.getServiceBody().getSppLocalVarList().stream()
                .filter(parameter -> parameter.getName().equals(path[0])).findFirst().orElse(null);
        for (int i = 1; i < path.length && current != null; i++) {
            current = current.getType() instanceof SppClass cls ? cls.getField(path[i]) : null;
        }
        return current;
    }

    static String normalizeLiteral(String source) {
        if (source.startsWith("-") || source.startsWith("+")) {
            String value = normalizeLiteral(source.substring(1));
            return source.startsWith("-") ? new java.math.BigDecimal(value).negate().toString() : value;
        }
        String value = source.replace("_", "");
        if (value.matches("(?i)0x[0-9a-f]+[l]?")) {
            return new java.math.BigInteger(value.replaceAll("[lL]$", "").substring(2), 16).toString();
        }
        if (value.matches("(?i)0b[01]+[l]?")) {
            return new java.math.BigInteger(value.replaceAll("[lL]$", "").substring(2), 2).toString();
        }
        if (value.matches("0[0-7]+[lL]?")) {
            return new java.math.BigInteger(value.replaceAll("[lL]$", ""), 8).toString();
        }
        value = value.replaceAll("[fFdDlL]$", "");
        return new java.math.BigDecimal(value).toString();
    }
}
