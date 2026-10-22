package com.servpp.multiexpr.handler;

/**
 * @author lidong@date 2024-07-30@version 1.0
 */

import com.servpp.antlr4.GeneratorErrorListener;
import com.servpp.logger.ILogable;
import com.servpp.multiexpr.IEvaluatorEnvironment;
import com.servpp.multiexpr.MathAndLogicUtil;
import com.servpp.multiexpr.MultiExprCompiler;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import com.servpp.multiexpr.antlr.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class ExprEvaluator extends MultiExprBaseVisitor<Object> implements ILogable {
    private IEvaluatorEnvironment environment = new DefaultEnvironment();
    private Map<String, ParseTree> parseTreeMap = new HashMap<>();
    public ExprEvaluator() {
    }

    public IEvaluatorEnvironment getEnvironment() {
        return environment;
    }

    public ExprEvaluator setEnvironment(IEvaluatorEnvironment environment) {
        this.environment = environment;
        return this;
    }

    public void setVar(String name, Object value) {
        environment.setVar(name, value);
    }

    public Object getVar(String name) {
        return environment.getVar(name);
    }
    public Function<Object[], Object> getFunction(String name) {
        return environment.getFunction(name);
    }

    @Override
    public Object visitStringExpr(MultiExprParser.StringExprContext ctx) {
        String result = visit(ctx.stringTerm(0)).toString();
        for (int i = 1; i < ctx.stringTerm().size(); i++) {
            result += visit(ctx.stringTerm(i)).toString();
        }
        return result;
    }

    @Override
    public Object visitStringTerm(MultiExprParser.StringTermContext ctx) {
        return fineString(ctx.getChild(0).getText());
    }

    @Override
    public Object visitMathExpr(MultiExprParser.MathExprContext ctx) {
        return visit(ctx.addExpr());
    }

    @Override
    public Object visitAddExpr(MultiExprParser.AddExprContext ctx) {
        Object result = visit(ctx.mulExpr(0));
        for (int i = 1; i < ctx.mulExpr().size(); i++) {
            if (ctx.getChild(2 * i - 1).getText().equals("+")) {
                Object arg = visit(ctx.mulExpr(i));
                if(result instanceof Number && arg instanceof Number)
                    result = MathAndLogicUtil.add((Number) result, (Number) arg);
                else
                    result = result.toString() + arg;
            } else if (ctx.getChild(2 * i - 1).getText().equals("-")) {
                result = MathAndLogicUtil.subtract((Number) result, (Number) visit(ctx.mulExpr(i)));
            }
        }
        return result;
    }

    @Override
    public Object visitMulExpr(MultiExprParser.MulExprContext ctx) {
        Object arg0 = visit(ctx.atom(0));
        Object ret = null;
        if (arg0 instanceof Number) {
            Number result = (Number) arg0;
            for (int i = 1; i < ctx.atom().size(); i++) {
                if (ctx.getChild(2 * i - 1).getText().equals("*")) {
                    result = MathAndLogicUtil.multiply(result, (Number) visit(ctx.atom(i)));
                } else if (ctx.getChild(2 * i - 1).getText().equals("/")) {
                    result = MathAndLogicUtil.divide(result, (Number) visit(ctx.atom(i)));
                }
            }
            ret = result;
        }else{
            if(ctx.atom().size() > 1){
                throw new RuntimeException("MulExpr shoud not use non-number arg:" + arg0);
            }
            ret = arg0 == null ? "null" : arg0;
        }
        return ret;
    }

    @Override
    public Object visitAtom(MultiExprParser.AtomContext ctx) {
        if (ctx.NUMBER() != null) {
            String text = ctx.NUMBER().getText();
            if(text.matches("-?\\d+"))
                return Integer.parseInt(text);
            return Double.parseDouble(text);
        } else if (ctx.STRING() != null) {
            return fineString(ctx.STRING().getText());
        } else if (ctx.qualifiedName() != null) {
            return environment.getVar(ctx.qualifiedName().getText());
        } else if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        } else {
            return visit(ctx.expr());
        }
    }

    private static String fineString(String s){
        return s.substring(1,s.length()-1)
                .replace("\\\"","\"")
                .replace("\\n","\n")
                .replace("\\r","\n")
                .replace("\\t","\t");
    }

    @Override
    public Object visitCondExpr(MultiExprParser.CondExprContext ctx) {
        boolean condition = (boolean) visit(ctx.logicExpr());
        return condition ? visit(ctx.expr(0)) : visit(ctx.expr(1));
    }

    @Override
    public Object visitLogicExpr(MultiExprParser.LogicExprContext ctx) {
        List<MultiExprParser.ComparisonExprContext> comparisonExprContexts = ctx.comparisonExpr();
        int len = comparisonExprContexts.size();
        boolean ret = false;
        for(int i = 0;i<len;i++){
            boolean val = Boolean.parseBoolean(visit(ctx.comparisonExpr(i))+"");
            if(i == 0)
                ret = val;
            else {
                String op = ctx.logicOp(i - 1).getText();
                switch(op){
                    case "||":
                        ret |= val;
                        break;
                    case "&&":
                        ret &=val;
                        break;
                    default:
                        break;
                }
            }

        }
        return ret;
    }

    @Override
    public Object visitYesComparisonExpr(MultiExprParser.YesComparisonExprContext ctx) {
        Object left = visit(ctx.addExpr(0));
        if (ctx.getChildCount() > 1) {
            String op = ctx.getChild(1).getText();
            Object right = visit(ctx.addExpr(1));
            return MathAndLogicUtil.compareNumberAndString(op,left,right);
        }
        return left;
    }

    @Override
    public Object visitNotComparisonExpr(MultiExprParser.NotComparisonExprContext ctx) {
        Boolean val = Boolean.parseBoolean(visit(ctx.yesComparisonExpr()).toString());
        return !val;
    }

    @Override
    public Object visitAssignment(MultiExprParser.AssignmentContext ctx) {
        String varName = ctx.qualifiedName().getText();
        Object value = visit(ctx.expr());
        environment.setVar(varName, value);
        return value;
    }

    @Override
    public Object visitFunctionCall(MultiExprParser.FunctionCallContext ctx) {
        String funcName = ctx.qualifiedName().getText();
        Function<Object[], Object> function = environment.getFunction(funcName);
        if (function == null) {
            throw new IllegalArgumentException("Unknown function: " + funcName);
        }

        // 解析函数参数
        int numArgs = ctx.expr().size();
        Object[] args = new Object[numArgs];
        for (int i = 0; i < numArgs; i++) {
            args[i] = visit(ctx.expr(i));
        }

        return function.apply(args);
    }

    @Override
    public Object visitMultiexpr(MultiExprParser.MultiexprContext ctx) {
        Object result = null;
        for (MultiExprParser.ExprContext exprCtx : ctx.expr()) {
            result = visit(exprCtx);
        }
        return result;
    }

    public Object evalFormula(String formula, URI mustacheFile, int begLineInFile) {
        getLogger().info("evalFormula:{}",formula);
        ParseTree tree = parseTreeMap.get(formula);
        if(tree == null) {
            MultiExprCompiler exprCompiler = new MultiExprCompiler(mustacheFile, formula);
            try {
                exprCompiler.compileAntlr4(new GeneratorErrorListener(mustacheFile,begLineInFile));
                tree = exprCompiler.getTree();

                parseTreeMap.put(formula,tree);
            } catch (IOException e) {
                getLogger().error("Error while compile formula:{} in file:{}", formula, mustacheFile);
            }
        }
        if(tree != null)
            return visit(tree);
        return null;
    }

    public static void main(String[] args) throws Exception {
        // 示例输入
        String input = "abc =34456;abc*5';def=\"asd\"";

        ExprEvaluator evaluator = new ExprEvaluator();

        Object result = evaluator.evalFormula(input, new File("NOFILE").toURI(),0);

        // 打印结果
        System.out.println("Result: " + result);
    }
}
