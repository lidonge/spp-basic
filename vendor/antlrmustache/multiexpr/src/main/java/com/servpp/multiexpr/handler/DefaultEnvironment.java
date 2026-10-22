package com.servpp.multiexpr.handler;


import com.servpp.multiexpr.IEvaluatorEnvironment;

import java.util.*;
import java.util.function.Function;

/**
 * @author lidong@date 2024-07-31@version 1.0
 */
public class DefaultEnvironment implements IEvaluatorEnvironment {

    private Map<String, Object> variables = new HashMap<>();
    private Map<String, Function<Object[], Object>> functions = new HashMap<>();
    private Stack<Map<String, Object>> partialContexts = new Stack<>();
    private List<String> globalVariables = new ArrayList<>();

    @Override
    public List<String> getGlobalVariables() {
        return globalVariables;
    }

    @Override
    public void setGlobalVariables(List<String> globalVariables) {
        this.globalVariables = globalVariables;
    }

    public DefaultEnvironment() {
        addDefault();
    }

    @Override
    public void partialCall(String... params) {
        Map<String, Object> mParams = variables;
        partialContexts.push(variables);
        variables = new HashMap<>();
        for(String paramName:globalVariables){
            variables.put(paramName, mParams.get(paramName));
        }
        if(params != null) {
            for (String paramName : params) {
                variables.put(paramName, mParams.get(paramName));
            }
        }
    }

    @Override
    public void endPartialCall(String... returns) {
        Map<String, Object> rets = variables;
        variables = partialContexts.pop();
        if(returns != null) {
            for (String retName : returns) {
                variables.put(retName, rets.get(retName));
            }
        }
    }

    @Override
    public void setVar(String name, Object value) {
        variables.put(name, value);
    }

    @Override
    public Object getVar(String name) {
        return variables.get(name);
    }

    @Override
    public void addFunction(String name, Function<Object[], Object> function) {
        functions.put(name,function);
    }

    @Override
    public Function<Object[], Object> getFunction(String name) {
        return functions.get(name);
    }
}
