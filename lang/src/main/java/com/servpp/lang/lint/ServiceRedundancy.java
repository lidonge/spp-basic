package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser.*;
import com.servpp.lang.general.NameUtil;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;
import java.util.function.Consumer;

/** Diagnose service declarations using the participant and primary-key facts in one domain. */
final class ServiceRedundancy {
    private record Service(RedundancyLint.Source source, AtomicserviceContext node) {
        ServiceDefinitionContext definition() { return node.serviceDefinition(); }
        String name() { return definition().Identifier().getText(); }
    }
    private record Field(String participant, String name) { }
    private record Assignment(Field target, ExpressionContext value) { }

    private final Map<String, Map<String, String>> types;
    private final Set<String> roles = new HashSet<>();
    private final Set<String> constants = new HashSet<>();
    private final Map<String, Set<String>> keys = new HashMap<>();
    private final List<ActasContext> takes = new ArrayList<>();
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final RedundancyLint.Reporter reporter;

    private ServiceRedundancy(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        this.reporter = reporter;
        types = LocalRedundancy.typeMaps(sources);
        for (var source : sources) {
            if (source.tree() instanceof AppParser.ProgramContext app) {
                visit(app, AppParser.TableDefineContext.class, this::table);
            } else if (source.tree() instanceof ProgramContext program) {
                for (var unit : program.compilationUnits().compilationUnit()) {
                    if (unit.role() != null && unit.role().getStart().getText().equals("Role"))
                        roles.add(unit.role().Identifier().getText());
                    if (unit.actas() != null) takes.add(unit.actas());
                    if (unit.constantDeclaration() != null) constants.add(unit.constantDeclaration().Identifier().getText());
                    if (unit.atomicservice() != null) {
                        var service = new Service(source, unit.atomicservice());
                        services.put(method(service.name()), service);
                    }
                }
            }
        }
    }

    static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        var domain = new ServiceRedundancy(sources, reporter);
        for (var service : domain.services.values()) {
            domain.implicitCrud(service);
            domain.fieldCopy(service);
        }
        for (var source : sources) if (source.tree() instanceof ProgramContext program)
            for (var unit : program.compilationUnits().compilationUnit()) if (unit.scenario() != null)
                domain.keySetters(source, unit.scenario());
    }

    private void table(AppParser.TableDefineContext table) {
        if (table.identifyList().identifyItem().size() != 1) return;
        String type = table.identifyList().getText();
        for (var key : table.keyDefine()) if (key.primaryKeyDefine() != null) {
            Set<String> names = new HashSet<>();
            for (var item : key.primaryKeyDefine().qualifiedItemList().qualifiedItem()) {
                var parts = item.qualifiedName().Identifier();
                if (parts.size() != 2 || !parts.get(0).getText().equals(type)) return;
                names.add(parts.get(1).getText());
            }
            keys.merge(type, names, (first, next) -> first.equals(next) ? first : Set.of());
        }
    }

    private void implicitCrud(Service service) {
        if (service.node.calculationBody() != null || service.node.quantumService() != null) return;
        var params = parameters(service.definition());
        if (params.size() != 1 || params.get(0).roletype() == null || params.get(0).queryKey() != null) return;
        var role = params.get(0).roletype();
        String type = role.Identifier().getText();
        if (!types.containsKey(type) || !(role.getText().equals(type) || role.getText().equals(type + "[]"))) return;
        String kind = service.node.servicetype().getText();
        String builtin = (kind.equals("query") ? "find" : kind) + type;
        if (Set.of("query", "insert", "update", "delete").contains(kind) && builtin.equals(method(service.name())))
            warn(service, "SPP_REDUNDANT_IMPLICIT_CRUD", "Remove this declaration and use the built-in " + builtin + ".");
    }

    private static List<Assignment> assignments(Service service) {
        var body = service.node.calculationBody();
        if (body == null || service.node.quantumService() != null) return List.of();
        List<Assignment> result = new ArrayList<>();
        for (var statement : body.calculationStatement()) {
            var a = statement.calculationAssignment();
            if (a == null || a.expression() == null || !a.getChild(1).getText().equals("=")) return List.of();
            Field target = field(a.qualifiedName());
            if (target == null) return List.of();
            result.add(new Assignment(target, a.expression()));
        }
        return result;
    }

    private static Field field(QualifiedNameContext path) {
        if (path == null || path.getChildCount() != 3 || path.Identifier().size() != 2) return null;
        return new Field(path.Identifier(0).getText(), path.Identifier(1).getText());
    }

    private static Field field(ExpressionContext value) {
        while (value instanceof ParenthesesExprContext parens) value = parens.expression();
        if (value instanceof PrimExprContext p && p.primaryExpr().primaryExprQualified() != null)
            return field(p.primaryExpr().primaryExprQualified().qualifiedName());
        return null;
    }

    private void fieldCopy(Service service) {
        if (!service.node.servicetype().getText().equals("calculate")
                && !method(service.name()).matches("(prepare|select).*")) return;
        var assignments = assignments(service);
        if (assignments.isEmpty()) return;
        var formals = parameters(service.definition());
        String target = assignments.get(0).target.participant;
        Field first = field(assignments.get(0).value);
        if (first == null || first.participant.equals(target)) return;
        String from = participantType(formals, first.participant), to = participantType(formals, target);
        if (from == null || to == null || from.equals(to)) return;
        // A take targets a Role. For writes from a role to its performer the mapping is reversed.
        boolean reverse = !roles.contains(to);
        String performer = reverse ? to : from, role = reverse ? from : to;
        if (!roles.contains(role)) return;
        Map<String,String> mapping = new LinkedHashMap<>();
        for (var a : assignments) {
            Field rhs = field(a.value);
            if (rhs == null || !target.equals(a.target.participant) || !first.participant.equals(rhs.participant)) return;
            String f = reverse ? rhs.name : a.target.name, g = reverse ? a.target.name : rhs.name;
            String type = types.getOrDefault(role, Map.of()).get(f);
            if (type == null || !type.equals(types.getOrDefault(performer, Map.of()).get(g))
                    || !scalar(type) || mapping.containsValue(g) || mapping.putIfAbsent(f, g) != null) return;
        }
        var existing = takes.stream().filter(t -> t.Identifier().size() == 2 && t.Identifier(0).getText().equals(performer)
                && t.Identifier(1).getText().equals(role)).toList();
        Set<String> common = new HashSet<>(types.get(role).keySet());
        common.retainAll(types.get(performer).keySet());
        boolean same = common.equals(mapping.keySet()) && mapping.entrySet().stream().allMatch(e -> e.getKey().equals(e.getValue()));
        String body = same ? "mapsame;" : String.join(" ", mapping.entrySet().stream()
                .map(e -> base(types.get(role).get(e.getKey())) + " " + e.getKey() + " = " + e.getValue() + ";").toList());
        if (!existing.isEmpty() && (existing.size() != 1 || !mapping.equals(mappedFields(existing.get(0), performer, role)))) return;
        warn(service, "SPP_REDUNDANT_FIELD_COPY", "Use take " + performer + " as " + role + " { " + body
                + " } with a typed copy delegate.");
    }

    private Map<String,String> mappedFields(ActasContext take, String performer, String role) {
        Map<String,String> fields = new LinkedHashMap<>();
        if (take.mapBody() == null) return fields;
        for (var d : take.mapBody().mapDeclaration()) {
            if (d.mapsame() != null) {
                for (String name : types.getOrDefault(role, Map.of()).keySet())
                    if (types.getOrDefault(performer, Map.of()).containsKey(name)) fields.putIfAbsent(name,name);
            } else if (d.mapfield() != null) {
                var ids = d.mapfield().Identifier();
                fields.put(ids.get(0).getText(), ids.get(1).getText());
            } else return Map.of();
        }
        return fields;
    }

    private void keySetters(RedundancyLint.Source source, ScenarioContext scenario) {
        visit(scenario, AtomiccallContext.class, call -> {
            if (call.Identifier() == null || call.scenarioBranch() != null || call.transactionBreak() != null) return;
            var setter = services.get(method(call.Identifier().getText()));
            if (setter == null || !setter.node.servicetype().getText().equals("calculate")) return;
            var parent = call.getParent();
            boolean serial = parent instanceof SerialgroupContext || parent instanceof TransactionStatementsContext
                    || (parent instanceof ExecutegroupContext && scenario.autosort() != null && scenario.autosort().getText().equals("serial"));
            if (!serial) return;
            for (int i = 1; i < parent.getChildCount(); i++)
                if (parent.getChild(i-1) == call && parent.getChild(i) instanceof AtomiccallContext read && read.Identifier() != null)
                    keySetter(source, scenario, setter, call, read);
        });
    }

    private void keySetter(RedundancyLint.Source source, ScenarioContext scenario, Service service,
                           AtomiccallContext call, AtomiccallContext read) {
        String find = method(read.Identifier().getText());
        if (!find.startsWith("find") || read.readBinding() != null || read.scenarioBranch() != null) return;
        var assignments = assignments(service);
        if (assignments.isEmpty()) return;
        var formals = parameters(service.definition());
        String target = assignments.get(0).target.participant, type = participantType(formals, target);
        if (type == null || !keys.containsKey(type)) return;
        String actual = actual(scenario, call, formals, target);
        if (actual == null) return;
        Service query = services.get(find);
        if (query == null) {
            if (!find.equals("find" + type)) return;
            if (read.parameters() == null ? !actual.equals(uniqueActual(scenario, type))
                    : !read.parameters().getText().equals(actual)) return;
        } else {
            var params = parameters(query.definition());
            var selectors = params.stream().filter(p -> params.size() == 1 || p.queryKey() != null).toList();
            if (!query.node.servicetype().getText().equals("query") || selectors.size() != 1) return;
            var selector = selectors.get(0);
            if (selector.roletype() == null || !selector.roletype().getText().equals(type)
                    || !actual.equals(actual(scenario, read, params, name(selector)))) return;
        }
        Set<String> assigned = new HashSet<>();
        List<String> parts = new ArrayList<>();
        Set<String> participants = new LinkedHashSet<>();
        Map<String, Map<String, String>> proposedTakes = new LinkedHashMap<>();
        for (var a : assignments) {
            if (!a.target.participant.equals(target) || !assigned.add(a.target.name)) return;
            Field rhs = field(a.value);
            if (rhs != null) {
                String provider = participantType(formals, rhs.participant);
                String providerActual = actual(scenario, call, formals, rhs.participant);
                if (provider == null || providerActual == null) return;
                var bindings = takes.stream().filter(t -> t.Identifier().size()==2 && t.Identifier(0).getText().equals(provider)
                        && t.Identifier(1).getText().equals(type)).toList();
                if (bindings.size() > 1) return;
                Map<String, String> mapped;
                if (bindings.isEmpty()) {
                    if (!roles.contains(type) || provider.equals(type)) return;
                    mapped = proposedTakes.computeIfAbsent(provider, unused -> new LinkedHashMap<>());
                    String t = types.getOrDefault(type, Map.of()).get(a.target.name);
                    if (t == null || !scalar(t) || !t.equals(types.getOrDefault(provider, Map.of()).get(rhs.name))) return;
                    mapped.put(a.target.name, rhs.name);
                } else {
                    mapped = mappedFields(bindings.get(0), provider, type);
                }
                if (!rhs.name.equals(mapped.get(a.target.name))) return;
                // A selector supplies every mapped key, including any others from this source.
                for (String key : keys.get(type)) if (mapped.containsKey(key) && assignments.stream().noneMatch(other ->
                        other.target.name.equals(key) && new Field(rhs.participant,mapped.get(key)).equals(field(other.value)))) return;
                participants.add(providerActual);
            } else {
                String value = a.value.getText();
                if (!(constants.contains(value) || value.equals("BLANK") || value.matches("-?[0-9]+(\\.[0-9]+)?|true|false|null")
                        || a.value instanceof PrimExprContext literal && literal.primaryExpr().primaryExprLiteral() != null)) return;
                parts.add(actual + "." + a.target.name + " = " + value);
            }
        }
        if (!assigned.equals(keys.get(type))) return;
        parts.addAll(0, participants);
        String args = read.parameters() == null ? "" : "(" + read.parameters().getText() + ")";
        StringBuilder prefix = new StringBuilder();
        for (var take : proposedTakes.entrySet()) {
            prefix.append("Declare take ").append(take.getKey()).append(" as ").append(type).append(" { ");
            take.getValue().forEach((targetField, sourceField) -> prefix.append(base(types.get(type).get(targetField)))
                    .append(' ').append(targetField).append(" = ").append(sourceField).append("; "));
            prefix.append("} first. ");
        }
        reporter.warn(source, call.Identifier().getSymbol(), "SPP_REDUNDANT_KEY_SETTER", prefix + "Replace " + service.name()
                + " and the following read with " + find + args + " by " + String.join(", ", parts) + ";");
    }

    private static String actual(ScenarioContext scenario, AtomiccallContext call, List<ParameterDeclarationContext> params, String name) {
        for (int i=0; i<params.size(); i++) if (name(params.get(i)).equals(name)) {
            if (call.parameters() == null) return uniqueActual(scenario, params.get(i).roletype().getText());
            if (call.parameters().parameter().size() == params.size()) return call.parameters().parameter(i).getText();
        }
        return null;
    }

    private static String uniqueActual(ScenarioContext scenario, String type) {
        var matches = parameters(scenario.serviceDefinition()).stream().filter(p -> p.roletype()!=null
                && p.roletype().getText().equals(type)).toList();
        return matches.size()==1 ? name(matches.get(0)) : null;
    }

    private static List<ParameterDeclarationContext> parameters(ServiceDefinitionContext definition) {
        return definition.parameterDeclarations() == null ? List.of() : definition.parameterDeclarations().parameterDeclaration();
    }
    private static String name(ParameterDeclarationContext p) {
        return p.Identifier() != null ? p.Identifier().getText() : p.roletype().getText();
    }
    private static String participantType(List<ParameterDeclarationContext> params, String name) {
        for (var p : params) if (p.roletype()!=null && name(p).equals(name) && p.roletype().getChildCount()==1)
            return p.roletype().getText();
        return null;
    }
    private static String method(String name) { return NameUtil.firstToLowerCase(name,true); }
    private static String base(String type) { return type.split("\\(",2)[0]; }
    private static boolean scalar(String type) {
        return Set.of("String","boolean","byte","short","int","long","float","double","char","BigDecimal").contains(base(type));
    }
    private void warn(Service s, String code, String message) {
        reporter.warn(s.source, s.definition().Identifier().getSymbol(), code, message);
    }
    private static <T> void visit(ParseTree tree, Class<T> kind, Consumer<T> action) {
        if (kind.isInstance(tree)) action.accept(kind.cast(tree));
        for (int i=0; i<tree.getChildCount(); i++) visit(tree.getChild(i),kind,action);
    }
}
