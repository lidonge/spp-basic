package com.servpp.mustache;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class CodeFormator {

    public static final String LINE_CONTINUE = "//-";
    private static final String INDENT = "    ";

    public static String formatCode(String code) {

        StringBuilder formattedCode = new StringBuilder();
        String[] lines = code.split("\n");
        int indentLevel = 0;
        boolean inMultilineComment = false;
        boolean inShortenLine = false;
        String tempLine = "";

        for (String line : lines) {
            line = line.trim();

            // Skip empty lines
            if (line.isEmpty()) {
                continue;
            }

            // Handle multiline comments
            if (line.startsWith("/*")) {
                inMultilineComment = true;
            }

            if (inMultilineComment) {
                formattedCode.append(line).append("\n");
                if (line.endsWith("*/")) {
                    inMultilineComment = false;
                }
                continue;
            }

            if (!inShortenLine && isShortenLine(line)) {
                inShortenLine = true;
                tempLine = null;
            }
            if (inShortenLine) {
                tempLine = tempLine == null ? line : tempLine + " " + line;
                if (tempLine.endsWith(LINE_CONTINUE)) {
                    tempLine = tempLine.substring(0, tempLine.length() - LINE_CONTINUE.length());
                }
                if (isShortenLine(line))
                    continue;
                inShortenLine = false;
                line = tempLine;
            }

            int leadingClosingBraces = countLeadingClosingBraces(line);
            int openBraces = countUnquotedChar(line, '{');
            int closeBraces = countUnquotedChar(line, '}');
            int effectiveIndent = Math.max(0, indentLevel - leadingClosingBraces);

            // Add the line with the current indent level
            addIndentedLine(formattedCode, line, effectiveIndent);

            indentLevel = Math.max(0, indentLevel + openBraces - closeBraces);
        }

        return formattedCode.toString();
    }

    private static boolean isShortenLine(String line) {
        return line.endsWith(LINE_CONTINUE) || 
                !(
                    line.startsWith("@") || 
                    line.startsWith("//") ||
                    line.endsWith(";") || 
                    line.endsWith("{") ||
                    line.endsWith("}")
                );
    }


    private static void addIndentedLine(StringBuilder formattedCode, String line, int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            formattedCode.append(INDENT);
        }

        formattedCode.append(line).append("\n");

    }

    private static int countLeadingClosingBraces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (current == '}') {
                count++;
                continue;
            }
            break;
        }
        return count;
    }

    private static int countUnquotedChar(String line, char target) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;
        int count = 0;

        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && current == target) {
                count++;
            }
        }

        return count;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: CodeFormator <source-file>");
        try (FileInputStream fin = new FileInputStream(args[0])) {
            String s = formatCode(new String(fin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            System.out.println(s);
        }
    }
}
