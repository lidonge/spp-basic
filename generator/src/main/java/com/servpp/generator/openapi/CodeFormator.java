package com.servpp.generator.openapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class CodeFormator {

    public static String formatCode(String code) {
        BufferedReader reader = new BufferedReader(new StringReader(code));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) {

                } else {
                    sb.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to format generated code.", e);
        }
        return sb.toString();
    }

}
