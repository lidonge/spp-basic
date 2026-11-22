package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.IContainer;

import java.util.List;

/** A single amount shared by the two movements of a transfer. */
public final class QuantumTransferStatement implements IJavaStatement {
    private final Reference source;
    private final Reference destination;
    private final IExpression amount;
    private IContainer parent;

    public QuantumTransferStatement(Reference source, Reference destination, IExpression amount) {
        this.source = source;
        this.destination = destination;
        this.amount = amount;
    }

    public Reference source() { return source; }
    public Reference destination() { return destination; }
    public IExpression amount() { return amount; }

    public List<AssignExpression> movements(IExpression amountValue) {
        return List.of(movement(source, "-", amountValue), movement(destination, "+", amountValue));
    }

    private static AssignExpression movement(Reference target, String direction, IExpression amount) {
        var operation = new AssignOperation();
        operation.setAssoc(direction);
        operation.setType(IOperation.Opers_Type.Double);
        var assignment = new AssignExpression();
        assignment.setOperation(operation);
        assignment.setLeft(target);
        assignment.setRight(amount);
        return assignment;
    }

    @Override public String getJavaStatement() { throw new IllegalStateException("Quantum transfer requires PHY lowering."); }
    @Override public IContainer getParent() { return parent; }
    @Override public void setParent(IContainer parent) { this.parent = parent; }
}
