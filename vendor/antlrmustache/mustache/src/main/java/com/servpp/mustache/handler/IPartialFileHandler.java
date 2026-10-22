package com.servpp.mustache.handler;

import com.servpp.mustache.model.Template;

/**
 * @author lidong@date 2024-07-29@version 1.0
 */
public interface IPartialFileHandler {
    Template compilePartialTemplate(String partialName);
}
