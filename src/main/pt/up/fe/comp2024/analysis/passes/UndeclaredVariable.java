package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        //Declaration and reference Checks
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);

        addVisit(Kind.BINARY_OP, this::visitBinaryOp);
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);

        addVisit(Kind.CONDITIONAL_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.getAttributes().contains("name")) {
            currentMethod = method.get("name");
        } else {
            currentMethod = "main";
        }
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a declared variable, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varRefName))) {
            return null;
        }

        // Create error report
        var message = String.format("Variable '%s' does not exist.", varRefName);
        addErrorReport(varRefExpr, message);

        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {
        try {
            Type resultType = TypeUtils.getExprType(binaryOp, table);

        } catch (RuntimeException e) {
            addErrorReport(binaryOp, e.getMessage());
        }

        return null;
    }

    private Void visitAssign(JmmNode assign, SymbolTable table) {
        JmmNode left = assign.getChildren().get(0);
        JmmNode right = assign.getChildren().get(1);

        Type leftType = TypeUtils.getExprType(left, table);
        Type rightType = TypeUtils.getExprType(right, table);

        if (TypeUtils.isTypeImported(leftType, table) && TypeUtils.isTypeImported(rightType, table)) {
            return null;
        }

        if (!TypeUtils.areTypesAssignable(rightType, leftType)) {
            String message = String.format("Cannot assign a value of type '%s' to a variable of type '%s'.", rightType.getName(), leftType.getName());
            addErrorReport(assign, message);
            return null;
        }

        return null;
    }

    private Void visitIfStmt(JmmNode ifStmt, SymbolTable table) {
        JmmNode condition = ifStmt.getChildren().get(0);
        Type conditionType = TypeUtils.getExprType(condition, table);

        if (!conditionType.getName().equals("boolean")) {
            addErrorReport(ifStmt, "Condition expression must be boolean, found type '" + conditionType + "'");
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        JmmNode condition = whileStmt.getChildren().get(0);
        Type conditionType = TypeUtils.getExprType(condition, table);

        if (!conditionType.getName().equals("boolean")) {
            addErrorReport(whileStmt, "Condition expression must be boolean, found type '" + conditionType + "'");
        }

        return null;
    }

    private void addErrorReport(JmmNode node, String message) {
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null)
        );
    }


}
