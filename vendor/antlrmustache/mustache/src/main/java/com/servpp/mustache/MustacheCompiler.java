package com.servpp.mustache;

import com.servpp.antlr4.AntlrCompiler;
import com.servpp.mustache.antlr.*;
import com.servpp.mustache.handler.MustacheListenerImpl;
import org.antlr.v4.runtime.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class MustacheCompiler extends AntlrCompiler {
    public MustacheCompiler(File cobolFile) {
        super(cobolFile);
    }

    public MustacheCompiler(URL url) throws URISyntaxException {
        super(url.toURI());
    }

    @Override
    public Lexer createLexer(CharStream stream) {
        return new MustacheLexer(stream);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new MustacheParser(tokens);
    }

    @Override
    public ParserRuleContext executeParser(Parser parser) {
        return ((MustacheParser)parser).template();
    }
}
