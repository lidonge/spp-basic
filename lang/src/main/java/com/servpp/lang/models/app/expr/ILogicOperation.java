package com.servpp.lang.models.app.expr;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface ILogicOperation extends IOperation{
    static final String LOGIC_AND = "&&";
    static final String LOGIC_OR = "||";
    static final String LOGIC_NOT = "!";
    static final String EQUAL = "==";
    static final String NOT_EQUAL = "!=";
    static final String GREAT = ">";
    static final String LESS = "<";
    static final String GREAT_EQUAL = ">=";
    static final String LESS_EQUAL = "<=";

    static LogicOperation createLogic(String soper){
        LogicOperation ret = null;
        switch (soper){
            case LOGIC_AND:
            case LOGIC_OR:
            case EQUAL:
            case NOT_EQUAL:
            case GREAT:
            case LESS:
            case GREAT_EQUAL:
            case LESS_EQUAL:
                ret = new LogicOperation();
                ret.setOperator(soper);
                ret.setType(Opers_Type.Double);
                break;
            case LOGIC_NOT:
                ret = new LogicOperation();
                ret.setOperator(soper);
                ret.setType(Opers_Type.Left);
                break;
            default:
                break;
        }
        return ret;
    }
}
