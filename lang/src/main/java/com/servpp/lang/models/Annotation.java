package com.servpp.lang.models;

import com.servpp.lang.models.app.AnnotationDefine;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-12-05@version 1.0
 */
public class Annotation implements IAnnotation {
    protected List<AnnotationDefine> annotations = new ArrayList<>();

    public List<AnnotationDefine> getAnnotations() {
        return annotations;
    }

    @Override
    public void setAnnotations(List<AnnotationDefine> annotations) {
        this.annotations = annotations;
    }
}
