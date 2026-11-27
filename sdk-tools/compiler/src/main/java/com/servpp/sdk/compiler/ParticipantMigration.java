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
        Result result = new ParticipantMigration().migrate(files, useDeclaredNames);
        for (var change : result.changed.entrySet()) {
            Path target = output.resolve(root.relativize(change.getKey()));
            Files.createDirectories(target.getParent());
            Files.writeString(target, change.getValue());
        }
        Files.write(output.resolve(".conflicts"), result.conflicts);
        System.out.println("Files touched: " + result.changed.size());
        System.out.println("Services rewritten: " + result.servicesRewritten);
        System.out.println("Unresolved conflicts: " + result.conflicts.size());
    }

    private record FileSource(Path path, String text, ProgramContext tree) {
        String domain() { return ParticipantNames.domain(tree); }
        int offset(int codePoint) { return text.offsetByCodePoints(0, codePoint); }
    }
    private record Type(String domain, String type) { }
    private record Slot(Type type, int dimensions, boolean value) { }
    private record Declaration(FileSource file, Token name, ShortNames shortNames) { }
    private record Service(FileSource file, ServiceDefinitionContext definition, ParseTree body) {
        String location() { return file.path + ":" + definition.getStart().getLine() + " " + definition.Identifier().getText(); }
    }
    private record Edit(int start, int end, String text) { }
    private final Map<Type, Declaration> declarations = new LinkedHashMap<>();
    private final Map<Type, ShortNames> selected = new LinkedHashMap<>();
    private final Map<FileSource, List<Edit>> edits = new LinkedHashMap<>();
    private final List<String> conflicts = new ArrayList<>();
    private final Set<Slot> blocked = new HashSet<>();
    private final Set<Type> used = new HashSet<>();
    private final List<FileSource> files = new ArrayList<>();
    private final List<Service> services = new ArrayList<>();
    private int rewritten;

    public record Result(Map<Path, String> changed, List<String> conflicts, int servicesRewritten) { }

    public Result migrate(List<Path> paths, boolean useDeclaredNames) throws IOException {
        for (Path path : paths) {
            try {
                String text = Files.readString(path);
                var file = new FileSource(path, text, parse(text));
                files.add(file);
                index(file);
            } catch (IOException failure) {
                conflicts.add(path + " " + failure.getMessage());
            }
        }
        chooseNames(useDeclaredNames);
        services.forEach(this::rewrite);
        for (Type type : used) {
            Declaration declaration = declarations.get(type);
            ShortNames names = selected.get(type);
            if (declaration == null || declaration.shortNames != null || names == null
                    || names.equals(ParticipantNames.defaults(type.type, false))) continue;
            int offset = declaration.file.offset(declaration.name.getStopIndex() + 1);
            edit(declaration.file, new Edit(offset, offset, " short " + names.scalar()
                    + (names.array() == null ? "" : ", " + names.array())));
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

    private static ProgramContext parse(String text) throws IOException {
        List<String> errors = new ArrayList<>();
        var listener = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line,
                                              int column, String message, RecognitionException failure) {
                if (errors.isEmpty()) errors.add(line + ":" + (column + 1) + " " + message);
            }
        };
        var lexer = new SppLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        var parser = new SppParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        var tree = parser.program();
        if (!errors.isEmpty()) throw new IOException(errors.get(0));
        return tree;
    }

    private void index(FileSource file) {
        for (var unit : file.tree.compilationUnits().compilationUnit()) {
            var entity = unit.entity();
            var role = unit.role();
            if (entity != null || role != null) {
                var name = (entity == null ? role.Identifier() : entity.Identifier()).getSymbol();
                var names = entity == null ? role.shortNames() : entity.shortNames();
                Type type = new Type(file.domain(), name.getText());
                var previous = declarations.putIfAbsent(type, new Declaration(file, name, names == null ? null
                        : new ShortNames(names.Identifier(0).getText(), names.Identifier().size() == 2
                                ? names.Identifier(1).getText() : null)));
                if (previous != null) conflicts.add(file.path + " duplicate declaration " + type.type);
            }
            var definition = ParticipantNames.definition(unit);
            if (definition != null) services.add(new Service(file, definition,
                    unit.scenario() == null ? unit.atomicservice().calculationBody() : unit.scenario().scenarioDeclaration()));
        }
    }

    private void chooseNames(boolean useDeclaredNames) {
        Map<Slot, Map<String, List<String>>> occurrences = new LinkedHashMap<>();
        for (Service service : services) for (var parameter : ParticipantNames.parameters(service.definition)) {
            if (parameter.Identifier() == null) continue;
            Slot slot = slot(service, parameter);
            occurrences.computeIfAbsent(slot, unused -> new LinkedHashMap<>())
                    .computeIfAbsent(parameter.Identifier().getText(), unused -> new ArrayList<>()).add(service.location());
        }
        declarations.forEach((type, declaration) -> {
            if (declaration.shortNames != null) selected.put(type, declaration.shortNames);
        });
        if (useDeclaredNames) return;
        for (var entry : occurrences.entrySet()) {
            Slot slot = entry.getKey();
            if (entry.getValue().size() <= 1) continue;
            blocked.add(slot);
            conflicts.add(slot.type.domain + "." + slot.type.type + "[]".repeat(slot.dimensions)
                    + " uses conflicting names " + entry.getValue());
        }
        for (var entry : declarations.entrySet()) {
            Type type = entry.getKey();
            if (entry.getValue().shortNames != null) continue;
            ShortNames defaults = ParticipantNames.defaults(type.type, false);
            String scalar = consistent(occurrences, new Slot(type, 0, false), defaults.scalar());
            String array = consistent(occurrences, new Slot(type, 1, false), scalar + "s");
            selected.put(type, new ShortNames(scalar, array.equals(scalar + "s") ? null : array));
        }
    }

    private String consistent(Map<Slot, Map<String, List<String>>> names, Slot slot, String fallback) {
        var found = names.getOrDefault(slot, Map.of());
        return found.size() == 1 ? found.keySet().iterator().next() : fallback;
    }

    private static Slot slot(Service service, ParameterDeclarationContext parameter) {
        Shape shape = ParticipantNames.shape(parameter);
        return new Slot(new Type(service.file.domain(), shape.type()), shape.dimensions(), shape.value());
    }

    private void rewrite(Service service) {
        Map<String, String> aliases = new HashMap<>();
        Set<String> names = new HashSet<>();
        Set<Type> types = new HashSet<>();
        List<Edit> changes = new ArrayList<>();
        boolean valid = true;
        for (var parameter : ParticipantNames.parameters(service.definition)) {
            Slot slot = slot(service, parameter);
            Shape shape = ParticipantNames.shape(parameter);
            String shortName = selected.getOrDefault(slot.type, ParticipantNames.defaults(shape.type(), shape.value()))
                    .forShape(shape.dimensions());
            if (!names.add(shortName)) {
                conflicts.add(service.location() + " short-name collision: " + shortName);
                valid = false;
            }
            var explicit = parameter.Identifier();
            if (explicit != null && blocked.contains(slot)) valid = false;
            types.add(slot.type);
            if (explicit != null) {
                if (aliases.putIfAbsent(explicit.getText(), shortName) != null) {
                    conflicts.add(service.location() + " duplicate original parameter name: " + explicit.getText());
                    valid = false;
                }
                Token token = explicit.getSymbol();
                Token typeEnd = (parameter.type() == null ? parameter.roletype() : parameter.type()).getStop();
                int start = service.file.offset(typeEnd.getStopIndex() + 1);
                int nameStart = service.file.offset(token.getStartIndex());
                if (!service.file.text.substring(start, nameStart).isBlank()) start = nameStart;
                changes.add(new Edit(start, service.file.offset(token.getStopIndex() + 1), ""));
            } else if (!shape.value()) {
                aliases.putIfAbsent(shape.type() + "[]".repeat(shape.dimensions()), shortName);
                aliases.putIfAbsent(ParticipantNames.defaults(shape.type(), false).forShape(shape.dimensions()), shortName);
            }
        }
        if (!valid) return;
        boolean[] captured = {false};
        ParticipantNames.references(service.body, (path, root, brackets) -> {
            String old = root.getText() + "[]".repeat(brackets.size() / 2);
            String replacement = aliases.get(old);
            if (replacement == null || old.equals(replacement) || ParticipantNames.shadowed(path, root.getText())) return;
            if (ParticipantNames.shadowed(path, replacement)) {
                conflicts.add(service.location() + " short name '" + replacement + "' is shadowed at line " + root.getLine());
                captured[0] = true;
                return;
            }
            changes.add(new Edit(service.file.offset(root.getStartIndex()), service.file.offset(root.getStopIndex() + 1), replacement));
            for (Token bracket : brackets)
                changes.add(new Edit(service.file.offset(bracket.getStartIndex()), service.file.offset(bracket.getStopIndex() + 1), ""));
        });
        if (captured[0] || changes.isEmpty()) return;
        used.addAll(types);
        changes.forEach(change -> edit(service.file, change));
        rewritten++;
    }

    private void edit(FileSource file, Edit edit) {
        edits.computeIfAbsent(file, unused -> new ArrayList<>()).add(edit);
    }
}
