package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;
import java.util.Objects;

/**
 * Errors Table:
 0 -> Array type cannot be used on binary operation
 1 -> Binary operation cannot be between objects of different types
 2 -> If the variable is not found in either fields or locals, it's an undefined variable
 3 -> Can't compute type for expression of kind "O KIND"
 4 -> Inconsistent types in array initializer
 99 -> Continue as the reference is an extended or imported class
 */

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
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.CONDITIONAL_STMT, this::visitIfStmt);
        addVisit(Kind.ARRAY_INITIALIZER, this::visitArrayInitializer);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);
        addVisit(Kind.FUNCTION_CALL, this::visitFunctionCall);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
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

        if (Objects.equals(rightType.getName(), "99")) {
            return null;
        }

        if (Objects.equals(leftType.getName(), "3") || Objects.equals(rightType.getName(), "3")) {
            var message = " Can't compute type for expression. ";
            addReport(Report.newError(
                       Stage.SEMANTIC,
                       NodeUtils.getLine(assign),
                       NodeUtils.getColumn(assign),
                       message,
                       null
                    )
            );
            return null;
        }

        if (Objects.equals(leftType.getName(), "4") || Objects.equals(rightType.getName(), "4")) {
            var message = "Inconsistent types in array initializer.";
            addReport(Report.newError(
                       Stage.SEMANTIC,
                       NodeUtils.getLine(assign),
                       NodeUtils.getColumn(assign),
                       message,
                       null
                    )
            );
            return null;
        }

        if(TypeUtils.isTypeImported(leftType.getName(), table) && TypeUtils.isTypeImported(rightType.getName(), table)){
            return null;
        }

        if(table.getSuper() != null  && table.getSuper().contains(leftType.getName())){
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

    private Void visitArrayInitializer(JmmNode arrayInitializer, SymbolTable table) {
        if (arrayInitializer.getChildren().isEmpty()) {
            addErrorReport(arrayInitializer, "Empty array initializers are not allowed.");
            return null;
        }

        Type expectedBaseType = TypeUtils.getExprType(arrayInitializer.getChildren().get(0), table);

        for (JmmNode expr : arrayInitializer.getChildren()) {
            Type exprType = TypeUtils.getExprType(expr, table);
            if (!exprType.equals(expectedBaseType)) {
                addErrorReport(arrayInitializer, String.format("Array initializer contains an element of type '%s', but expected type was '%s'.",
                        exprType.getName(), expectedBaseType.getName()));
                break;
            }
        }

        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAssign, SymbolTable table) {
        JmmNode arrayRef = arrayAssign.getChildren().get(0);
        JmmNode index = arrayAssign.getChildren().get(1);

        Type arrayRefType = TypeUtils.getExprType(arrayRef, table);
        Type indexType = TypeUtils.getExprType(index, table);

        // Check if the array reference is an array
        if (!arrayRefType.isArray()) {
            String message = String.format("Type '%s' is not an array.", arrayRefType.getName());
            addErrorReport(arrayRef, message);
            return null;
        }

        // Check if the index is an integer
        if (!indexType.getName().equals("int")) {
            String message = "Array index must be an integer.";
            addErrorReport(index, message);
            return null;
        }

        return null;
    }

    private Void visitFunctionCall(JmmNode functionCall, SymbolTable table) {
        JmmNode objectNode = functionCall.getChildren().get(0);
        List<JmmNode> args = functionCall.getChildren().subList(1, functionCall.getNumChildren()); // Get all arguments
        String methodName = functionCall.get("value");

        Type objectType = TypeUtils.getExprType(objectNode, table);

        //verify if the classes are being imported.
        if (TypeUtils.isTypeImported(objectType.getName(), table)) {
            return null;
        }

        //verify if the class extends an imported class
        if(table.getSuper() != null  && !table.getSuper().isEmpty()){
            return null;
        }

        if (!table.getMethods().contains(methodName)) {
            addErrorReport(functionCall, String.format("Method '%s' is not defined in the current class, an imported class, or superclass.", methodName));
        }

        List<Symbol> expectedParamTypes = table.getParameters(methodName);
        boolean hasVarargs = !expectedParamTypes.isEmpty() && expectedParamTypes.get(expectedParamTypes.size() - 1).getType().hasAttribute("isVararg");
        int minArgs = hasVarargs ? expectedParamTypes.size() - 1 : expectedParamTypes.size();

        //Check if the number of provided arguments matches the expected parameters
        if (args.size() < minArgs) {
            addErrorReport(functionCall, String.format("Incorrect number of arguments for method '%s'. Expected at least %d, found %d.",
                    methodName, minArgs, args.size()));
            return null;
        }

        // Check the type of each argument against the expected type
        for (int i = 0; i < args.size(); i++) {
            Type argType = TypeUtils.getExprType(args.get(i), table);
            Type expectedType = (i < expectedParamTypes.size()) ? expectedParamTypes.get(i).getType() : expectedParamTypes.get(expectedParamTypes.size() - 1).getType();

            if (hasVarargs && i >= minArgs) { // Handling varargs matching for single elements and arrays
                if (!argType.equals(new Type(expectedType.getName(), false)) && !TypeUtils.areTypesAssignable(argType, new Type(expectedType.getName(), true))) {
                    addErrorReport(args.get(i), String.format("Type mismatch for varargs argument in method '%s': Expected type '%s' or array of '%s', found '%s'.",
                            methodName, expectedType.getName(), expectedType.getName(), argType));
                }
            } else if (!TypeUtils.areTypesAssignable(argType, expectedType)) { 
                addErrorReport(args.get(i), String.format("Type mismatch for argument %d in method '%s': Expected '%s', found '%s'.",
                        i + 1, methodName, expectedType, argType));
            }
        }

        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        JmmNode expr = returnStmt.getChildren().get(0);

        Type exprType = TypeUtils.getExprType(expr, table);

        if (Objects.equals(exprType.getName(), "0")) {
            var message = "Array type cannot be used on binary operations.";
            addReport(Report.newError(
                       Stage.SEMANTIC,
                       NodeUtils.getLine(returnStmt),
                       NodeUtils.getColumn(returnStmt),
                       message,
                       null
                    )
            );
            return null;
        }
        if (Objects.equals(exprType.getName(), "1")) {
            var message = "Binary operation with incompatible types.";
            addReport(Report.newError(
                       Stage.SEMANTIC,
                       NodeUtils.getLine(returnStmt),
                       NodeUtils.getColumn(returnStmt),
                       message,
                       null
                    )
            );
            return null;
        }

        if (Objects.equals(exprType.getName(), "2")) {
            var message = "Undefined variable.";
            addReport(Report.newError(
                       Stage.SEMANTIC,
                       NodeUtils.getLine(returnStmt),
                       NodeUtils.getColumn(returnStmt),
                       message,
                       null
                    )
            );
            return null;
        }

        Type methodReturnType = table.getReturnType(currentMethod);

        //in case it is imported
        if (methodReturnType == null || exprType == null) {
            return null;
        }

        if (!TypeUtils.areTypesAssignable(exprType, methodReturnType)) {
            String message = String.format("Return type mismatch, expected %s, found %s in method %s.",
                    methodReturnType, exprType, currentMethod);
            addErrorReport(returnStmt, message);
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
