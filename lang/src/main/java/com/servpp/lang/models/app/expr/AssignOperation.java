package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.NameUtil;
import com.servpp.lang.general.app.SemanticException;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppEnum;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */

public class AssignOperation extends GeneralOperation implements IAssignOperation{
    String assoc;


    public AssignOperation() {
        setOperator("=");
    }

    @Override
    public String getAssocOperation() {
        return assoc;
    }

    public void setAssoc(String assoc) {
        this.assoc = assoc;
    }

    @Override
    public String getExpr(IExpression left, IExpression right) {
        String ret = "";
        if(left instanceof Reference){
            String setMethod = NameUtil.getNameCall(((Reference) left).getValue(),true);
            ret += setMethod +"(";
            if(assoc != null && !assoc.isEmpty()){
                ret += left.getExpr();
                ret += assoc;
            }
            ret += right == null ? "" : getRightExpr(left, right);

            ret += ")";
        } else {
            ret += left == null ? "" : left.getExpr();
            ret += assoc == null ? "" : assoc;
            ret += getOperator();
            ret += right == null ? "" : right.getExpr();
        }

        return ret;
    }

    public String getRightExpr(IExpression left, IExpression right) {
        SppCompilationUnit leftType = left.getReturnType();
        if (leftType instanceof SppEnum && right instanceof Literal) {
            String value = ((Literal) right).getValue();
            if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return leftType.getPackageName() + "." + leftType.getName() + ".fromValue(" + value + ")";
            }
        }
        return right.getExpr();
    }

    @Override
    public void check(IExpression left, IExpression right) throws SemanticException {

    }
}
