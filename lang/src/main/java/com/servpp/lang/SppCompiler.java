package com.servpp.lang;

import com.servpp.lang.general.IConstance.CompilationUnitType;
import com.servpp.lang.general.SppGeneralHandler;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.io.*;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class SppCompiler extends AntlrCompiler {
    SppProject sppProject;
    public SppCompiler(File sppFile,
                       SppProject sppProject) {
        super(sppFile);
        this.sppProject = sppProject;
    }

    @Override
    protected CharStream getCharStream() throws IOException {
        return org.antlr.v4.runtime.CharStreams.fromPath(antlrFile.toPath());
    }

    @Override
    protected Parser getParser() throws IOException {
        CharStream stream = getCharStream();
        SppLexer lexer = new SppLexer(stream);
        var lexicalErrors = new StringBuilder();
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line,
                                              int column, String message, RecognitionException failure) {
                com.servpp.lang.lint.CompilerDiagnostics.error("SPP_SYNTAX_ERROR", antlrFile, line, column + 1,
                        message, "Replace or remove the unrecognized input.");
                lexicalErrors.append(System.lineSeparator()).append(antlrFile).append(':').append(line)
                        .append(':').append(column + 1).append(' ').append(message);
            }
        });
        var tokens = new CommonTokenStream(lexer);
        tokens.fill();
        if (lexicalErrors.length() != 0) {
            throw new IOException("SPP lexical error(s):" + lexicalErrors);
        }
        return new SppParser(tokens);
    }
    public SppParser.ProgramContext parse() throws IOException {
        var parser = (SppParser) getParser();
        return (SppParser.ProgramContext) parseTree(parser, parser::program);
    }

    public void declareParticipantTypes(SppParser.ProgramContext tree) {
        var domain = sppProject.addDomain(ParticipantNames.domain(tree));
        for (var unit : tree.compilationUnits().compilationUnit()) {
            if (unit.entity() != null) {
                var entity = unit.entity();
                var kind = "Reference".equals(entity.getStart().getText())
                        ? CompilationUnitType.reference
                        : CompilationUnitType.entity;
                var type = domain.getSppClass(kind, entity.Identifier().getText());
                if (!type.isReal()) type.setType(kind);
            } else if (unit.role() != null) {
                var role = unit.role();
                var kind = "Contract".equals(role.getStart().getText())
                        ? CompilationUnitType.contract
                        : CompilationUnitType.role;
                var type = domain.getSppClass(kind, role.Identifier().getText());
                if (!type.isReal()) type.setType(kind);
            }
        }
    }

    public SppGeneralHandler compile(SppParser.ProgramContext tree, ParticipantNames names) {
        var listener = new SppGeneralHandler(sppProject);
        listener.setSppFile(antlrFile);
        sppProject.addDomain(ParticipantNames.domain(tree)).setParticipantNames(names);
        names.bind(tree);
        walkTree(listener, tree);
        return listener;
    }

    @Override
    public ParseTreeListener compile() throws IOException {
        var tree = parse();
        var names = new ParticipantNames();
        names.add(tree);
        declareParticipantTypes(tree);
        return compile(tree, names);
    }
}
