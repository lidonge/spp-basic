package com.servpp.mustache.handler;

import com.servpp.mustache.antlr.*;
import com.servpp.mustache.model.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class MustacheListenerImpl extends MustacheBaseListener {
    private Template template;
    private URI mustacheFile;

    public MustacheListenerImpl(URI mustacheFile) {
        super();
        template = new Template(mustacheFile);
        this.mustacheFile = mustacheFile;
    }

    public Template getTemplate() {
        return template;
    }

    Stack<Object> stacks = new Stack<>();
    @Override
    public void enterTemplate(MustacheParser.TemplateContext ctx) {
        stacks.push(template);
    }

    @Override
    public void exitTemplate(MustacheParser.TemplateContext ctx) {
        stacks.pop();
    }

    @Override
    public void enterVariable(MustacheParser.VariableContext ctx) {
        Template tpl = (Template) stacks.peek();
        Variable variable = new Variable();
        variable.setVarName(skipWS((MustacheParser.QualifiedNameContext) ctx.getChild(1)));
        tpl.addBlock(variable);
    }

    private String skipWS(MustacheParser.QualifiedNameContext ctx){
        String ret = "";
        for(int i = 0;i< ctx.getChildCount();i++){

            ParseTree child = ctx.getChild(i);
            if(child instanceof TerminalNode){
                int tokenType = ((TerminalNode) child).getSymbol().getType();
                if(tokenType == MustacheLexer.WS){
                    continue;
                }
            }
            String text = child.getText();
            ret += text;
        }
        return ret;
    }
    @Override
    public void exitVariable(MustacheParser.VariableContext ctx) {
    }

    @Override
    public void enterText(MustacheParser.TextContext ctx) {
        Template tpl = (Template) stacks.peek();
        tpl.addBlock(new Text().setText(ctx.getText()));
    }

    @Override
    public void exitText(MustacheParser.TextContext ctx) {

    }

    @Override
    public void enterComment(MustacheParser.CommentContext ctx) {
        Template tpl = (Template) stacks.peek();
        tpl.addBlock(new Comment().setComment(ctx.getText()));
    }

    @Override
    public void exitComment(MustacheParser.CommentContext ctx) {

    }

    @Override
    public void enterInvertedSection(MustacheParser.InvertedSectionContext ctx) {
        Template tpl = (Template) stacks.peek();
        InvertedSection invertedSection = new InvertedSection();
        _addSection(tpl,invertedSection);
    }

    @Override
    public void exitInvertedSection(MustacheParser.InvertedSectionContext ctx) {
        Template sub = (Template) stacks.pop();
        InvertedSection invertedSection = (InvertedSection) stacks.pop();
    }

    @Override
    public void enterSection(MustacheParser.SectionContext ctx) {
        Template tpl = (Template) stacks.peek();
        Section section = new Section();
        _addSection(tpl, section);
    }

    private void _addSection(Template tpl, BaseSection section) {
        tpl.addBlock(section);
        stacks.push(section);
        Template sub = new Template(this.mustacheFile);
        section.setSubTemplate(sub);
        stacks.push(sub);
    }

    @Override
    public void exitSection(MustacheParser.SectionContext ctx) {
        Template sub = (Template) stacks.pop();
        Section section = (Section) stacks.pop();
    }

    @Override
    public void enterSectionBeg(MustacheParser.SectionBegContext ctx) {
        BaseSection section = getBaseSection();
        if(section instanceof Section){
            if(ctx.getChild(1).getText().equals("@")){
                ((Section) section).setMapAsList(true);
            }
        }

    }

    private BaseSection getBaseSection() {
        Template sub = (Template) stacks.pop();
        BaseSection section = (BaseSection) stacks.peek();
        stacks.push(sub);
        return section;
    }

    @Override
    public void exitSectionBeg(MustacheParser.SectionBegContext ctx) {

    }

    @Override
    public void enterSectionVar(MustacheParser.SectionVarContext ctx) {
        BaseSection section = getBaseSection();
        String name = section.getSectionName();
        String newName = null;
        ParseTree child = ctx.getChild(0);
        if(child.getText().trim().equals("$")) {
            section.setExprSection(true);
            child = ctx.getChild(1);
        }
        if(child instanceof MustacheParser.QualifiedNameContext) {
            newName = skipWS((MustacheParser.QualifiedNameContext) child);
        }else
            newName = child.getText();
        if(name != null && !name.equals(newName))
            throw new RuntimeException("Section "+name + "end incorrect at " + ctx.getStart().getLine());
        if(name == null)
            section.setSectionName(newName);
    }

    @Override
    public void exitSectionVar(MustacheParser.SectionVarContext ctx) {

    }

    @Override
    public void enterSectionContent(MustacheParser.SectionContentContext ctx) {

    }

    @Override
    public void exitSectionContent(MustacheParser.SectionContentContext ctx) {
    }

    @Override
    public void enterSectionEnd(MustacheParser.SectionEndContext ctx) {
        BaseSection section = getBaseSection();
        if(section instanceof Section){
            if(ctx.getChild(1).getText().equals("%")){
                ((Section) section).setRecursive(true);
            }
        }
    }

    @Override
    public void exitSectionEnd(MustacheParser.SectionEndContext ctx) {

    }

    @Override
    public void enterPartial(MustacheParser.PartialContext ctx) {
        Template tpl = (Template) stacks.peek();
        Partial partial = new Partial();
        stacks.push(partial);
        partial.setPartialName(skipWS((MustacheParser.QualifiedNameContext) ctx.getChild(2)));
        tpl.addBlock(partial);
    }

    @Override
    public void exitPartial(MustacheParser.PartialContext ctx) {
        Partial partial = (Partial) stacks.pop();
    }

    @Override
    public void enterPartialReturns(MustacheParser.PartialReturnsContext ctx) {
        List<MustacheParser.ParameterContext> parameter = ctx.parameter();
        createPartialParameters(parameter, true);
    }

    @Override
    public void exitPartialReturns(MustacheParser.PartialReturnsContext ctx) {
        List<Parameter> parameterList = (List<Parameter>) stacks.pop();
    }

    @Override
    public void enterParameters(MustacheParser.ParametersContext ctx) {
        List<MustacheParser.ParameterContext> parameter = ctx.parameter();
        createPartialParameters(parameter,false);
    }

    @Override
    public void exitParameters(MustacheParser.ParametersContext ctx) {
        List<Parameter> parameterList = (List<Parameter>) stacks.pop();
    }

    private void createPartialParameters(List<MustacheParser.ParameterContext> parameter, boolean isReturns) {
        Partial partial = (Partial) stacks.peek();
        List<Parameter> parameterList = new ArrayList<>();
        stacks.push(parameterList);
        if(parameter != null && parameter.size() != 0){
            if(isReturns)
                partial.setReturnsList(parameterList);
            else
                partial.setParameterList(parameterList);
        }
    }

    @Override
    public void exitParameter(MustacheParser.ParameterContext ctx) {
        List<Parameter> parameterList =(List<Parameter>) stacks.peek();
        Parameter parameter = new Parameter();
        parameter.setParamId(ctx.getText());
        parameterList.add(parameter);
    }

    @Override
    public void enterSectionIndex(MustacheParser.SectionIndexContext ctx) {
        Template tpl = (Template) stacks.peek();
        SectionIndex index = new SectionIndex();
        tpl.addBlock(index);
    }

    @Override
    public void exitSectionIndex(MustacheParser.SectionIndexContext ctx) {

    }

    @Override
    public void enterSectionRecursive(MustacheParser.SectionRecursiveContext ctx) {
        Template tpl = (Template) stacks.peek();
        Recursive variable = new Recursive();
        variable.setRecursiveName(skipWS((MustacheParser.QualifiedNameContext) ctx.getChild(2)));
        tpl.addBlock(variable);
    }

    @Override
    public void exitSectionRecursive(MustacheParser.SectionRecursiveContext ctx) {

    }


    @Override
    public void enterMultiexpr(MustacheParser.MultiexprContext ctx) {
        Template tpl = (Template) stacks.peek();
        MultiExpr expr = new MultiExpr();
        expr.setMultiExpr(ctx.getText(), ctx.getStart().getLine());
        tpl.addBlock(expr);
    }

    @Override
    public void exitMultiexpr(MustacheParser.MultiexprContext ctx) {

    }
}
