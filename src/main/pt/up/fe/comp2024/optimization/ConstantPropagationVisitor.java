package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class ConstantPropagationVisitor extends AJmmVisitor<Boolean, Boolean> {
    private final Map<String, JmmNode> constants = new HashMap<>();
    private final Map<String, List<JmmNode>> removals = new HashMap<>();
    private final SymbolTable symbolTable;
    private int counter = 0;

    public ConstantPropagationVisitor(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    @Override
    protected void buildVisitor() {
        addVisit(METHOD_DECL, this::visitMethodDeclaration);
        addVisit(VAR_DECL, this::visitVarDeclaration);
        addVisit(ASSIGN_STMT, this::visitAssignment);
        addVisit(VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitWhileStmt(JmmNode jmmNode, Boolean bool) {
        visit(jmmNode.getChildren().get(0));
        visit(jmmNode.getChildren().get(1));
        return bool;
    }

    private Boolean visitAssignment(JmmNode jmmNode, Boolean bool) {
        JmmNode left = jmmNode.getChildren().get(0);
        JmmNode right = jmmNode.getChildren().get(1);
        visit(left, bool);
        visit(right, bool);

        String leftName = left.get("name");
        boolean isBoolean = right.getKind().equals("BooleanLiteral");
        boolean isInteger = right.getKind().equals("IntegerLiteral");

        if ((!isInteger && !isBoolean) || jmmNode.getChildren().size() > 2) {
            constants.remove(leftName);
            removals.remove(leftName);
        } else if (isLocalVariable(leftName)) {
            constants.put(leftName, right);
        }
        return true;
    }

    private Boolean visitVarRefExpr(JmmNode jmmNode, Boolean bool) {
        if (constants.containsKey(jmmNode.get("name"))) {
            JmmNode newNode = constants.get(jmmNode.get("name"));
            JmmNode parent = jmmNode.getParent();
            int index = jmmNode.getIndexOfSelf();
            parent.removeChild(index);
            parent.add(newNode,index);
            counter++;
        }
        return true;
    }

    private Boolean visitVarDeclaration(JmmNode jmmNode, Boolean aBoolean) {
        constants.remove(jmmNode.get("name"));
        return true;
    }

    private Boolean defaultVisit(JmmNode jmmNode, Boolean aBoolean) {
        jmmNode.getChildren().forEach(child -> visit(child, aBoolean));
        return aBoolean;
    }

    private Boolean visitMethodDeclaration(JmmNode jmmNode, Boolean aBoolean) {
        constants.clear();
        removals.clear();
        jmmNode.getChildren().forEach(child -> visit(child, aBoolean));
        removeAssignsAndDeclarations();
        return true;
    }

    public void removeAssignsAndDeclarations() {
        for (Map.Entry<String, JmmNode> assignment : constants.entrySet()) {
            removals.getOrDefault(assignment.getKey(), Collections.emptyList())
                    .forEach(assign -> assign.getParent().removeChild(assign));
        }
    }

    private boolean isLocalVariable(String varName) {
        return symbolTable.getMethods().stream()
                .flatMap(method -> symbolTable.getLocalVariables(method).stream())
                .anyMatch(localVar -> localVar.getName().equals(varName));
    }

    public int getCounter() {
        return counter;
    }

    public void resetCounter() {
        counter = 0;
    }
}
