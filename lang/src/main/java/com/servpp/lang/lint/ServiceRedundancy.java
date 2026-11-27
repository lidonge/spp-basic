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
    private final Set<String> contracts = new HashSet<>();
    private final Map<String, Set<String>> repositoryInputs = new HashMap<>();
    private final Map<String, Set<String>> repositoryFields = new HashMap<>();
    private final Set<String> roles = new HashSet<>();
    private final Set<String> constants = new HashSet<>();
    private final Map<String, Set<String>> keys = new HashMap<>();
    private final List<ActasContext> takes = new ArrayList<>();
    private final Map<String, Integer> callCounts = new HashMap<>();
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final RedundancyLint.Reporter reporter;

    private ServiceRedundancy(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        this.reporter = reporter;
        types = LocalRedundancy.typeMaps(sources);
        for (var source : sources) {
            if (source.tree() instanceof AppParser.ProgramContext app) {
                visit(app, AppParser.TableDefineContext.class, this::table);
                visit(app, AppParser.ScopeItemContext.class, item -> {
                    for (var modifier : item.scopeModifier()) if (modifier instanceof AppParser.RepositoryModifierContext repo) {
                        var indexes = repositoryInputs.computeIfAbsent(method(item.scopeItemIdentifier().getText()), k -> new HashSet<>());
                        var fields = repositoryFields.computeIfAbsent(method(item.scopeItemIdentifier().getText()), k -> new HashSet<>());
                        for (var target : repo.repositoryTarget()) {
                            if (target instanceof AppParser.ArgumentRepositoryTargetContext arg) {
                                indexes.add(arg.IntegerLiteral().getText());
                                fields.add(arg.qualifiedName().getText());
                            } else {
                                indexes.add("unsupported");
                            }
                        }
                        if (repo.repositoryKeyInterception() != null) fields.add("intercepted");
                    }
                });
            } else if (source.tree() instanceof ProgramContext program) {
                visit(program, AtomiccallContext.class, call -> {
                    if (call.Identifier() != null) callCounts.merge(method(call.Identifier().getText()), 1, Integer::sum);
                });
                for (var unit : program.compilationUnits().compilationUnit()) {
                    if (unit.role() != null && unit.role().getStart().getText().equals("Role"))
                        roles.add(unit.role().Identifier().getText());
                    if (unit.role() != null && unit.role().getStart().getText().equals("Contract"))
                        contracts.add(unit.role().Identifier().getText());
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
            if (domain.implicitCrud(service)) domain.warn(service, "SPP_REDUNDANT_IMPLICIT_CRUD",
                    "Remove this declaration and use the built-in " + method(service.name()) + ".");
            var copy = domain.fieldCopy(service);
            if (copy != null) domain.warn(service, "SPP_REDUNDANT_FIELD_COPY",
                    "Use " + copy.take() + " with a typed copy delegate.");
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

    private boolean implicitCrud(Service service) {
        String type = implicitCrudType(service.node);
        return type != null && types.containsKey(type);
    }

    private static String implicitCrudType(AtomicserviceContext node) {
        if (node.calculationBody() != null || node.quantumService() != null) return null;
        var params = parameters(node.serviceDefinition());
        if (params.size() != 1 || params.get(0).roletype() == null || params.get(0).queryKey() != null) return null;
        var role = params.get(0).roletype();
        String type = role.Identifier().getText();
        if (!(role.getText().equals(type) || role.getText().equals(type + "[]"))) return null;
        String kind = node.servicetype().getText();
        String builtin = (kind.equals("query") ? "find" : kind) + type;
        return Set.of("query", "insert", "update", "delete").contains(kind)
                && builtin.equals(method(node.serviceDefinition().Identifier().getText())) ? type : null;
    }

    static boolean isImplicitCrud(List<RedundancyLint.Source> sources, AtomicserviceContext atomic) {
        String type = implicitCrudType(atomic);
        if (type == null) return false;
        for (var source : sources) if (source.tree() instanceof ProgramContext spp) {
            for (var unit : spp.compilationUnits().compilationUnit())
                if (unit.role() != null && type.equals(unit.role().Identifier().getText())
                        || unit.entity() != null && type.equals(unit.entity().Identifier().getText())) return true;
        }
        return false;
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

    private CopyProjection fieldCopy(Service service) {
        if (!service.node.servicetype().getText().equals("calculate")
                && !method(service.name()).matches("(prepare|select).*")) return null;
        var assignments = assignments(service);
        if (assignments.isEmpty()) return null;
        var formals = parameters(service.definition());
        String target = assignments.get(0).target.participant;
        Field first = field(assignments.get(0).value);
        if (first == null || first.participant.equals(target)) return null;
        String from = participantType(formals, first.participant), to = participantType(formals, target);
        if (from == null || to == null || from.equals(to)) return null;
        // A take targets a Role. For writes from a role to its performer the mapping is reversed.
        boolean reverse = !roles.contains(to);
        String performer = reverse ? to : from, role = reverse ? from : to;
        if (!roles.contains(role) || contracts.contains(performer)) return null;
        Map<String,String> mapping = new LinkedHashMap<>();
        for (var a : assignments) {
            Field rhs = field(a.value);
            if (rhs == null || !target.equals(a.target.participant) || !first.participant.equals(rhs.participant)) return null;
            String f = reverse ? rhs.name : a.target.name, g = reverse ? a.target.name : rhs.name;
            String type = types.getOrDefault(role, Map.of()).get(f);
            if (type == null || !type.equals(types.getOrDefault(performer, Map.of()).get(g))
                    || !scalar(type) || mapping.containsValue(g) || mapping.putIfAbsent(f, g) != null) return null;
        }
        var existing = takes.stream().filter(t -> t.Identifier().size() == 2 && t.Identifier(0).getText().equals(performer)
                && t.Identifier(1).getText().equals(role)).toList();
        Set<String> common = new HashSet<>(types.get(role).keySet());
        common.retainAll(types.get(performer).keySet());
        boolean same = common.equals(mapping.keySet()) && mapping.entrySet().stream().allMatch(e -> e.getKey().equals(e.getValue()));
        String body = same ? "mapsame;" : String.join(" ", mapping.entrySet().stream()
                .map(e -> base(types.get(role).get(e.getKey())) + " " + e.getKey() + " = " + e.getValue() + ";").toList());
        if (!existing.isEmpty() && (existing.size() != 1 || !mapping.equals(mappedFields(existing.get(0), performer, role)))) return null;
        return new CopyProjection("take " + performer + " as " + role + " { " + body + " }",
                performer, role, !existing.isEmpty(), first.participant, target);
    }

    record CopyProjection(String take, String performer, String role, boolean existing, String from, String to) { }

    static CopyProjection projection(List<RedundancyLint.Source> sources, AtomicserviceContext node) {
        var domain = new ServiceRedundancy(sources, null);
        var service = domain.services.get(method(node.serviceDefinition().Identifier().getText()));
        return service != null && service.node == node ? domain.fieldCopy(service) : null;
    }

    record CopyRead(AtomiccallContext statement, String provider) { }

    static CopyRead copyRead(List<RedundancyLint.Source> sources, AtomicserviceContext atomic,
                             AtomiccallContext call, CopyProjection projection) {
        if (!inSerialGroup(call) || call.parameters() == null || call.scenarioBranch() != null
                || call.transactionBreak() != null || call.quantumBounds() != null || !projection.existing()) return null;
        var index = new ServiceRedundancy(sources, null);
        var service = index.services.get(method(atomic.serviceDefinition().Identifier().getText()));
        var params = parameters(service.definition());
        var args = call.parameters().parameter();
        if (params.size() != 2 || args.size() != 2 || !projection.role().equals(participantType(params, projection.to()))) return null;
        int from = name(params.get(0)).equals(projection.from()) ? 0 : 1;
        String provider = args.get(from).getText(), target = args.get(1 - from).getText();
        if (provider.contains(".") || target.contains(".") || provider.equals(target)) return null;
        var assigned = new HashSet<String>();
        assignments(service).forEach(a -> assigned.add(a.target.name));
        if (!assigned.equals(index.keys.get(projection.role()))) return null;
        // A second incoming take could contribute another key path to this read.
        if (index.takes.stream().filter(t -> t.Identifier(t.Identifier().size()-1).getText().equals(projection.role())).count() != 1
                || index.takes.stream().anyMatch(t -> t.Identifier(t.Identifier().size()-1).getText().equals(projection.performer()))) return null;
        var parent = call.getParent();
        for (int i = 1; i < parent.getChildCount(); i++) {
            if (parent.getChild(i - 1) != call || !(parent.getChild(i) instanceof AtomiccallContext read)) continue;
            String find = "find" + projection.role();
            if (read.Identifier() == null || !read.Identifier().getText().equals(find) || read.parameters() == null
                    || !read.parameters().getText().equals(target) || read.getChildCount() != 5) return null;
            var query = index.services.get(find);
            if (query != null && !index.implicitCrud(query)) return null;
            return new CopyRead(read, provider);
        }
        return null;
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
            if (!inSerialGroup(call)) return;
            for (int i = 1; i < parent.getChildCount(); i++)
                if (parent.getChild(i-1) == call && parent.getChild(i) instanceof AtomiccallContext read && read.Identifier() != null)
                    keySetter(source, scenario, setter, call, read);
        });
    }

    static boolean inSerialGroup(AtomiccallContext call) {
        for (var context = call.getParent(); context != null; context = context.getParent()) {
            if (context instanceof ParallelgroupContext) return false;
            if (context instanceof SerialgroupContext || context instanceof TransactionStatementsContext) return true;
            if (context instanceof ScenarioContext scenario)
                return scenario.autosort() != null && scenario.autosort().getText().equals("serial");
        }
        return false;
    }

    private void keySetter(RedundancyLint.Source source, ScenarioContext scenario, Service service,
                           AtomiccallContext call, AtomiccallContext read) {
        String find = method(read.Identifier().getText());
        if (read.scenarioBranch() != null || read.uniqueModifier() != null) return;
        var assignments = assignments(service);
        if (assignments.isEmpty()) return;
        var formals = parameters(service.definition());
        String target = assignments.get(0).target.participant, type = participantType(formals, target);
        if (type == null) return;
        String actual = actual(scenario, call, formals, target);
        if (actual == null) return;
        if (read.readBinding() != null) {
            preparedOperand(source, scenario, service, call, read, assignments, target, type, actual);
            return;
        }
        if (!keys.containsKey(type)) return;
        Service query = services.get(find);
        if (query == null) {
            if (!find.equals("find" + type)) return;
            if (read.parameters() == null ? !actual.equals(uniqueActual(scenario, type))
                    : !read.parameters().getText().equals(actual)) return;
        } else {
            var params = parameters(query.definition());
            var selectors = params.stream().filter(p -> p.queryKey() != null).toList();
            if (selectors.isEmpty()) {
                var inputs = repositoryInputs.getOrDefault(find, Set.of());
                if (inputs.size() == 1) {
                    for (int index = 0; index < params.size(); index++)
                        if (inputs.contains(Integer.toString(index))) selectors = List.of(params.get(index));
                } else if (inputs.isEmpty() && params.size() == 1) selectors = params;
            }
            if (!query.node.servicetype().getText().equals("query") || selectors.size() != 1) return;
            var selector = selectors.get(0);
            if (selector.roletype() == null || !selector.roletype().getText().equals(type)
                    || !actual.equals(actual(scenario, read, params, name(selector)))) return;
        }
        // Named list queries can select by a repository field outside the APP primary key.
        // A constant lookup interception is executable there; a partial SPP "by" is not.
        if (query != null && assignments.size() == 1 && callCounts.getOrDefault(find, 0) == 1) {
            var a = assignments.get(0);
            var fields = repositoryFields.getOrDefault(find, Set.of());
            if (!keys.get(type).contains(a.target.name) && a.value instanceof PrimExprContext literal
                    && literal.primaryExpr().primaryExprLiteral() != null
                    && literal.primaryExpr().primaryExprLiteral().literal().StringLiteral() != null
                    && fields.equals(Set.of(type + "." + a.target.name))) {
                reporter.warn(source, call.Identifier().getSymbol(), "SPP_REDUNDANT_KEY_SETTER",
                        "This calculation only supplies the repository filter for " + find
                                + ". Use intercept key { " + a.target.name + " = " + a.value.getText()
                                + "; } on that lookup if other callers use the same constant; preserve any other uses of the selector.");
                return;
            }
        }
        if (query != null && indexedSelector(source, scenario, service, call, read, assignments, target, type)) return;
        Set<String> assigned = new HashSet<>();
        List<String> parts = new ArrayList<>();
        Set<String> participants = new LinkedHashSet<>();
        Map<String, Map<String, String>> proposedTakes = new LinkedHashMap<>();
        for (var a : assignments) {
            if (!a.target.participant.equals(target) || !assigned.add(a.target.name)) return;
            Field rhs = field(a.value);
            if (rhs != null) {
                if (rhs.participant.equals(target)) return;
                String provider = participantType(formals, rhs.participant);
                String providerActual = actual(scenario, call, formals, rhs.participant);
                if (provider == null || providerActual == null) return;
                var bindings = takes.stream().filter(t -> t.Identifier().size()==2 && t.Identifier(0).getText().equals(provider)
                        && t.Identifier(1).getText().equals(type)).toList();
                if (bindings.size() > 1) return;
                Map<String, String> mapped;
                if (bindings.isEmpty()) {
                    if (provider.equals(type)) return;
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
            if (!roles.contains(type) || contracts.contains(take.getKey())) {
                reporter.warn(source, call.Identifier().getSymbol(), "SPP_REDUNDANT_KEY_SETTER",
                        service.name() + " only prepares the primary key for " + find + ". Model its key inputs as Role participants "
                                + "before replacing this call with " + find + args + " by " + String.join(", ", parts)
                                + "; preserve the key mapping " + take.getValue() + ".");
                return;
            }
            prefix.append("Declare take ").append(take.getKey()).append(" as ").append(type).append(" { ");
            take.getValue().forEach((targetField, sourceField) -> prefix.append(base(types.get(type).get(targetField)))
                    .append(' ').append(targetField).append(" = ").append(sourceField).append("; "));
            prefix.append("} first. ");
        }
        reporter.warn(source, call.Identifier().getSymbol(), "SPP_REDUNDANT_KEY_SETTER", prefix + "Replace " + service.name()
                + " and the following read with " + find + args + " by " + String.join(", ", parts) + ";");
    }

    private void preparedOperand(RedundancyLint.Source source, ScenarioContext scenario, Service setter,
                                 AtomiccallContext call, AtomiccallContext read, List<Assignment> assignments,
                                 String target, String operandType, String operand) {
        // The helper sometimes fills the existing "by" operand, rather than the read's result.
        var bindings = read.readBinding().readKey();
        String query = method(read.Identifier().getText());
        if (!query.startsWith("find") || bindings.size() != 1 || !bindings.get(0).getText().equals(operand)) return;
        String resultType = query.substring(4);
        var primary = keys.get(resultType);
        if (primary == null || primary.isEmpty()) return;
        if (services.containsKey(query) && !implicitCrud(services.get(query))) return;
        var paths = takes.stream().filter(t -> t.Identifier().size() == 2
                && operandType.equals(t.Identifier(0).getText()) && resultType.equals(t.Identifier(1).getText())).toList();
        if (paths.size() != 1) return;
        var mapping = mappedFields(paths.get(0), operandType, resultType);
        Map<String, String> supplied = new LinkedHashMap<>();
        for (var a : assignments) {
            Field rhs = field(a.value);
            if (rhs == null || rhs.participant.equals(target) || !target.equals(a.target.participant)) return;
            var params = parameters(setter.definition());
            String rhsType = participantType(params, rhs.participant);
            if (rhsType == null) return;
            String provider = actual(scenario, call, params, rhs.participant);
            String fieldType = types.getOrDefault(rhsType, Map.of()).get(rhs.name);
            if (provider == null || fieldType == null || !scalar(fieldType)
                    || !fieldType.equals(types.getOrDefault(operandType, Map.of()).get(a.target.name))
                    || supplied.putIfAbsent(a.target.name, provider + "." + rhs.name) != null) return;
        }
        Set<String> needed = new HashSet<>();
        for (String key : primary) {
            String field = mapping.get(key);
            if (field == null) return;
            needed.add(field);
        }
        if (!needed.equals(supplied.keySet())) return;
        reporter.warn(source, call.Identifier().getSymbol(), "SPP_REDUNDANT_KEY_SETTER",
                setter.name() + " only fills " + operand + " for the following " + query + " by " + operand
                        + ". Represent " + supplied + " in a typed lookup Role and bind the read directly; preserve other uses of the selector.");
    }

    private boolean indexedSelector(RedundancyLint.Source source, ScenarioContext scenario, Service setter,
                                    AtomiccallContext call, AtomiccallContext read, List<Assignment> assignments,
                                    String target, String type) {
        // A prefix or secondary-index selector is observable in APP even when SPP "by" would
        // require additional primary-key fields. Match the entire repository predicate.
        String query = method(read.Identifier().getText());
        var predicates = repositoryFields.get(query);
        if (predicates == null || predicates.size() != assignments.size()) return false;
        var params = parameters(setter.definition());
        Map<String, String> fields = new LinkedHashMap<>();
        String provider = null;
        for (var assignment : assignments) {
            Field rhs = field(assignment.value);
            if (rhs == null || rhs.participant.equals(target) || !assignment.target.participant.equals(target)
                    || !predicates.contains(type + "." + assignment.target.name)) return false;
            String fieldType = types.getOrDefault(type, Map.of()).get(assignment.target.name);
            String providerType = participantType(params, rhs.participant);
            if (providerType == null) return false;
            String actual = actual(scenario, call, params, rhs.participant);
            if (actual == null || fieldType == null || !scalar(fieldType)
                    || !fieldType.equals(types.getOrDefault(providerType, Map.of()).get(rhs.name))
                    || provider != null && !provider.equals(actual)) return false;
            provider = actual;
            if (fields.putIfAbsent(assignment.target.name, actual + "." + rhs.name) != null) return false;
        }
        if (fields.keySet().equals(keys.get(type))) return false;
        reporter.warn(source, call.Identifier().getSymbol(), "SPP_REDUNDANT_KEY_SETTER",
                setter.name() + " only prepares the repository selector for " + query + ". Use a lookup Role "
                        + "with " + fields + " and bind an index-backed query on " + type + fields.keySet()
                        + "; preserve the existing filter, result ordering and any other selector uses.");
        return true;
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
        return com.servpp.lang.ParticipantNames.bindingName(p);
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
