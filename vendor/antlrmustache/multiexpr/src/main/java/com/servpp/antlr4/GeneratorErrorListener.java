package com.servpp.antlr4;

import com.servpp.logger.ILogable;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import java.net.URI;
import java.util.BitSet;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class GeneratorErrorListener implements ANTLRErrorListener, ILogable {
    URI file;
    int begLine = 0;
    public GeneratorErrorListener(URI file) {
        this(file,0);
    }

    public GeneratorErrorListener(URI file, int begLine) {
        this.file = file;
        this.begLine = begLine;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
//        getLogger().error(file + ":" + line + ":" + (charPositionInLine + 1));
        getLogger().error("file://{}:{}:{} {}",file.getPath() , begLine+line-1 , (charPositionInLine + 1), msg);
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        getLogger().error("reportAmbiguity {}:{} {}", startIndex, stopIndex , exact);
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        getLogger().error("reportAttemptingFullContext {}:{}",startIndex , stopIndex );
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
        getLogger().error("reportContextSensitivity {}:{} {}",startIndex, stopIndex,prediction);
    }
}
