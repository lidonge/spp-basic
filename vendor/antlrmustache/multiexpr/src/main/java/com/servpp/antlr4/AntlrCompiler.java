package com.servpp.antlr4;

import com.servpp.logger.ILogable;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public abstract class AntlrCompiler implements ILogable {
    private URI antlrUri;

    private Parser parser;
    private ParseTree tree;
    public AntlrCompiler(URI antlrUri) {
        this.antlrUri = antlrUri;
    }

    public AntlrCompiler(File antlrFile) {
        this(antlrFile.toURI());
    }
    public abstract Lexer createLexer(CharStream stream);
    public abstract Parser createParser(CommonTokenStream tokens);
    public  abstract ParserRuleContext executeParser(Parser parser);

    public ParseTree getTree() {
        return tree;
    }

    public void compileAntlr4()  throws IOException{
        compileAntlr4(null);
    }
    public void compileAntlr4(ANTLRErrorListener errorListener)  throws IOException{
        CharStream stream = getCharStream();
        Lexer lexer = createLexer(stream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        parser = createParser(tokens);
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.removeErrorListeners();
        lexer.removeErrorListeners();;
        if(errorListener == null)
            errorListener = new GeneratorErrorListener(antlrUri);
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);

        try {
            tree = executeParser(parser); // STAGE 1
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public void workListener(ParseTreeListener listener){
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);
    }

    private CharStream getCharStream() throws IOException {
        String str = getAntlrText();
        CharStream stream = new ANTLRInputStream(str);
        return stream;
    }

    protected String getAntlrText() throws IOException {
        InputStream reader = getInputStreamFromURI(antlrUri);
        byte[] bytes = reader.readAllBytes();
        reader.close();
        String str = new String(bytes);
        return str;
    }

    private static InputStream getInputStreamFromURI(URI uri) throws IOException {
        switch (uri.getScheme()) {
            case "file":
                // 处理文件 URI
                return getInputStreamFromFile(uri);
            case "http":
            case "https":
                // 处理 HTTP/HTTPS URI
                return getInputStreamFromHttp(uri);
            case "jar":
                // 处理 JAR 文件中的资源 URI
                return getInputStreamFromJar(uri);
            default:
                throw new IllegalArgumentException("Unsupported URI scheme: " + uri.getScheme());
        }
    }

    private static InputStream getInputStreamFromFile(URI uri) throws IOException {
        File file = new File(uri);
        return Files.newInputStream(Paths.get(file.toURI()));
    }

    private static InputStream getInputStreamFromHttp(URI uri) throws IOException {
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return connection.getInputStream();
    }

    private static InputStream getInputStreamFromJar(URI uri) throws IOException {
        InputStream inputStream = uri.toURL().openStream();
        if (inputStream == null) {
            throw new IOException("Resource not found: " + uri);
        }
        return inputStream;
    }
}
