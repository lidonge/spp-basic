package com.servpp.lang.lint;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import java.util.List;
import java.util.Locale;

/** JSON Lines wire format. Display locations are one-based; edit offsets are UTF-16. */
public record RepairDiagnostic(String code, String severity, String file, int line, int column,
                               String message, String cause, String fixHint,
                               List<Location> relatedLocations, List<RedundancyLint.Edit> edits, boolean safe) {
    public record Location(String file, int line, int column) { }

    public static RepairDiagnostic warning(List<RedundancyLint.Source> domain, RedundancyLint.Finding finding,
                                           boolean strict) {
        Token token = finding.token();
        String code = finding.code();
        List<RedundancyLint.Edit> edits;
        if (code.equals("SPP_REDUNDANT_PADDED_BLANK")) {
            int start = utf16Offset(token);
            edits = BlankEdits.equivalent(domain, finding)
                    ? List.of(new RedundancyLint.Edit(finding.path(), start, start + token.getText().length(), "BLANK"))
                    : List.of();
        } else if (code.equals("SPP_MISSING_READONLY") && token.getText().equals("Scenario")) {
            int start = utf16Offset(token);
            edits = List.of(new RedundancyLint.Edit(finding.path(), start, start, "readonly "));
        } else {
            edits = MechanicalEdits.forFinding(domain, finding);
        }
        String title = code.replaceFirst("^SPP_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        String cause = DiagnosticPages.rule(code, title);
        return new RepairDiagnostic(code, strict || RedundancyLint.ERROR_CODES.contains(code) ? "error" : "warning", finding.path(), token.getLine(),
                token.getCharPositionInLine() + 1, finding.message(), cause, finding.message(),
                related(domain, edits, finding), edits, !edits.isEmpty());
    }

    private static List<Location> related(List<RedundancyLint.Source> domain, List<RedundancyLint.Edit> edits,
                                          RedundancyLint.Finding finding) {
        if (edits.size() < 2) return List.of();
        var result = new java.util.ArrayList<Location>();
        for (var edit : edits) {
            var source = domain.stream().filter(entry -> entry.path().equals(edit.path())).findFirst().orElseThrow();
            var input = source.tree().getStart().getInputStream().toString();
            int line = 1, lineStart = 0;
            for (int i = 0; i < edit.start(); i++) if (input.charAt(i) == '\n') { line++; lineStart = i + 1; }
            if (!edit.path().equals(finding.path()) || line != finding.token().getLine())
                result.add(new Location(edit.path(), line, input.codePointCount(lineStart, edit.start()) + 1));
        }
        return List.copyOf(result);
    }

    public static int utf16Offset(Token token) {
        int index = token.getStartIndex();
        return index == 0 ? 0 : token.getInputStream().getText(Interval.of(0, index - 1)).length();
    }

    public static RepairDiagnostic error(String code, String file, int line, int column, String message, String hint) {
        return new RepairDiagnostic(code, "error", file, line, column, message, message, hint, List.of(), List.of(), false);
    }
}
