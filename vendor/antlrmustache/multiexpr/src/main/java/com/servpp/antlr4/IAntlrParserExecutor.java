package com.servpp.antlr4;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public interface IAntlrParserExecutor {
    ParserRuleContext execute(Parser parser);
}
