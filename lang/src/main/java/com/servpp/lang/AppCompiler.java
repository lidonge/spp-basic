package com.servpp.lang;

import com.servpp.lang.general.app.AppGeneralHandler;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.antlr.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.io.File;
import java.io.IOException;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class AppCompiler extends AntlrCompiler{
    SppProject sppProject;
    public AppCompiler(File antlrFile, SppProject sppProject) {
        super(antlrFile);
        this.sppProject = sppProject;
    }

    @Override
    protected Parser getParser() throws IOException {
        CharStream stream = getCharStream();
        AppLexer lexer1 = new AppLexer(stream);
        CommonTokenStream tokens1 = new CommonTokenStream(lexer1);
        AppParser parser1 = new AppParser(tokens1);
        return parser1;
    }

    @Override protected org.antlr.v4.runtime.tree.ParseTreeWalker createWalker() {
        return new org.antlr.v4.runtime.tree.ParseTreeWalker() {
            @Override public void walk(ParseTreeListener listener, org.antlr.v4.runtime.tree.ParseTree tree) {
                if (tree instanceof AppParser.PhyEnvironmentContext environment
                        && !environment.Identifier().getText().equals(System.getProperty("spp.environment", "develop"))) return;
                super.walk(listener, tree);
            }
        };
    }

    @Override
    public ParseTreeListener compile() throws IOException {
        AppParser appParser = (AppParser) getParser();
        AppGeneralHandler listener = new AppGeneralHandler(antlrFile, sppProject);

        parseFile(appParser, ()->appParser.program(),listener);
        return listener;
    }
}
