package com.servpp.lang.general.app;

import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.app.RuleBlock;

import java.io.File;
import java.util.Stack;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class RuleBlockHandler extends BaseHandler {
    File antlrFile;
    SppDomain sppDomain;
    IContainer currentContainer;
    private Stack stack = new Stack();

    @Override
    public void push(Object obj) {
        stack.push(obj);
    }

    @Override
    public Object pop() {
        return stack.pop();
    }

    @Override
    public Object peek() {
        return stack.peek();
    }

    @Override
    public IContainer getCurrentContainer(){
        return currentContainer;
    }

    @Override
    public void setCurrentContainer(IContainer container){
        this.currentContainer = container;
    }

    public RuleBlockHandler(File antlrFile) {
        this.antlrFile = antlrFile;
    }

    public SppDomain getSppDomain() {
        return sppDomain;
    }

    public void setSppDomain(SppDomain sppDomain) {
        this.sppDomain = sppDomain;
    }

    @Override
    public File getAntlrFile() {
        return antlrFile;
    }

    @Override
    public void logSppError(int line, int charPositionInLine, String msg) {
        getLogger().error(getAntlrFile() + ":" + line + ":" + (charPositionInLine + 1));
        getLogger().error(msg);
        if (sppDomain != null && msg != null) {
            sppDomain.addAppParseError(getAntlrFile() + ":" + line + ":" + (charPositionInLine + 1)
                    + ": " + msg);
        }
    }

    @Override
    public RuleBlock getCurrentRuleBlock() {
        return sppDomain.getRuleBlock();
    }

    @Override
    public SppDomain getSppDomian() {
        return sppDomain;
    }
}
