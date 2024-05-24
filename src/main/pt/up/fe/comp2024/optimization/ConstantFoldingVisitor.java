package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

public class ConstantFoldingVisitor extends AJmmVisitor<Boolean, Boolean> {
    private int counter = 0;

    @Override
    protected void buildVisitor() {
        addVisit(BINARY_OP, this::visitBinaryOp);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(PAREN_EXPR, this::visitParenthesis);

        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitParenthesis(JmmNode jmmNode, Boolean bool) {
        visit(jmmNode.getChildren().get(0));

        JmmNode child = jmmNode.getChildren().get(0);
        if(child.getKind().equals("IntegerLiteral") || child.getKind().equals("BooleanLiteral")) {
            JmmNode parent = jmmNode.getParent();
            int index = jmmNode.getIndexOfSelf();
            parent.removeChild(index);
            parent.add(child, index);
        }

        return bool;
    }

    private Boolean visitWhileStmt(JmmNode jmmNode, Boolean bool) {
        return bool;
    }

    private Boolean defaultVisit(JmmNode jmmNode, Boolean bool) {
        for(JmmNode node : jmmNode.getChildren())
            visit(node, bool);
        return bool;
    }

    private Boolean visitBinaryOp(JmmNode jmmNode, Boolean bool) {
        String op = jmmNode.get("op");

        visit(jmmNode.getChildren().get(0), bool);
        visit(jmmNode.getChildren().get(1), bool);

        if ( !(Objects.equals(op, "*") || Objects.equals(op, "/") || Objects.equals(op, "+") || Objects.equals(op, "-") ) ){
            return false;
        }

        JmmNode left = jmmNode.getChildren().get(0);
        JmmNode right = jmmNode.getChildren().get(1);

        int newValue = 0;

        if(left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
            switch (op) {
                case "*" -> newValue = Integer.parseInt(left.get("value")) * Integer.parseInt(right.get("value"));
                case "/" -> newValue = Integer.parseInt(left.get("value")) / Integer.parseInt(right.get("value"));
                case "+" -> newValue = Integer.parseInt(left.get("value")) + Integer.parseInt(right.get("value"));
                case "-" -> newValue = Integer.parseInt(left.get("value")) - Integer.parseInt(right.get("value"));
                default -> {
                }
            }

            JmmNode newNode = new JmmNodeImpl("IntegerLiteral");
            newNode.put("value", String.valueOf(newValue));

            JmmNode parentNode = jmmNode.getParent();
            int index = jmmNode.getIndexOfSelf();
            parentNode.removeJmmChild(index);
            parentNode.add(newNode, index);

            counter++;
        }

        return bool;
    }

    public int getCounter() {
        return this.counter;
    }

    public void resetCounter() {
        this.counter = 0;
    }
}