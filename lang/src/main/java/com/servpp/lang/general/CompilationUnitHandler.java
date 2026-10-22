package com.servpp.lang.general;

import com.servpp.lang.models.SppDomain;

import java.io.File;
import java.util.Stack;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class CompilationUnitHandler extends BaseHandler {
    private Stack stack = new Stack();
    private SppDomain sppDomain ;
    private File sppFile;

    public void setSppFile(File sppFile) {
        this.sppFile = sppFile;
    }

    @Override
    public File getAntlrFile() {
        return sppFile;
    }

    @Override
    public SppDomain getSppDomain() {
        return sppDomain;
    }

    @Override
    public void setSppDomain(SppDomain sppDomain) {
        this.sppDomain = sppDomain;
    }

    @Override
    public void push(Object obj) {
//        System.out.println("push:" +obj);
        stack.push(obj);
    }

    @Override
    public Object pop() {
        Object obj = stack.pop();
//        System.out.println("pop:" +obj);

        return obj;
    }

    @Override
    public Object peek() {
        return stack.peek();
    }

    @Override
    public int stackSize() {
        return stack.size();
    }
}
