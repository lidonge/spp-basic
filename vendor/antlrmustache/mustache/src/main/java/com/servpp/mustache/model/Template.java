package com.servpp.mustache.model;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class Template {
    public Template(URI templateFile) {
        this.templateFile = templateFile;
    }

    private URI templateFile;
    private List<IMustacheBlock> blocks = new ArrayList<>();

    public void addBlock(IMustacheBlock block){
        blocks.add(block);
    }

    public List<IMustacheBlock> getBlocks() {
        return blocks;
    }

    public URI getTemplateFile() {
        return templateFile;
    }
}
