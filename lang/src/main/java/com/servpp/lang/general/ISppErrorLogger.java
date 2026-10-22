package com.servpp.lang.general;

import org.antlr.v4.runtime.ParserRuleContext;

import java.io.File;
import com.servpp.ILogable;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public interface ISppErrorLogger extends ILogable{
    File getAntlrFile();

    default void logSppError(int line, int charPositionInLine, String msg) {
        getLogger().error(getAntlrFile() + ":" + line + ":" + (charPositionInLine + 1));
        getLogger().error(msg);
    }

    default void logSppError(ParserRuleContext ctx, String msg) {
        if(msg != null)
            logSppError(ctx.getStart().getLine(),ctx.getStart().getCharPositionInLine(),msg);
    }
}
