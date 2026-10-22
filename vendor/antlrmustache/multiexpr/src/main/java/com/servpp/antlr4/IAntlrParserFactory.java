package com.servpp.antlr4;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;

/**
 * @author lidong@date 2024-10-14@version 1.0
 */
public interface IAntlrParserFactory {
    Parser createParser(CommonTokenStream tokens);
}
