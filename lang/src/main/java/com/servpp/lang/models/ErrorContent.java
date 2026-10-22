package com.servpp.lang.models;

/**
 * @author lidong@date 2023-11-02@version 1.0
 */
public class ErrorContent {
    String name;
    int line;
    int col;
    String msg;

    public ErrorContent() {
    }

    public ErrorContent(String name, int line, int col, String msg) {
        this.name = name;
        this.line = line;
        this.col = col;
        this.msg = msg;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
