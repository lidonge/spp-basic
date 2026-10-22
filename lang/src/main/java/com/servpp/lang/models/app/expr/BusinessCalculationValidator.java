package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.models.*;
import com.servpp.lang.models.app.NumericBinding;
import java.util.*;

/** Validates mathematical types without imposing the selected machine representation. */
public final class BusinessCalculationValidator {
    private final SppService service;
    private final List<String> errors = new ArrayList<>();
    private final Deque<Map<String, SppLocalVar>> scopes = new ArrayDeque<>();
    private int loops, switches;
    private enum Kind { NUMBER, BOOLEAN, STRING, CHAR, ENUM, OBJECT, NULL, UNKNOWN }
    public BusinessCalculationValidator(SppService service) { this.service = service; }
    public List<String> validateCondition(IExpression condition) {
        scopes.push(new HashMap<>());
        bool(condition);
        scopes.pop();
        return errors;
    }
    public List<String> validateBoundEndpoint(IExpression expression) {
        scopes.push(new HashMap<>());
        Kind kind = type(expression);
        if (kind != Kind.NUMBER && kind != Kind.NULL) error("Quantum interval endpoint must be a number or null.");
        scopes.pop(); return errors;
    }
    public List<String> validate() {
        if (service.getServiceType() != IConstance.ServiceType.calculate && service.getServiceType() != IConstance.ServiceType.check && !service.isDeclaredQuantumService()) {
            error("Business implementation bodies are only allowed for calculate/check services.");
        }

        scopes.push(new HashMap<>());
        statements(service.getDeclaredJavaStatements());
        return errors;
    }
    private void statements(List<IJavaStatement> statements) {
        for (IJavaStatement statement : statements) {
            if (service.isDeclaredQuantumService() && !(statement instanceof IOperationExpression)
                    && !(statement instanceof CalculationControl) && !(statement instanceof CalculationRule)
                    && (!(statement instanceof RequireStatement constraint) || !constraint.isQuantumConstraint())) {
                error("Quantum service bodies declare quantum attribute updates and check(expression) constraints."); continue;
            }
            if (statement instanceof RequireStatement constraint && constraint.isQuantumConstraint() && !service.isDeclaredQuantumService())
                error("check(expression) constraints belong to quantum service definitions.");
            if (statement instanceof CalculationServiceCall call) { validateCall(call); }
            else if (statement instanceof CalculationControl control) { control(control); }
            else if (statement instanceof RequireStatement require) {
                if (service.getServiceType() != IConstance.ServiceType.check && !service.isDeclaredQuantumService())
                    error("Business validation belongs in an atomic check explicitly called by a scenario; calculate cannot require.");
                bool(require.getCondition());
                if (service.isDeclaredQuantumService()) validateQuantumCondition(require.getCondition());
            }
            else if (statement instanceof CalculationRule rule) {
                bool(rule.getCondition()); scoped(rule.getWhenTrue()); scoped(rule.getWhenFalse());
            } else if (statement instanceof IOperationExpression assignment) {
                if (service.isDeclaredQuantumService()) {
                    var target = assignment.getLeft() instanceof Reference reference ? resolve(reference.getValue()) : null;
                    if (!(target instanceof SppField field) || !field.isQuantum())
                        error("Quantum updates can change only quantum participant attributes.");
                }
                boolean local = assignment.getLeft() instanceof CalculationReference ref && lookupLocal(ref.getValue()) != null && !ref.getValue().contains(".");
                if (service.getServiceType() == IConstance.ServiceType.check && !local) { error("Check services cannot assign business attributes."); }
                Kind left = type(assignment.getLeft()), right = type(assignment.getRight());
                validateEnumLiteral(assignment.getLeft(), assignment.getRight());
                boolean entranceCollection = assignment.getLeft() instanceof Reference ref
                        && service.getServiceBody().getSppLocalVarList().stream().anyMatch(parameter ->
                            parameter.getName().equals(ref.getValue()) && parameter.isParticipantCollection());
                if (!(assignment.getLeft() instanceof Reference reference) || !reference.getValue().contains(".") && !local && !entranceCollection) {
                    error("Assign a participant attribute or collection contents, not a scalar entrance participant.");
                }
                boolean absentNumericFact = left == Kind.NUMBER && right == Kind.NULL
                        && assignment.getLeft() instanceof CalculationReference ref && ref.field instanceof SppField f && !f.isQuantum();
                if (!compatible(left, right) && !absentNumericFact) { error("Assignment type mismatch: " + left + " and " + right); }
                if (left == Kind.OBJECT && right == Kind.OBJECT && assignment.getLeft().getReturnType() != null
                        && assignment.getRight().getReturnType() != null
                        && !assignment.getLeft().getReturnType().getName().equals(assignment.getRight().getReturnType().getName())) {
                    error("Participant assignment requires the same declared object type.");
                }
                if (left == Kind.OBJECT && right == Kind.OBJECT
                        && dimensions(assignment.getLeft()) != dimensions(assignment.getRight())) {
                    error("Participant assignment requires the same collection dimension.");
                }
                String compound = ((IAssignOperation) assignment.getOperation()).getAssocOperation();
                if (compound != null && left != Kind.NUMBER) { error("Compound assignment requires numeric operands."); }
            }
        }
    }
    private void validateCall(CalculationServiceCall call) {
        var unit = call.domain.getSppClass(com.servpp.lang.general.NameUtil.firstToLowerCase(call.name, false));
        if (!(unit instanceof SppService target)) { error("Unknown calculation service " + call.name); return; }
        call.callee = target;
        if (service.getServiceType() != IConstance.ServiceType.calculate || target instanceof SppScenario
                || target.getServiceType() != IConstance.ServiceType.calculate || target.isQuantumService()) {
            error("Calculate can only call calculate: " + call.name); return;
        }
        if (call.implicit && call.arguments.isEmpty()) {
            for (var parameter : target.getServiceBody().getSppLocalVarList()) {
                var matches = service.getServiceBody().getSppLocalVarList().stream()
                        .filter(p -> p.getType().getName().equals(parameter.getType().getName())
                            && p.getArrayDimension() == parameter.getArrayDimension()).toList();
                if (matches.size() != 1) { error("Calculation participant binding missing or ambiguous: " + parameter.getType().getName()); return; }
                call.arguments.add(matches.get(0).getName());
            }
        }
        if (call.arguments.size() != target.getServiceBody().getSppLocalVarList().size()) { error("Calculation participant count mismatch: " + call.name); return; }
        call.resolvedArguments.clear();
        for (int i=0; i<call.arguments.size(); i++) {
            String path = call.arguments.get(i);
            var actual = resolve(path); var expected = target.getServiceBody().getSppLocalVarList().get(i);
            if (actual == null || !actual.getType().getName().equals(expected.getType().getName())
                    || actual.getArrayDimension() != expected.getArrayDimension()) error("Calculation participant mismatch: " + call.arguments.get(i));
            var reference = new CalculationReference(); reference.setValue(path); reference.field = actual;
            reference.local = lookupLocal(path) != null; reference.root = reference.local ? lookupLocal(path) : service.getLocalVar(path.split("\\.")[0]);
            if (actual != null) reference.setReturnType(actual.getType());
            call.resolvedArguments.add(reference);
        }
    }

    private void validateQuantumCondition(IExpression expression) {
        var references = new java.util.ArrayList<Reference>(); collectConstraintReferences(expression, references);
        boolean quantum = false;
        for (var reference : references) {
            var field = resolve(reference.getValue());
            if (field instanceof SppField f && f.isQuantum()) quantum = true;
            else if (field != null && reference.getValue().contains(".")) {
                var root = service.getLocalVar(reference.getValue().split("\\.")[0]);
                if (root == null || !(root.getType().getType() == IConstance.CompilationUnitType.contract
                        || root.getType() instanceof SppClass externalContract
                        && externalContract.getReferenceSourceKind() == IConstance.CompilationUnitType.contract))
                    error("Quantum check can constrain only quantum attributes; thresholds may come from contracts: " + reference.getValue());
            }
        }
        if (!quantum) error("Quantum check must reference a quantum attribute");
    }
    private void collectConstraintReferences(IExpression expression, java.util.List<Reference> result) {
        if (expression instanceof Reference reference) result.add(reference);
        if (expression instanceof IOperationExpression operation) {
            if (operation.getLeft()!=null) collectConstraintReferences(operation.getLeft(), result);
            if (operation.getRight()!=null) collectConstraintReferences(operation.getRight(), result);
        }
        if (expression instanceof ConditionalExpression conditional) {
            collectConstraintReferences(conditional.getCondition(), result);
            collectConstraintReferences(conditional.getWhenTrue(), result);
            collectConstraintReferences(conditional.getWhenFalse(), result);
        }
        if (expression instanceof CalculationFunction function)
            function.getArguments().forEach(arg -> collectConstraintReferences(arg,result));
    }

    private SppLocalVar lookupLocal(String path) {
        String root = path.split("\\.")[0];
        for (var scope : scopes) { if (scope.containsKey(root)) { return scope.get(root); } }
        return null;
    }
    private void declare(SppLocalVar local) {
        if (lookupLocal(local.getName()) != null || service.getLocalVar(local.getName()) != null) {
            error("Duplicate local variable " + local.getName());
        }
        scopes.peek().put(local.getName(), local);
    }
    private void scoped(List<IJavaStatement> body) {
        scopes.push(new HashMap<>()); statements(body); scopes.pop();
    }
    private void control(CalculationControl control) {
        switch (control.kind) {
            case BLOCK -> scoped(control.body);
            case LOCAL -> {
                if (control.condition != null) {
                    Kind actual = type(control.condition);
                    CalculationReference target = new CalculationReference(); target.setReturnType(control.local.getType());
                    validateEnumLiteral(target, control.condition);
                    String name = control.local.getType().getName();
                    Kind expected = control.local.getArrayDimension() > 0 ? Kind.OBJECT : NumericBinding.isNumeric(name) ? Kind.NUMBER : switch (name) {
                        case "boolean" -> Kind.BOOLEAN; case "String" -> Kind.STRING; case "char" -> Kind.CHAR;
                        default -> control.local.getType() instanceof SppEnum ? Kind.ENUM : Kind.OBJECT;
                    };
                    if (!compatible(expected, actual)) { error("Local initializer type mismatch for " + control.local.getName()); }
                    if (expected == Kind.OBJECT && actual == Kind.OBJECT && control.condition.getReturnType() != null
                            && !control.local.getType().getName().equals(control.condition.getReturnType().getName())) {
                        error("Local initializer requires the same participant type.");
                    }
                    if (expected == Kind.OBJECT && actual == Kind.OBJECT
                            && control.local.getArrayDimension() != dimensions(control.condition)) {
                        error("Local initializer requires the same collection dimension.");
                    }
                }
                declare(control.local); statements(control.body);
            }
            case WHILE, FOR, EACH -> {
                scopes.push(new HashMap<>()); statements(control.init);
                if (control.kind == CalculationControl.Kind.EACH) {
                    type(control.condition);
                    SppLocalVar collection = control.condition instanceof CalculationReference ref ? ref.field : null;
                    if (collection == null || collection.getArrayDimension() != 1) {
                        error("Enhanced for requires a one-dimensional participant collection.");
                    } else if (!collection.getType().getName().equals(control.local.getType().getName())
                            && !(NumericBinding.isNumeric(collection.getType().getName()) && NumericBinding.isNumeric(control.local.getType().getName()))) {
                        error("Enhanced for element type mismatch.");
                    }
                    declare(control.local);
                } else if (control.condition != null) { bool(control.condition); }
                loops++; statements(control.body); statements(control.update); loops--;
                scopes.pop();
            }
            case SWITCH -> {
                Kind selector = type(control.condition);
                if (!Set.of(Kind.NUMBER, Kind.STRING, Kind.CHAR, Kind.ENUM).contains(selector)) { error("Unsupported switch selector type."); }
                Set<String> labels = new HashSet<>(); boolean hasDefault = false;
                scopes.push(new HashMap<>()); switches++;
                for (IJavaStatement caseStatement : control.body) {
                    CalculationControl entry = (CalculationControl) caseStatement;
                    if (entry.condition == null) {
                        if (hasDefault) { error("Duplicate switch default."); } hasDefault = true;
                    } else {
                        if (selector == Kind.ENUM && entry.condition instanceof Reference label
                                && control.condition.getReturnType() instanceof SppEnum enumeration) {
                            String labelName = label.getValue();
                            var itemValue = enumeration.getItems().stream().filter(item -> item.getName().equals(labelName)
                                    || (enumeration.getName() + "." + item.getName()).equals(labelName)).findFirst();
                            if (itemValue.isPresent()) {
                                var item = itemValue.get(); Literal value = new Literal();
                                value.setValue("\"" + (item.getValue() == null ? item.getName() : item.getValue()) + "\""); entry.condition = value;
                            }
                        }
                        if (selector == Kind.NUMBER && entry.condition instanceof IOperationExpression sign
                                && sign.getLeft() == null && sign.getRight() instanceof Literal literal
                                && Set.of("+", "-").contains(sign.getOperation().getOperator())) {
                            Literal value = new Literal(); value.setValue(sign.getOperation().getOperator() + literal.getValue()); entry.condition = value;
                        }
                        if (!(entry.condition instanceof Literal) && !(entry.condition instanceof EnumValue)) { error("Switch case requires a literal or enum constant."); }
                        if (!compatible(selector, type(entry.condition))) { error("Switch case type mismatch."); }
                        validateEnumLiteral(control.condition, entry.condition);
                        if (entry.condition instanceof Literal || entry.condition instanceof EnumValue) {
                            String key = entry.condition instanceof Literal literal ? literal.getValue()
                                    : "\"" + ((EnumValue) entry.condition).getValue() + "\"";
                            if (selector == Kind.NUMBER) {
                                try { key = new java.math.BigDecimal(NumericJavaRenderer.normalizeLiteral(key)).stripTrailingZeros().toPlainString(); }
                                catch (NumberFormatException ignored) { }
                            }
                            if (!labels.add(key)) { error("Duplicate switch case " + key); }
                        }
                    }
                    statements(entry.body);
                }
                switches--; scopes.pop();
            }
            case BREAK -> { if (loops == 0 && switches == 0) { error("break requires an enclosing loop or switch."); } }
            case CONTINUE -> { if (loops == 0) { error("continue requires an enclosing loop."); } }
            default -> { }
        }
    }
    private void bool(IExpression value) {
        Kind kind = type(value);
        if (kind == Kind.OBJECT && value instanceof CalculationReference ref
                && ref.field != null && ref.field.getArrayDimension() == 0) return;
        if (kind != Kind.BOOLEAN) error("Rule/require condition must be boolean or an identified scalar scenario participant.");
    }
    private boolean compatible(Kind a, Kind b) {
        return a == b || a == Kind.UNKNOWN || b == Kind.UNKNOWN || a == Kind.ENUM && b == Kind.STRING
                || b == Kind.NULL && (a == Kind.OBJECT || a == Kind.STRING || a == Kind.ENUM);
    }
    private Kind type(IExpression value) {
        if (value instanceof EnumValue) return Kind.ENUM;
        if (value instanceof ParticipantConstruction construction) {
            if (service.isDeclaredQuantumService()) error("Quantum services cannot construct participants.");
            if (service instanceof SppScenario) {
                error("Scenario cannot construct participants or participant collections; create them in an atomic service.");
            }
            for (IExpression element : construction.getElements()) {
                Kind kind=type(element);
                if (kind != Kind.NULL && (kind != Kind.OBJECT || dimensions(element) != 0 || element.getReturnType() == null
                        || !construction.getReturnType().getName().equals(element.getReturnType().getName()))) {
                    error("Participant collection elements require the declared scalar object type.");
                }
            }
            return Kind.OBJECT;
        }
        if (value instanceof Reference reference) {
            if (!(service instanceof SppScenario) && (reference.getValue().startsWith("ResultInfo")
                    || reference.getValue().contains(".resultInfo")))
                error("System result information is only accessible inside scenarios.");
            SppLocalVar field = resolve(reference.getValue());
            if (reference instanceof CalculationReference ref) {
                ref.field = field; ref.root = lookupLocal(reference.getValue()); ref.local = ref.root != null;
                ref.collectionLength = field != null && "$collectionLength".equals(field.getName());
                if (ref.root == null) { ref.root = service.getLocalVar(reference.getValue().split("\\.")[0]); }
            }
            if (field == null || field.getType() == null) { error("Undefined participant attribute " + reference.getValue()); return Kind.UNKNOWN; }
            reference.setReturnType(field.getType());
            if (field.getArrayDimension() != 0) { return Kind.OBJECT; }
            String name = field.getType().getName();
            if (NumericBinding.isNumeric(name)) { return Kind.NUMBER; }
            if (name.equals("boolean")) { return Kind.BOOLEAN; }
            if (name.equals("String")) { return Kind.STRING; }
            if (name.equals("char")) { return Kind.CHAR; }
            if (field.getType() instanceof SppEnum) { return Kind.ENUM; }
            return Kind.OBJECT;
        }
        if (value instanceof Literal literal) {
            String text = literal.getValue();
            if (text.equals("true") || text.equals("false")) { return Kind.BOOLEAN; }
            if (text.equals("null")) { return Kind.NULL; }
            if (text.startsWith("\"")) { return Kind.STRING; }
            if (text.startsWith("'")) { return Kind.CHAR; }
            return Kind.NUMBER;
        }
        if (value instanceof ConditionalExpression conditional) {
            bool(conditional.getCondition());
            Kind a = type(conditional.getWhenTrue()), b = type(conditional.getWhenFalse());
            if (!compatible(a, b) && !compatible(b, a)) { error("Conditional branches must have compatible types."); }
            conditional.setReturnType(conditional.getWhenTrue().getReturnType() != null
                    ? conditional.getWhenTrue().getReturnType() : conditional.getWhenFalse().getReturnType());
            return a == Kind.NULL ? b : a;
        }
        if (value instanceof CalculationFunction function) {
            if (service instanceof SppScenario && Set.of("copy", "append").contains(function.getName())) {
                error("Scenario cannot construct participant facts using " + function.getName() + "; use an atomic service.");
            }
            if (function.getName().equals("append")) {
                if (function.getArguments().size() != 2) { error("append requires a collection and one participant."); return Kind.UNKNOWN; }
                IExpression collection = function.getArguments().get(0), element = function.getArguments().get(1);
                Kind a = type(collection), b = type(element);
                if (a != Kind.OBJECT || b != Kind.OBJECT || dimensions(collection) != 1 || dimensions(element) != 0
                        || !(collection.getReturnType() instanceof SppClass) || collection.getReturnType() instanceof SppService
                        || element.getReturnType() == null || !collection.getReturnType().getName().equals(element.getReturnType().getName())) {
                    error("append requires a one-dimensional collection and a participant of its element type.");
                }
                function.setReturnType(collection.getReturnType()); return Kind.OBJECT;
            }
            if (function.getName().equals("sameFacts")) {
                if (function.getArguments().size() != 2) {
                    error("sameFacts requires two participants or ordered participant collections."); return Kind.UNKNOWN;
                }
                IExpression left = function.getArguments().get(0), right = function.getArguments().get(1);
                Kind leftKind = type(left), rightKind = type(right);
                if (leftKind != Kind.OBJECT || rightKind != Kind.OBJECT
                        || !(left.getReturnType() instanceof SppClass) || left.getReturnType() instanceof SppService
                        || !(right.getReturnType() instanceof SppClass) || right.getReturnType() instanceof SppService
                        || !left.getReturnType().getName().equals(right.getReturnType().getName())
                        || dimensions(left) != dimensions(right)) {
                    error("sameFacts requires the same declared participant type and collection dimensions.");
                }
                return Kind.BOOLEAN;
            }
            if (function.getName().equals("copy")) {
                if (function.getArguments().size() != 1) { error("copy requires one participant or collection argument."); return Kind.UNKNOWN; }
                IExpression source = function.getArguments().get(0);
                Kind kind = type(source);
                if (kind != Kind.OBJECT && kind != Kind.NULL) { error("copy requires a participant or collection."); }
                function.setReturnType(source.getReturnType());
                return kind;
            }
            if (function.getName().equals("addUtcMonths")) {
                if (function.getArguments().size() != 2) error("addUtcMonths requires epoch seconds and calendar months.");
                for (IExpression argument : function.getArguments()) {
                    if (type(argument) != Kind.NUMBER) error("addUtcMonths requires numeric arguments.");
                }
                return Kind.NUMBER;
            }
            if (Set.of("isGregorianDate", "dateYear", "dateMonth", "dateDay", "dateEpochDay").contains(function.getName())) {
                if (function.getArguments().size() != 1) { error(function.getName() + " requires one encoded date."); }
                for (IExpression argument : function.getArguments()) {
                    if (type(argument) != Kind.NUMBER) { error("Gregorian date representation must be numeric."); }
                }
                return function.getName().equals("isGregorianDate") ? Kind.BOOLEAN : Kind.NUMBER;
            }
            if (!Set.of("abs", "min", "max", "roundHalfUp", "roundHalfEven", "roundDown", "roundUp", "floor", "ceil")
                    .contains(function.getName())) { error("Unknown business function " + function.getName()); }
            int expected = Set.of("abs", "floor", "ceil").contains(function.getName()) ? 1 : 2;
            if (function.getArguments().size() != expected) { error(function.getName() + " requires " + expected + " arguments."); }
            for (IExpression argument : function.getArguments()) {
                if (type(argument) != Kind.NUMBER) { error(function.getName() + " requires numeric arguments."); }
            }
            return Kind.NUMBER;
        }
        IOperationExpression operation = (IOperationExpression) value;
        String operator = operation.getOperation().getOperator();
        if (operator.equals("(")) {
            Kind kind = type(operation.getRight());
            ((Expression)value).setReturnType(operation.getRight().getReturnType()); return kind;
        }
        if (operator.equals("!")) { bool(operation.getRight()); return Kind.BOOLEAN; }
        Kind right = type(operation.getRight());
        Kind left = operation.getLeft() == null ? right : type(operation.getLeft());
        if (operator.equals("&&") || operator.equals("||")) {
            bool(operation.getLeft()); bool(operation.getRight());
            return Kind.BOOLEAN;
        }
        if (Set.of("==", "!=").contains(operator)) {
            validateEnumLiteral(operation.getLeft(), operation.getRight());
            validateEnumLiteral(operation.getRight(), operation.getLeft());
            boolean boxedNullComparison = left == Kind.NULL && nullableReference(operation.getRight())
                    || right == Kind.NULL && nullableReference(operation.getLeft());
            if (!boxedNullComparison && !compatible(left, right) && !compatible(right, left)) { error("Equality operands have incompatible types."); }
            return Kind.BOOLEAN;
        }
        if (operator.equals("+") && left == Kind.STRING && right == Kind.STRING) {
            ((Expression)value).setReturnType(operation.getLeft().getReturnType());
            return Kind.STRING;
        }
        if (left != Kind.NUMBER || right != Kind.NUMBER) { error("Operator " + operator + " requires numeric operands."); }
        return Set.of("<", "<=", ">", ">=").contains(operator) ? Kind.BOOLEAN : Kind.NUMBER;
    }
    private boolean nullableReference(IExpression value) {
        if (!(value instanceof Reference reference)) { return false; }
        SppLocalVar field = resolve(reference.getValue());
        return field != null && (reference.getValue().contains(".") || field.getArrayDimension() > 0
                || !Set.of("short", "int", "long", "float", "double", "boolean", "char").contains(field.getJavaType()));
    }

    private int dimensions(IExpression value) {
        if (value instanceof ParticipantConstruction construction) { return construction.isCollection() ? 1 : 0; }
        if (value instanceof Reference reference) {
            SppLocalVar field = resolve(reference.getValue()); return field == null ? 0 : field.getArrayDimension();
        }
        if (value instanceof CalculationFunction function && Set.of("copy", "append").contains(function.getName()) && !function.getArguments().isEmpty()) {
            return dimensions(function.getArguments().get(0));
        }
        if (value instanceof ConditionalExpression conditional) { return dimensions(conditional.getWhenTrue()); }
        return 0;
    }

    private SppLocalVar resolve(String path) {
        String[] parts = path.split("\\.");
        SppLocalVar current = lookupLocal(path);
        if (current == null) { current = service.getLocalVar(parts[0]); }
        if (current == null && parts.length == 1) current = service.getBusinessConstant(path);
        for (int i = 1; i < parts.length && current != null; i++) {
            if (current.getArrayDimension() == 1 && "length".equals(parts[i]) && i == parts.length - 1)
                return new SppLocalVar(new SppCompilationUnit("int"), "$collectionLength");
            current = current.getArrayDimension() == 0 && current.getType() instanceof SppClass cls ? cls.getField(parts[i]) : null;
        }
        return current;
    }
    private void validateEnumLiteral(IExpression reference, IExpression value) {
        if (reference == null || !(reference.getReturnType() instanceof SppEnum enumeration)) { return; }
        if (value != null && value.getReturnType() instanceof SppEnum other && !enumeration.getName().equals(other.getName())) {
            error("Enum type mismatch: " + enumeration.getName() + " and " + other.getName());
            return;
        }
        if (!(value instanceof Literal literal) || !literal.getValue().startsWith("\"")) {
            if (type(value) == Kind.STRING) { error("Enum values require a declared string literal."); }
            return;
        }
        String text = literal.getValue();
        String unquoted = text.substring(1, text.length() - 1);
        if (enumeration.getItems().stream().noneMatch(item -> unquoted.equals(item.getValue() == null ? item.getName() : item.getValue()))) {
            error("Unknown value " + text + " for enum " + enumeration.getName());
        }
    }
    private void error(String message) { errors.add("Business calculation " + service.getName() + ": " + message); }
}
