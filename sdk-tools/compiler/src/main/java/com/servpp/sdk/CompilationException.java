package com.servpp.sdk;

/** Local compiler diagnostic; contains no account or membership policy. */
public final class CompilationException extends Exception {
    private final String code;
    public CompilationException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
