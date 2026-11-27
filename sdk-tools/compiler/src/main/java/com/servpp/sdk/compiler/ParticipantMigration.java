package com.servpp.sdk.compiler;

import com.servpp.lang.ParticipantNames;
import com.servpp.lang.ParticipantNames.Shape;
import com.servpp.lang.ParticipantNames.ShortNames;
import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.antlr.SppParser.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

/** Computes source edits from the same grammar used by compilation. */
public final class ParticipantMigration {
    public static void main(String[] arguments) throws IOException {
        Path root = Path.of(arguments[0]);
        Path manifest = Path.of(arguments[1]);
        Path output = Path.of(arguments[2]);
        boolean useDeclaredNames = Boolean.parseBoolean(arguments[3]);
        List<Path> files = Files.readAllLines(manifest).stream().map(root::resolve).toList();
        Map<String, String> mapping = arguments.length == 5 ? readMapping(Path.of(arguments[4])) : Map.of();
        Result result = new ParticipantMigration().migrate(files, useDeclaredNames, mapping);
        for (var change : result.changed.entrySet()) {
            Path target = output.resolve(root.relativize(change.getKey()));
            Files.createDirectories(target.getParent());
            Files.writeString(target, change.getValue());
        }
        List<String> tsv = new ArrayList<>();
        tsv.add("kind\tdomain/flow\tfile:line\tservice\ttype\tnames\tsuggested_short_name\tspp_domain\tdetails");
        result.conflicts.forEach(conflict -> tsv.add(conflict.tsv(root)));
        Files.write(output.resolve(".conflicts"), tsv);
        System.out.println("Files scanned: " + files.size());
        System.out.println("Files touched: " + result.changed.size());
        System.out.println("Services rewritten: " + result.servicesRewritten);
        System.out.println("Unresolved conflicts: " + result.conflicts.size());
        if (!result.conflicts.isEmpty()) System.exit(2);
    }

    static Map<String, String> readMapping(Path path) throws IOException {
        Map<String, String> mapping = new LinkedHashMap<>();
        int line = 0;
        for (String row : Files.readAllLines(path)) {
            line++;
            if (row.isBlank() || row.stripLeading().startsWith("#")) continue;
            String[] fields = row.split("\t", -1);
            if (fields.length != 2 || !fields[0].matches(
                    "(?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)?"
                            + "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(?:\\[\\])?")
                    || !identifier(fields[1]) || mapping.putIfAbsent(fields[0], fields[1]) != null)
                throw new IOException(path + ":" + line + ": expected a unique type<TAB>short mapping");
        }
        return mapping;
    }

    private static boolean identifier(String name) {
        if (name.isEmpty()) return false;
        var lexer = new SppLexer(CharStreams.fromString(name));
        lexer.removeErrorListeners();
        var token = lexer.nextToken();
        return token.getType() == SppLexer.Identifier && token.getText().equals(name);
    }

    private record FileSource(Path path, String text, StatementTemplates.View view, ProgramContext tree) {
        String domain() { return ParticipantNames.domain(tree); }
        int offset(Token token) { return view.text().offsetByCodePoints(0, token.getStartIndex()); }
        int sourceStart(Token token) { return view.sourceOffset(offset(token)); }
        int sourceEnd(Token token) { return sourceStart(token) + token.getText().length(); }
    }

    private record Type(String domain, String type) {
        String mapped(Map<String, String> mapping, String suffix) {
            String key = type + suffix;
            return mapping.getOrDefault(domain + "." + key, mapping.get(key));
        }
    }
    private record Slot(Type type, int dimensions, boolean value) {
        String spelling() { return type.type + "[]".repeat(dimensions); }
        String defaultName() { return ParticipantNames.defaults(type.type, value).forShape(dimensions); }
    }
    private record Declaration(FileSource file, Token name, ShortNames names, ShortNamesContext clause) { }
    private record Service(FileSource file, ServiceDefinitionContext definition, ParseTree body) {
        Site site() { return new Site(file.path, lineAt(file.text, file.sourceStart(definition.getStart())),
                definition.Identifier().getText(), file.domain()); }
    }
    private record Site(Path path, int line, String service, String domain) {
        String location(Path root) { return root.relativize(path) + ":" + line; }

        String domainFlow(Path root) {
            Path relative = root.relativize(path);
            if (relative.getNameCount() > 2) {
                String top = relative.getName(0).toString();
                if (top.equals("applications")) return relative.subpath(0, 2).toString();
                if (top.equals("domains")) {
                    String group = relative.getName(2).toString();
                    return relative.subpath(0, (group.equals("flows") || group.equals("shared"))
                            && relative.getNameCount() > 4 ? 4 : 2).toString();
                }
            }
            return domain.isEmpty() ? relative.getName(0).toString() : domain;
        }
    }
    public record Conflict(String kind, Site site, String type, String names, String suggestion,
                           String detail, List<Site> usages) {
        String tsv(Path root) {
            var locations = new StringJoiner(", ");
            for (Site usage : usages) locations.add(usage.location(root) + " " + usage.service);
            String details = locations.length() == 0 ? detail : locations.toString();
            return String.join("\t", List.of(kind, site.domainFlow(root), site.location(root), site.service, type,
                    names, suggestion, site.domain, details).stream()
                    .map(value -> value.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")).toList());
        }
    }

    private record Edit(int start, int end, String text) { }
    private record Binding(Slot slot, String name, boolean typeSpelling) { }
    private final Map<Type, Declaration> declarations = new LinkedHashMap<>();
    private final Map<Type, ShortNames> selected = new LinkedHashMap<>();
    private final Map<FileSource, List<Edit>> edits = new LinkedHashMap<>();
    private final List<Conflict> conflicts = new ArrayList<>();
    private final Set<Slot> blocked = new HashSet<>();
    private final Set<Type> used = new LinkedHashSet<>();
    private final List<FileSource> files = new ArrayList<>();
    private final List<Service> services = new ArrayList<>();
    private int rewritten;

    public record Result(Map<Path, String> changed, List<Conflict> conflicts, int servicesRewritten) { }

    public Result migrate(List<Path> paths, boolean useDeclaredNames) throws IOException {
        return migrate(paths, useDeclaredNames, Map.of());
    }

    public Result migrate(List<Path> paths, boolean useDeclaredNames, Map<String, String> mapping) throws IOException {
        var templates = new StatementTemplates(paths);
        for (Path path : paths) {
            if (StatementTemplates.isFragment(path)) continue;
            String source;
            try {
                source = Files.readString(path);
            } catch (IOException failure) {
                parseConflict(path, 1, "Cannot read source: " + failure.getClass().getSimpleName());
                continue;
            }
            try {
                var view = templates.expand(path, source);
                var file = new FileSource(path, source, view, parse(view));
                files.add(file);
                index(file);
            } catch (StatementTemplates.IncludeFailure failure) {
                parseConflict(path, lineAt(source, failure.offset), failure.getMessage());
            } catch (SyntaxFailure failure) {
                parseConflict(path, lineAt(source, failure.offset), failure.getMessage());
            }
        }
        chooseNames(useDeclaredNames, mapping);
        services.forEach(this::rewrite);
        for (Type type : used) {
            Declaration declaration = declarations.get(type);
            ShortNames names = selected.get(type);
            if (declaration == null || names == null || names.equals(declaration.names)) continue;
            if (declaration.clause == null && names.equals(ParticipantNames.defaults(type.type, false))) continue;
            editDeclaration(declaration, names);
        }
        Map<Path, String> changed = new LinkedHashMap<>();
        for (FileSource file : files) {
            List<Edit> changes = edits.get(file);
            if (changes == null) continue;
            changes.sort(Comparator.comparingInt(Edit::start).reversed());
            var text = new StringBuilder(file.text);
            for (Edit edit : changes) text.replace(edit.start, edit.end, edit.text);
            if (!file.text.contentEquals(text)) changed.put(file.path, text.toString());
        }
        return new Result(Collections.unmodifiableMap(changed), List.copyOf(conflicts), rewritten);
    }

    private void editDeclaration(Declaration declaration, ShortNames names) {
        FileSource file = declaration.file;
        if (declaration.clause == null) {
            int offset = file.sourceEnd(declaration.name);
            edit(file, new Edit(offset, offset, " short " + names.scalar()
                    + (names.array() == null ? "" : ", " + names.array())));
            return;
        }
        Token scalar = declaration.clause.Identifier(0).getSymbol();
        String newScalar = names.scalar();
        if (declaration.clause.Identifier().size() == 2) {
            Token array = declaration.clause.Identifier(1).getSymbol();
            String newArray = names.forShape(1);
            if (!array.getText().equals(newArray))
                edit(file, new Edit(file.sourceStart(array), file.sourceEnd(array), newArray));
        } else if (names.array() != null) {
            newScalar += ", " + names.array();
        }
        if (!scalar.getText().equals(newScalar))
            edit(file, new Edit(file.sourceStart(scalar), file.sourceEnd(scalar), newScalar));
    }

    private static final class SyntaxFailure extends IOException {
        final int offset;

        SyntaxFailure(int offset, String message) {
            super(message);
            this.offset = offset;
        }
    }

    private static ProgramContext parse(StatementTemplates.View view) throws SyntaxFailure {
        List<SyntaxFailure> errors = new ArrayList<>();
        var listener = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line,
                                              int column, String message, RecognitionException failure) {
                if (!errors.isEmpty()) return;
                int offset = 0;
                for (int previous = 1; previous < line; previous++) offset = view.text().indexOf('\n', offset) + 1;
                offset = view.text().offsetByCodePoints(offset, column);
                errors.add(new SyntaxFailure(view.sourceOffset(offset), message));
            }
        };
        var lexer = new SppLexer(CharStreams.fromString(view.text()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        var parser = new SppParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        var tree = parser.program();
        if (!errors.isEmpty()) throw errors.getFirst();
        return tree;
    }

    private static int lineAt(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) if (text.charAt(index) == '\n') line++;
        return line;
    }

    private void parseConflict(Path path, int line, String detail) {
        conflicts.add(new Conflict("parse", new Site(path, line, "", ""), "", "", "", detail, List.of()));
    }

    private void index(FileSource file) {
        for (var unit : file.tree.compilationUnits().compilationUnit()) {
            var entity = unit.entity();
            var role = unit.role();
            if (entity != null || role != null) {
                var name = (entity == null ? role.Identifier() : entity.Identifier()).getSymbol();
                var clause = entity == null ? role.shortNames() : entity.shortNames();
                Type type = new Type(file.domain(), name.getText());
                ShortNames names = clause == null ? null : new ShortNames(clause.Identifier(0).getText(),
                        clause.Identifier().size() == 2 ? clause.Identifier(1).getText() : null);
                var previous = declarations.putIfAbsent(type, new Declaration(file, name, names, clause));
                if (previous != null) {
                    conflicts.add(new Conflict("name-conflict", new Site(file.path, lineAt(file.text, file.sourceStart(name)),
                            "", file.domain()), type.type, "", "", "Duplicate type declaration", List.of()));
                }
            }
            var definition = ParticipantNames.definition(unit);
            if (definition != null) services.add(new Service(file, definition,
                    unit.scenario() == null ? unit.atomicservice().calculationBody() : unit.scenario().scenarioDeclaration()));
        }
    }

    private void chooseNames(boolean useDeclaredNames, Map<String, String> mapping) {
        Map<Slot, Map<String, List<Site>>> occurrences = new LinkedHashMap<>();
        Map<Slot, Site> firstUse = new LinkedHashMap<>();
        for (Service service : services) {
            Site site = service.site();
            for (var parameter : ParticipantNames.parameters(service.definition)) {
                Slot slot = slot(service, parameter);
                firstUse.putIfAbsent(slot, site);
                if (parameter.Identifier() == null) continue;
                occurrences.computeIfAbsent(slot, unused -> new LinkedHashMap<>())
                        .computeIfAbsent(parameter.Identifier().getText(), unused -> new ArrayList<>()).add(site);
            }
        }
        for (var entry : occurrences.entrySet()) {
            Slot slot = entry.getKey();
            boolean mapped = slot.type.mapped(mapping, slot.dimensions == 0 ? "" : "[]") != null
                    || (slot.dimensions > 0 && slot.type.mapped(mapping, "") != null);
            if (useDeclaredNames || mapped || entry.getValue().size() <= 1) continue;
            blocked.add(slot);
            reportAliases(slot, entry.getValue());
        }
        for (var entry : declarations.entrySet()) {
            Type type = entry.getKey();
            ShortNames declared = entry.getValue().names;
            String scalar = type.mapped(mapping, "");
            String array = type.mapped(mapping, "[]");
            if (scalar == null) {
                scalar = declared == null ? ParticipantNames.defaults(type.type, false).scalar() : declared.scalar();
                if (declared == null && !useDeclaredNames)
                    scalar = consistent(occurrences, new Slot(type, 0, false), scalar);
            }
            if (array == null) {
                array = declared == null ? null : declared.array();
                if (declared == null && !useDeclaredNames && type.mapped(mapping, "") == null)
                    array = consistent(occurrences, new Slot(type, 1, false), null);
            }
            selected.put(type, new ShortNames(scalar, Objects.equals(array, scalar + "s") ? null : array));
        }
        for (var entry : firstUse.entrySet()) {
            Slot slot = entry.getKey();
            if (declarations.containsKey(slot.type)) continue;
            String mapped = slot.type.mapped(mapping, slot.dimensions == 0 ? "" : "[]");
            String scalar = slot.type.mapped(mapping, "");
            if (slot.dimensions > 0 && mapped == null && scalar != null) mapped = scalar + "s";
            if (mapped == null) continue;
            if (slot.dimensions > 1) mapped += "s".repeat(slot.dimensions - 1);
            if (mapped.equals(slot.defaultName())) continue;
            blocked.add(slot);
            Site site = entry.getValue();
            conflicts.add(new Conflict("name-conflict", site, slot.spelling(), mapped, slot.defaultName(),
                    "Custom short names require a type declaration in the migration scope", List.of()));
        }
    }

    private void reportAliases(Slot slot, Map<String, List<Site>> names) {
        var ranking = names.entrySet().stream().sorted(
                Comparator.<Map.Entry<String, List<Site>>>comparingInt(entry -> entry.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey)).toList();
        String suggestion = ranking.get(0).getValue().size() == ranking.get(1).getValue().size()
                ? slot.defaultName() : ranking.getFirst().getKey();
        var counts = new StringJoiner(", ");
        ranking.forEach(entry -> counts.add(entry.getKey() + "=" + entry.getValue().size()));
        var usages = names.values().stream().flatMap(List::stream).distinct().toList();
        conflicts.add(new Conflict("name-conflict", usages.getFirst(), slot.spelling(), counts.toString(),
                suggestion, "", usages));
    }

    private static String consistent(Map<Slot, Map<String, List<Site>>> names, Slot slot, String fallback) {
        var found = names.getOrDefault(slot, Map.of());
        return found.size() == 1 ? found.keySet().iterator().next() : fallback;
    }

    private static Slot slot(Service service, ParameterDeclarationContext parameter) {
        Shape shape = ParticipantNames.shape(parameter);
        return new Slot(new Type(service.file.domain(), shape.type()), shape.dimensions(), shape.value());
    }

    private void rewrite(Service service) {
        Map<String, Binding> aliases = new LinkedHashMap<>();
        Map<String, Binding> typeAliases = new LinkedHashMap<>();
        Map<String, List<Slot>> shortNames = new LinkedHashMap<>();
        Map<Slot, List<String>> parameters = new LinkedHashMap<>();
        List<Edit> changes = new ArrayList<>();
        boolean valid = true;
        Site site = service.site();
        for (var parameter : ParticipantNames.parameters(service.definition)) {
            Slot slot = slot(service, parameter);
            String shortName = selected.getOrDefault(slot.type, ParticipantNames.defaults(slot.type.type, slot.value))
                    .forShape(slot.dimensions);
            var explicit = parameter.Identifier();
            Declaration declaration = declarations.get(slot.type);
            ShortNames previous = declaration == null ? null : declaration.names;
            String old = explicit != null ? explicit.getText()
                    : previous == null ? slot.defaultName() : previous.forShape(slot.dimensions);
            parameters.computeIfAbsent(slot, unused -> new ArrayList<>()).add(old);
            shortNames.computeIfAbsent(shortName, unused -> new ArrayList<>()).add(slot);
            var binding = new Binding(slot, shortName, false);
            if (explicit != null && blocked.contains(slot)) valid = false;
            if (aliases.putIfAbsent(old, binding) != null && explicit != null) {
                conflicts.add(new Conflict("name-conflict", site, slot.spelling(), old, shortName,
                        "Duplicate original parameter name", List.of()));
                valid = false;
            }
            if (explicit == null) {
                if (!slot.value) typeAliases.put(slot.spelling(), new Binding(slot, shortName, true));
                continue;
            }
            Token token = explicit.getSymbol();
            Token typeEnd = (parameter.type() == null ? parameter.roletype() : parameter.type()).getStop();
            int start = service.file.sourceEnd(typeEnd);
            int nameStart = service.file.sourceStart(token);
            if (!service.file.text.substring(start, nameStart).isBlank()) start = nameStart;
            changes.add(new Edit(start, service.file.sourceEnd(token), ""));
        }
        for (var entry : parameters.entrySet()) {
            Slot slot = entry.getKey();
            if (entry.getValue().size() <= 1) continue;
            conflicts.add(new Conflict("duplicate-type", site, slot.spelling(), String.join(", ", entry.getValue()),
                    "", "Choose a distinct role type for each participant", List.of()));
            valid = false;
        }
        for (var entry : shortNames.entrySet()) {
            var types = entry.getValue().stream().distinct().toList();
            if (types.size() < 2) continue;
            conflicts.add(new Conflict("name-conflict", site,
                    String.join(", ", types.stream().map(Slot::spelling).toList()),
                    entry.getKey() + "=" + entry.getValue().size(),
                    String.join(", ", types.stream().map(Slot::defaultName).toList()),
                    "Short name shared by different parameter types in this signature", List.of()));
            valid = false;
        }
        if (!valid) return;
        typeAliases.forEach(aliases::putIfAbsent);
        int conflictsBefore = conflicts.size();
        ParticipantNames.references(service.body, (path, root, brackets) -> {
            String old = root.getText() + "[]".repeat(brackets.size() / 2);
            Binding binding = aliases.get(old);
            if (binding == null || old.equals(binding.name) || ParticipantNames.shadowed(path, root.getText())) return;
            int offset = service.file.offset(root);
            int line = lineAt(service.file.text, service.file.view.sourceOffset(offset));
            String problem = null;
            if (service.file.view.generated(offset)) {
                if (binding.typeSpelling && !shortNames.containsKey(old)) return;
                problem = "Update the statement include binding or fragment at line " + line;
            }
            else if (ParticipantNames.shadowed(path, binding.name)) problem = "Short name is shadowed at line " + line;
            if (problem != null) {
                var conflict = new Conflict("name-conflict", site, binding.slot.spelling(), old, binding.name, problem, List.of());
                if (!conflicts.contains(conflict)) conflicts.add(conflict);
                return;
            }
            changes.add(new Edit(service.file.sourceStart(root), service.file.sourceEnd(root), binding.name));
            for (Token bracket : brackets)
                changes.add(new Edit(service.file.sourceStart(bracket), service.file.sourceEnd(bracket), ""));
        });
        if (conflicts.size() != conflictsBefore) return;
        for (Slot slot : parameters.keySet()) used.add(slot.type);
        if (changes.isEmpty()) return;
        changes.forEach(change -> edit(service.file, change));
        rewritten++;
    }

    private void edit(FileSource file, Edit edit) {
        edits.computeIfAbsent(file, unused -> new ArrayList<>()).add(edit);
    }
}
