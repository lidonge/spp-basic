package com.servpp.sdk.editor;

import com.servpp.lang.antlr.AppLexer;
import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class SourceDocument {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("spp", "enum", "app", "phy", "rdb");
    public record Problem(Range range, String message) { }

    private final String uri;
    private final int version;
    private final String text;
    private final int[] lineStarts;
    private final int[] codePointOffsets;
    private final CommonTokenStream tokens;
    private final Parser parser;
    private final ParserRuleContext tree;
    private final List<Problem> problems;

    public SourceDocument(String uri, int version, String text) {
        String pathname = URI.create(uri).getPath();
        int separator = pathname == null ? -1 : pathname.lastIndexOf('.');
        String extension = separator < 0 ? "" : pathname.substring(separator + 1);
        if (!EXTENSIONS.contains(extension)) throw new IllegalArgumentException("Unsupported S++ source extension.");
        if (text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("S++ source exceeds the 1 MiB editor limit.");
        this.uri = uri;
        this.version = version;
        this.text = text;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int offset = 0; offset < text.length(); offset++) if (text.charAt(offset) == '\n') starts.add(offset + 1);
        lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        codePointOffsets = new int[text.codePointCount(0, text.length()) + 1];
        int utf16 = 0;
        for (int offset = 0; offset < codePointOffsets.length - 1; offset++) {
            codePointOffsets[offset] = utf16;
            utf16 += Character.charCount(text.codePointAt(utf16));
        }
        codePointOffsets[codePointOffsets.length - 1] = text.length();
        List<Problem> errors = new ArrayList<>();
        BaseErrorListener collector = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                              int column, String message, RecognitionException failure) {
                if (errors.size() >= 100) return;
                Range range;
                if (offendingSymbol instanceof Token token) range = range(token);
                else {
                    int lineStart = lineStarts[Math.max(0, Math.min(line - 1, lineStarts.length - 1))];
                    int codePoints = text.codePointCount(0, lineStart) + column;
                    Position position = position(utf16Offset(codePoints));
                    range = new Range(position, position);
                }
                errors.add(new Problem(range, message));
            }
        };
        boolean business = extension.equals("spp") || extension.equals("enum");
        var stream = CharStreams.fromString(text.endsWith("\n") ? text : text + "\n");
        Lexer lexer = business ? new SppLexer(stream) : new AppLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(collector);
        tokens = new CommonTokenStream(lexer);
        parser = business ? new SppParser(tokens) : new AppParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(collector);
        try {
            tree = business ? ((SppParser) parser).program() : ((AppParser) parser).program();
        } catch (StackOverflowError failure) {
            throw new IllegalArgumentException("S++ editor syntax nesting exceeds parser limits.");
        }
        if (tokens.LA(1) != Token.EOF) errors.add(new Problem(range(tokens.LT(1)), "Unexpected input after program."));
        tokens.fill();
        problems = List.copyOf(errors);
    }

    public String uri() { return uri; }
    public int version() { return version; }
    public String text() { return text; }
    public ParserRuleContext tree() { return tree; }
    public Parser parser() { return parser; }
    public List<Token> tokens() { return List.copyOf(tokens.getTokens()); }
    public List<Problem> problems() { return problems; }
    public String domain() {
        if (tree instanceof SppParser.ProgramContext program)
            return program.servicedomain() == null || program.servicedomain().domainname() == null ? ""
                    : program.servicedomain().domainname().getText();
        var domain = ((AppParser.ProgramContext) tree).serviceDomain();
        return domain == null || domain.domainName() == null ? "" : domain.domainName().getText();
    }

    public int utf16Offset(int codePointOffset) {
        return codePointOffsets[Math.max(0, Math.min(codePointOffset, codePointOffsets.length - 1))];
    }

    public Position position(int utf16Offset) {
        int offset = Math.max(0, Math.min(utf16Offset, text.length()));
        int found = Arrays.binarySearch(lineStarts, offset);
        int line = found >= 0 ? found : -found - 2;
        return new Position(line, offset - lineStarts[line]);
    }

    public int offset(Position position) {
        if (position.getLine() < 0 || position.getLine() >= lineStarts.length || position.getCharacter() < 0)
            throw new IllegalArgumentException("Invalid document position.");
        int start = lineStarts[position.getLine()];
        int end = position.getLine() + 1 < lineStarts.length ? lineStarts[position.getLine() + 1] - 1 : text.length();
        if (end > start && text.charAt(end - 1) == '\r') end--;
        if (position.getCharacter() > end - start) throw new IllegalArgumentException("Invalid document position.");
        return start + position.getCharacter();
    }

    public Range range(Token token) {
        int start = utf16Offset(token.getStartIndex());
        int end = token.getType() == Token.EOF ? start : utf16Offset(token.getStopIndex() + 1);
        return new Range(position(start), position(Math.max(start, end)));
    }

    public Range range(ParserRuleContext context) {
        Token start = context.getStart();
        Token stop = context.getStop();
        return new Range(range(start).getStart(), stop == null || stop.getStopIndex() < start.getStartIndex()
                ? range(start).getEnd() : range(stop).getEnd());
    }

    public Token identifierAt(Position position) {
        int cursor = offset(position);
        Token boundary = null;
        for (Token token : tokens.getTokens()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) continue;
            if (!"Identifier".equals(parser.getVocabulary().getSymbolicName(token.getType()))
                    && !(parser instanceof AppParser && token.getText().equals("commit"))) continue;
            int start = utf16Offset(token.getStartIndex());
            int end = utf16Offset(token.getStopIndex() + 1);
            if (cursor >= start && cursor < end) return token;
            if (cursor == end) boundary = token;
        }
        return boundary;
    }
}
