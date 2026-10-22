package com.servpp.lang;

import com.servpp.lang.general.GeneratorErrorListener;
import com.servpp.lang.general.IAntlrParserExecutor;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public abstract class AntlrCompiler {
    File antlrFile;
    public AntlrCompiler(File antlrFile) {
        this.antlrFile = antlrFile;
    }

    public abstract ParseTreeListener compile() throws IOException;

    protected abstract Parser getParser() throws IOException;

    protected void parseFile(Parser sppParser, IAntlrParserExecutor executor, ParseTreeListener listener)
            throws IOException {
        sppParser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        sppParser.removeErrorListeners();
        sppParser.addErrorListener(new GeneratorErrorListener(antlrFile));

        ParseTree tree = null;
        try {
            tree = executor.execute(); // STAGE 1
//            System.out.println(tree.toStringTree());
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse " + antlrFile + ".", ex);
        }
        if (sppParser.getNumberOfSyntaxErrors() > 0) {
            throw new IOException("Failed to parse " + antlrFile + " with "
                    + sppParser.getNumberOfSyntaxErrors() + " syntax error(s).");
        }
        if (tree == null) {
            throw new IOException("Failed to parse " + antlrFile + ": parser returned no parse tree.");
        }

        ParseTreeWalker walker = createWalker();


        walker.walk(listener, tree);
    }

    protected ParseTreeWalker createWalker() { return new ParseTreeWalker(); }

    protected CharStream getCharStream() throws IOException {
        FileInputStream reader = new FileInputStream(antlrFile);
        byte[] bytes = reader.readAllBytes();
        reader.close();
        String sql = new String(bytes);
        CharStream stream = new ANTLRInputStream(sql);
        return stream;
    }
}
