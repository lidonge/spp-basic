package com.servpp.lang.models.app.expr;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IArithmeticOperation extends IOperation{
    static final String PLUS = "+";
    static final String MINUS = "-";
    static final String MULTIPLE = "*";
    static final String DIVIDE = "/";
    static final String PPLUS = "++";
    static final String MMINUS = "--";
    static final String MOD = "%";

    static ArithmeticOperation createArithmetic(String soper){
        ArithmeticOperation ret = null;
        switch (soper){
            case PLUS:
            case MINUS:
            case MULTIPLE:
            case DIVIDE:
            case MOD:
                ret = new ArithmeticOperation();
                ret.setOperator(soper);
                ret.setType(Opers_Type.Double);
                break;
            case PPLUS:
            case MMINUS:
                ret = new ArithmeticOperation();
                ret.setOperator(soper);
                break;
            default:
                break;
        }
        return ret;
    }
}
