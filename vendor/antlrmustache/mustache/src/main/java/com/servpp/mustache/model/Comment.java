package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class Comment implements IStatement{
    private String comment;

    public String getComment() {
        return comment;
    }

    public Comment setComment(String comment) {
        this.comment = comment;
        return this;
    }
}
