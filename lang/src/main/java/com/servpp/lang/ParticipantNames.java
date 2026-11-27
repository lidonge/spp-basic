package com.servpp.lang;

import com.servpp.lang.antlr.SppParser.*;
import com.servpp.lang.general.NameUtil;
import java.util.*;
import java.util.function.Consumer;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/** Domain-wide, source-level names for service parameters. */
public final class ParticipantNames {
    public static final String EXPLICIT = "SPP_EXPLICIT_PARAMETER_NAME";
    public static final String COLLISION = "SPP_PARAMETER_SHORT_NAME_COLLISION";

    public record Shape(String type, int dimensions, boolean value) {
        public String legacy() {
            return NameUtil.participantName(type, dimensions);
        }
    }

    public record ShortNames(String scalar, String array) {
        public String forShape(int dimensions) {
            if (dimensions == 0) return scalar;
            return (array == null ? scalar + "s" : array) + "s".repeat(dimensions - 1);
        }
    }

    public record Problem(Token token, String code, String message) { }

    private final Map<String, Map<String, ShortNames>> domains = new HashMap<>();

    public static String domain(ProgramContext tree) {
        return tree.servicedomain().domainname().getText();
    }

    public void add(ProgramContext tree) {
        var types = domains.computeIfAbsent(domain(tree), unused -> new HashMap<>());
        for (var unit : tree.compilationUnits().compilationUnit()) {
            var entity = unit.entity();
            var role = unit.role();
            if (entity == null && role == null) continue;
            var names = entity == null ? role.shortNames() : entity.shortNames();
            if (names == null) continue;
            String type = (entity == null ? role.Identifier() : entity.Identifier()).getText();
            types.put(type, new ShortNames(names.Identifier(0).getText(),
                    names.Identifier().size() == 2 ? names.Identifier(1).getText() : null));
        }
    }

    public static Shape shape(ParameterDeclarationContext parameter) {
        var type = parameter.type();
        if (type == null) {
            var role = parameter.roletype();
            return new Shape(role.Identifier().getText(), (role.getChildCount() - 1) / 2, false);
        }
        if (type.quantumNumber() != null)
            return new Shape(type.quantumNumber().numberType().getText(), 0, true);
        return new Shape(type.primitiveType().getText(), (type.getText().length() - type.getText().replace("[]", "").length()) / 2, true);
    }

    public static ShortNames defaults(String type, boolean value) {
        int prefix = 1;
        while (prefix < type.length() && Character.isUpperCase(type.charAt(prefix))) prefix++;
        if (prefix > 1 && prefix < type.length() && Character.isLowerCase(type.charAt(prefix))) prefix--;
        String scalar = type.substring(0, prefix).toLowerCase(Locale.ROOT) + type.substring(prefix);
        if (value) scalar += "Value";
        return new ShortNames(scalar, null);
    }

    public String name(String domain, Shape shape) {
        var names = domains.getOrDefault(domain, Map.of()).get(shape.type());
        return (names == null ? defaults(shape.type(), shape.value()) : names).forShape(shape.dimensions());
    }

    public static String bindingName(ParameterDeclarationContext parameter) {
        if (parameter.bindingName != null) return parameter.bindingName;
        if (parameter.Identifier() != null) return parameter.Identifier().getText();
        return parameter.roletype() == null ? parameter.type().getText() : parameter.roletype().getText();
    }

    public static ServiceDefinitionContext definition(CompilationUnitContext unit) {
        if (unit.scenario() != null) return unit.scenario().serviceDefinition();
        return unit.atomicservice() == null ? null : unit.atomicservice().serviceDefinition();
    }

    public static List<ParameterDeclarationContext> parameters(ServiceDefinitionContext definition) {
        return definition.parameterDeclarations() == null ? List.of()
                : definition.parameterDeclarations().parameterDeclaration();
    }

    public void check(ProgramContext tree, Consumer<Problem> reporter) {
        String domain = domain(tree);
        for (var unit : tree.compilationUnits().compilationUnit()) {
            var service = definition(unit);
            if (service == null) continue;
            Set<String> names = new HashSet<>();
            for (var parameter : parameters(service)) {
                var shape = shape(parameter);
                String name = name(domain, shape);
                if (parameter.Identifier() != null)
                    reporter.accept(new Problem(parameter.Identifier().getSymbol(), EXPLICIT,
                            "Declare only the parameter type. Its short name is '" + name + "'."));
                if (!names.add(name))
                    reporter.accept(new Problem(parameter.getStart(), COLLISION,
                            "Service " + service.Identifier().getText() + " has more than one parameter named '" + name + "'."));
            }
        }
    }

    /**
     * Keep historical default symbols in generated Java and accept a type spelling
     * as a source alias when it designates exactly one entrance parameter.
     */
    public void bind(ProgramContext tree) {
        String domain = domain(tree);
        for (var unit : tree.compilationUnits().compilationUnit()) {
            var definition = definition(unit);
            if (definition == null) continue;
            var parameters = parameters(definition);
            Map<String, String> bindings = new HashMap<>();
            Set<String> symbols = new HashSet<>();
            for (var parameter : parameters) {
                var shape = shape(parameter);
                String name = name(domain, shape);
                String symbol = parameter.Identifier() != null ? parameter.Identifier().getText()
                        : shape.value() || domains.getOrDefault(domain, Map.of()).containsKey(shape.type())
                                ? name : shape.legacy();
                parameter.bindingName = symbol;
                parameter.wireName = parameter.Identifier() == null ? name : symbol;
                symbols.add(symbol);
                bindings.put(parameter.Identifier() == null ? name : symbol, symbol);
            }
            if (symbols.size() != parameters.size()) {
                for (var parameter : parameters) {
                    if (parameter.Identifier() == null) parameter.bindingName = name(domain, shape(parameter));
                }
                bindings.replaceAll((name, unused) -> name);
            }
            for (var parameter : parameters) {
                var shape = shape(parameter);
                if (parameter.Identifier() != null || shape.value()) continue;
                String alias = shape.type() + "[]".repeat(shape.dimensions());
                bindings.putIfAbsent(alias, parameter.bindingName);
            }
            // A short name takes precedence over a conflicting legacy spelling.
            ParseTree body = unit.scenario() != null ? unit.scenario().scenarioDeclaration()
                    : unit.atomicservice().calculationBody();
            references(body, (path, root, brackets) -> {
                String spelling = root.getText() + "[]".repeat(brackets.size() / 2);
                String symbol = bindings.get(spelling);
                if (symbol != null && !shadowed(path, root.getText())) {
                    ((CommonToken) root).setText(symbol);
                    brackets.forEach(token -> ((CommonToken) token).setText(""));
                }
            });
        }
    }

    @FunctionalInterface
    public interface ReferenceVisitor {
        void visit(ParserRuleContext path, Token root, List<Token> brackets);
    }

    public static void references(ParseTree tree, ReferenceVisitor visitor) {
        if (tree == null) return;
        if (tree instanceof ReadKeyContext key && key.Identifier() != null) {
            visitor.visit(key, key.Identifier().getSymbol(), List.of());
            return;
        }
        if (tree instanceof QualifiedNameContext path && !path.Identifier().isEmpty()) {
            Token root = path.Identifier(0).getSymbol();
            List<Token> brackets = new ArrayList<>();
            for (int index = 1; index < path.getChildCount(); index++) {
                if (!(path.getChild(index) instanceof org.antlr.v4.runtime.tree.TerminalNode terminal)
                        || !(terminal.getText().equals("[") || terminal.getText().equals("]"))) break;
                brackets.add(terminal.getSymbol());
            }
            visitor.visit(path, root, brackets);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) references(tree.getChild(index), visitor);
    }

    public static boolean shadowed(ParserRuleContext use, String name) {
        for (ParserRuleContext child = use, parent = use.getParent(); parent != null;
                child = parent, parent = parent.getParent()) {
            if (parent instanceof CalculationBodyContext || parent instanceof CalculationCaseContext) {
                for (int index = 0; index < parent.getChildCount() && parent.getChild(index) != child; index++) {
                    if (parent.getChild(index) instanceof CalculationStatementContext statement
                            && declares(statement.calculationLocal(), name)) return true;
                }
            } else if (parent instanceof CalculationStatementContext statement
                    && statement.getStart().getText().equals("for") && child != statement.calculationLocal()
                    && declares(statement.calculationLocal(), name)) return true;
        }
        return false;
    }

    private static boolean declares(CalculationLocalContext local, String name) {
        if (local == null) return false;
        var identifiers = local.Identifier();
        return identifiers.stream().skip(local.primitiveType() == null ? 1 : 0)
                .anyMatch(identifier -> identifier.getText().equals(name));
    }
}
