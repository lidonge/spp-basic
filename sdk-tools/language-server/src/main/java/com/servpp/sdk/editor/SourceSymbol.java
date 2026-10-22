package com.servpp.sdk.editor;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

public record SourceSymbol(SourceDocument document, String name, String type, SymbolKind kind,
                           SourceSymbol owner, Token nameToken, ParserRuleContext context,
                           ParserRuleContext visibility, boolean implicit, String detail) {
    public Range selectionRange() { return document.range(nameToken); }
    public Range range() { return document.range(context); }
    public String domain() { return document.domain(); }
    public String environment() {
        for (ParserRuleContext parent = context; parent != null; parent = parent.getParent())
            if (parent instanceof com.servpp.lang.antlr.AppParser.PhyEnvironmentContext environment
                    && environment.Identifier() != null) return environment.Identifier().getText();
        return "";
    }
    public boolean visibleAt(int tokenIndex) {
        return visibility == null || tokenIndex >= visibility.getStart().getTokenIndex()
                && (visibility.getStop() == null || tokenIndex <= visibility.getStop().getTokenIndex());
    }
}
