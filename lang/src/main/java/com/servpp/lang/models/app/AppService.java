package com.servpp.lang.models.app;

import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.app.expr.IJavaStatement;
import com.servpp.lang.models.app.expr.IOperationExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class AppService  implements INamedObject, IContainer {
    private String name;
    private SppService sppService;

    private List<IOperationExpression> expressions = new ArrayList<>();
    private List<IJavaStatement> javaStatements = new ArrayList<>();

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private void addExpression(IOperationExpression expression){
        expressions.add(expression);
        javaStatements.add(expression);
    }

    public void addJavaStatement(IJavaStatement statement) {
        javaStatements.add(statement);
    }

    public List<IOperationExpression> getAppExpressions() {
        return expressions;
    }

    public List<IJavaStatement> getJavaStatements() {
        return javaStatements;
    }

    public SppService getSppService() {
        return sppService;
    }

    public void setSppService(SppService sppService) {
        this.sppService = sppService;
    }

    @Override
    public IContainer getParent() {
        return null;
    }

    @Override
    public void setParent(IContainer parent) {

    }

    @Override
    public void _add(IComponent component) {
        addExpression((IOperationExpression) component);
    }

    @Override
    public boolean isContains(IComponent component) {
        return expressions.contains(component);
    }
}
