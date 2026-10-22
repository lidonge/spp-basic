package com.servpp.mustache;

import com.servpp.mustache.antlr.MustacheLexer;
import com.servpp.mustache.antlr.MustacheParser;
import com.servpp.mustache.handler.MustacheListenerImpl;

import java.io.File;
import java.io.IOException;

/**
 * @author lidong@date 2024-07-24@version 1.0
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: Main <template.mustache>");
        test1(new File(args[0]));
    }




    private static void test1(File mustacheFile) throws IOException {
        MustacheCompiler mustacheCompiler = new MustacheCompiler(mustacheFile);
        mustacheCompiler.compileAntlr4(null);
        MustacheListenerImpl listener = new MustacheListenerImpl(mustacheFile.toURI());
        mustacheCompiler.workListener(listener);
    }
}
