package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class Text implements IMustacheBlock{
    private String text;

    public String getText() {
        return text;
    }

    public Text setText(String text) {
        this.text = text;
        return this;
    }
}
