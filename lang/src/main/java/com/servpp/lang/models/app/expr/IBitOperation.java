package com.servpp.lang.models.app.expr;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IBitOperation extends IOperation{
    static final String AND = "&";
    static final String OR = "|";
    static final String NOT = "~";
    static final String XOR = "^";
    static final String SHIFT_RIGHT = ">>";
    static final String SHIFT_LEFT = "<<";
    static final String SHIFT_RIGHT_R = ">>>";

    static BitOperation creaetBit(String soper){
        BitOperation ret = null;
        switch (soper){
            case AND:
            case OR:
            case XOR:
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
            case SHIFT_RIGHT_R:
                ret = new BitOperation();
                ret.setOperator(soper);
                ret.setType(Opers_Type.Double);
                break;
            case NOT:
                ret = new BitOperation();
                ret.setOperator(soper);
                ret.setType(Opers_Type.Left);
                break;
            default:
                break;
        }
        return ret;
    }
}
