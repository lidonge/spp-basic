package com.servpp.multiexpr;

import com.servpp.antlr4.AntlrCompiler;
import com.servpp.multiexpr.antlr.MultiExprLexer;
import com.servpp.multiexpr.antlr.MultiExprParser;
import org.antlr.v4.runtime.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * @author lidong@date 2024-10-14@version 1.0
 */
public class MultiExprCompiler extends AntlrCompiler {
    private String formula;
    public MultiExprCompiler(File antlrFile, String formula) {
        super(antlrFile);
        this.formula = formula;
    }
    public MultiExprCompiler(URI antlrFile, String formula) {
        super(antlrFile);
        this.formula = formula;
    }
    @Override
    protected String getAntlrText() throws IOException {
        return formula;
    }

    @Override
    public Lexer createLexer(CharStream stream) {
        return new MultiExprLexer(stream);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new MultiExprParser(tokens);
    }

    @Override
    public ParserRuleContext executeParser(Parser parser) {
        return ((MultiExprParser)parser).multiexpr();
    }
}
