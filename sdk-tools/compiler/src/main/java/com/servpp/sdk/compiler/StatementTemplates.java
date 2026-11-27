package com.servpp.sdk.compiler;

import com.servpp.lang.antlr.SppLexer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

/** Source view for projects that splice named statement fragments before compiling S++. */
final class StatementTemplates {
    private static final Pattern INCLUDE = Pattern.compile(
            "^([ ]*)include (\\w+)(?:\\(([\\w.=, ]+)\\))?;$", Pattern.MULTILINE);
    private static final Pattern WORD = Pattern.compile("\\b\\w+\\b");
    private final Map<String, List<Path>> fragments = new HashMap<>();

    static boolean isFragment(Path path) {
        Path parent = path.getParent();
        return parent != null && parent.endsWith(Path.of("spp", "statements"));
    }

    StatementTemplates(List<Path> paths) {
        for (Path path : paths) {
            if (isFragment(path))
                fragments.computeIfAbsent(path.getFileName().toString(), unused -> new ArrayList<>()).add(path);
        }
    }

    record Span(int start, int end, int sourceStart, boolean expanded) { }

    record View(String text, List<Span> spans) {
        int sourceOffset(int offset) {
            for (Span span : spans) {
                if (offset < span.end)
                    return span.expanded ? span.sourceStart : span.sourceStart + offset - span.start;
            }
            Span last = spans.getLast();
            return last.expanded ? last.sourceStart : last.sourceStart + last.end - last.start;
        }

        boolean generated(int offset) {
            return spans.stream().anyMatch(span -> span.expanded && offset >= span.start && offset < span.end);
        }
    }

    static final class IncludeFailure extends IOException {
        final int offset;

        IncludeFailure(int offset, String message) {
            super(message);
            this.offset = offset;
        }
    }

    View expand(Path path, String source) throws IOException {
        StringBuilder text = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        int previous = 0;
        var matcher = INCLUDE.matcher(source);
        Set<Integer> includes = includeOffsets(source);
        while (matcher.find()) {
            if (!includes.contains(matcher.end(1))) continue;
            append(text, spans, source.substring(previous, matcher.start()), previous, false);
            try {
                String body = expandFragment(path, matcher.group(2), matcher.group(3), new LinkedHashSet<>());
                append(text, spans, body.indent(matcher.group(1).length()).stripTrailing(), matcher.start(), true);
            } catch (IOException failure) {
                throw new IncludeFailure(matcher.start(), failure.getMessage());
            }
            previous = matcher.end();
        }
        append(text, spans, source.substring(previous), previous, false);
        return new View(text.toString(), List.copyOf(spans));
    }

    private static Set<Integer> includeOffsets(String text) {
        if (!INCLUDE.matcher(text).find()) return Set.of();
        var lexer = new SppLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        Set<Integer> offsets = new HashSet<>();
        for (Token token : lexer.getAllTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getText().equals("include"))
                offsets.add(text.offsetByCodePoints(0, token.getStartIndex()));
        }
        return offsets;
    }

    private String expandFragment(Path caller, String name, String arguments, Set<Path> active) throws IOException {
        Path fragment = find(caller, name);
        if (!active.add(fragment)) throw new IOException("Cyclic statement include: " + name);
        try {
            String body = Files.readString(fragment).stripTrailing();
            if (arguments != null) {
                Map<String, String> bindings = new HashMap<>();
                for (String argument : arguments.split(",", -1)) {
                    String[] pair = argument.strip().split("=", -1);
                    if (pair.length != 2 || !WORD.matcher(pair[0]).matches() || pair[1].isEmpty())
                        throw new IOException("Invalid statement include binding: " + argument.strip());
                    bindings.put(pair[0], pair[1]);
                }
                body = WORD.matcher(body).replaceAll(word -> bindings.getOrDefault(word.group(), word.group()));
            }
            StringBuilder expanded = new StringBuilder();
            var matcher = INCLUDE.matcher(body);
            Set<Integer> includes = includeOffsets(body);
            while (matcher.find()) {
                if (!includes.contains(matcher.end(1))) continue;
                String nested = expandFragment(fragment, matcher.group(2), matcher.group(3), active);
                matcher.appendReplacement(expanded, java.util.regex.Matcher.quoteReplacement(
                        nested.indent(matcher.group(1).length()).stripTrailing()));
            }
            return matcher.appendTail(expanded).toString();
        } finally {
            active.remove(fragment);
        }
    }

    private Path find(Path caller, String name) throws IOException {
        var candidates = fragments.getOrDefault(name + ".spp", List.of());
        if (candidates.isEmpty()) throw new IOException("Missing statement fragment: " + name);
        for (Path parent = caller.toAbsolutePath().getParent(); parent != null; parent = parent.getParent()) {
            Path ancestor = parent;
            var nearby = candidates.stream().filter(path -> path.toAbsolutePath().startsWith(ancestor)).toList();
            if (nearby.size() == 1) return nearby.getFirst();
            if (nearby.size() > 1) break;
        }
        throw new IOException("Ambiguous statement fragment: " + name);
    }

    private static void append(StringBuilder text, List<Span> spans, String part, int sourceStart, boolean expanded) {
        spans.add(new Span(text.length(), text.length() + part.length(), sourceStart, expanded));
        text.append(part);
    }
}
