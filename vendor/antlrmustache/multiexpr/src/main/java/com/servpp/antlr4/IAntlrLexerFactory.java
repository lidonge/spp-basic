package com.servpp.antlr4;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

/**
 * @author lidong@date 2024-10-14@version 1.0
 */
public interface IAntlrLexerFactory {
    Lexer createLexer(CharStream stream);
}
