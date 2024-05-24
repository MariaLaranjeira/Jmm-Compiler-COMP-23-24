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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Errors Table:
 0 -> Array type cannot be used on binary operation
 1 -> Binary operation cannot be between objects of different types
 2 -> If the variable is not found in either fields or locals, it's an undefined variable
 3 -> Can't compute type for expression of kind "O KIND"
 4 -> Inconsistent types in array initializer
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
        addVisit(Kind.PROGRAM, this::visitProgram);
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.BINARY_OP, this::visitBinaryOp);
        addVisit(Kind.WHILE_STMT, this::visitConditionStmt);
        addVisit(Kind.IF_STMT, this::visitConditionStmt);
        addVisit(Kind.ARRAY_INITIALIZER, this::visitArrayInitializer);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.LENGTH, this::visitLength);
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);
        addVisit(Kind.FUNCTION_CALL, this::visitFunctionCall);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitProgram(JmmNode method, SymbolTable table){
        List<String> imports = table.getImports();
        Set<String> seenImports = new HashSet<>();
        for (String imp : imports) {
            if (!seenImports.add(imp)) {
                addErrorReport(method, "Duplicate import: " + imp);
            }
        }
        return null;
    }

    private Void visitClassDecl(JmmNode method, SymbolTable table){
        // Check for duplicate fields
        Set<String> fieldNames = new HashSet<>();
        for (Symbol field : table.getFields()) {
            if (!fieldNames.add(field.getName())) {
                addErrorReport(method, "Duplicate field: " + field.getName());
            }
        }

        // Check for duplicate methods
        Set<String> methodNames = new HashSet<>();
        for (String methodName : table.getMethods()) {
            if (!methodNames.add(methodName)) {
                addErrorReport(method, "Duplicate method: " + methodName);
            }
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.getAttributes().contains("name")) {
            currentMethod = method.get("name");
        } else {
            currentMethod = "main";
        }

        List<Symbol> parameters = table.getParameters(currentMethod);
        Set<String> paramNames = new HashSet<>();
        for (Symbol param : parameters) {
            if (!paramNames.add(param.getName())) {
                addErrorReport(method, "Duplicate parameter: " + param.getName());
            }
        }

        // Check for duplicate local variables within the method
        List<Symbol> localVariables = table.getLocalVariables(currentMethod);
        Set<String> declaredLocals = new HashSet<>();
        for (Symbol localVar : localVariables) {
            if (!declaredLocals.add(localVar.getName())) {
                addErrorReport(method, "Duplicate local variable: " + localVar.getName());
            }
        }
        return null;
    }

    private Void visitLength(JmmNode varRefExpr, SymbolTable table){
        JmmNode firstExpr = varRefExpr.getChildren().get(0);
        Type exprType = TypeUtils.getExprType(firstExpr, table);

        if(!exprType.isArray()){
            var message = String.format("Variable '%s' has to be an array.", exprType.getName());
            addErrorReport(varRefExpr, message);
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");

        if(varRefName.equals("length")) return null;

        if ("this".equals(varRefName) && currentMethod.equals("main")) {
            addErrorReport(varRefExpr, "'this' cannot be used in static context such as the 'main' method.");
        }

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

        if(TypeUtils.isTypeImported(varRefName, table)){
            return null;
        }

        // Create error report
        var message = String.format("Variable '%s' does not exist.", varRefName);
        addErrorReport(varRefExpr, message);

        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {
        Type resultType = TypeUtils.getExprType(binaryOp, table);

        if (resultType.getName().equals("0")) {
            var message = "Array type cannot be used on binary operations.";
            addErrorReport(binaryOp, message);
            return null;
        }

        if (resultType.getName().equals("1")) {
            var message = "Binary operation with incompatible types.";
            addErrorReport(binaryOp, message);
            return null;
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
            addErrorReport(assign, "Can't compute type for expression. ");
            return null;
        }

        if (Objects.equals(leftType.getName(), "4") || Objects.equals(rightType.getName(), "4")) {
            addErrorReport(assign, "Inconsistent types in array initializer.");
            return null;
        }

        if (rightType.getName().equals("6")) {
            addErrorReport(assign, "Can't use this in main");
            return null;
        }

        if(TypeUtils.isTypeImported(leftType.getName(), table) && TypeUtils.isTypeImported(rightType.getName(), table)){
            return null;
        }

        if(table.getSuper() != null  && table.getSuper().contains(leftType.getName())){
            return null;
        }

        if (currentMethod.equals("main") && isVariableFromOutside(left, table)) {
            addErrorReport(assign,  "Cannot assign a variable from outside in the static main function.");
            return null;
        }

        if (!TypeUtils.isValidLeftValue(left)) {
            addErrorReport(assign, "Invalid left-hand side in assignment. Must be a variable or a valid lvalue.");
        }

        if (!TypeUtils.areTypesAssignable(rightType, leftType)) {
            String message = String.format("Cannot assign a value of type '%s' to a variable of type '%s'.", rightType.getName(), leftType.getName());
            addErrorReport(assign, message);
            return null;
        }

        return null;
    }

    private boolean isVariableFromOutside(JmmNode varNode, SymbolTable table) {
        String varName;
        if(varNode.hasAttribute("name")) varName = varNode.get("name");
        else varName = varNode.getChildren().get(0).get("name"); // get the name of the type

        List<Symbol> locals = table.getLocalVariables(currentMethod);

        for (Symbol local : locals) {
            if (local.getName().equals(varName)) {
                return false;
            }
        }
        return true;
    }

    private Void visitConditionStmt(JmmNode stmt, SymbolTable table) {
        JmmNode condition = stmt.getChildren().get(0);
        Type conditionType = TypeUtils.getExprType(condition, table);

        if (!conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addErrorReport(stmt, "Condition expression type incorrect");
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
        if (!indexType.getName().equals("int") || indexType.isArray()) {
            addErrorReport(index, "Array index must be an integer.");
            return null;
        }

        return null;
    }

    private Void visitFunctionCall(JmmNode functionCall, SymbolTable table) {
        JmmNode objectNode = functionCall.getChildren().get(0);
        List<JmmNode> args = functionCall.getChildren().subList(1, functionCall.getNumChildren()); // Get all arguments
        String methodName = functionCall.get("value");

        String importedVar = objectNode.get("name");
        String importedClass = TypeUtils.getExprType(objectNode, table).getName();

        if (importedClass.equals("6")) {
            addErrorReport(functionCall, "Can't use this in main");
            return null;
        }

        //verify if the classes are being imported.
        if (TypeUtils.isTypeImported(importedVar, table) || TypeUtils.isTypeImported(importedClass, table) ) {
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
            addErrorReport(functionCall, String.format("Incorrect number of arguments for method '%s'. Expected at least %d, found %d.", methodName, minArgs, args.size()));
            return null;
        }

        // Check the type of each argument against the expected type
        for (int i = 0; i < args.size(); i++) {
            Type argType = TypeUtils.getExprType(args.get(i), table);
            Type expectedType = (i < expectedParamTypes.size()) ? expectedParamTypes.get(i).getType() : expectedParamTypes.get(expectedParamTypes.size() - 1).getType();

            if (hasVarargs && i >= minArgs) {
                if (!argType.equals(new Type(expectedType.getName(), false)) && !TypeUtils.areTypesAssignable(argType, new Type(expectedType.getName(), true))) {
                    addErrorReport(args.get(i), String.format("Type mismatch for varargs argument in method '%s': Expected type '%s' or array of '%s', found '%s'.", methodName, expectedType.getName(), expectedType.getName(), argType));
                }
            }
            else if (!TypeUtils.areTypesAssignable(argType, expectedType)) {
                addErrorReport(args.get(i), String.format("Type mismatch for argument %d in method '%s': Expected '%s', found '%s'.", i + 1, methodName, expectedType, argType));
            }
        }

        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        JmmNode expr = returnStmt.getChildren().get(0);
        Type exprType = TypeUtils.getExprType(expr, table);

        if (Objects.equals(exprType.getName(), "0")) {
            var message = "Array type cannot be used on binary operations.";
            addErrorReport(returnStmt, message);
            return null;
        }

        if (Objects.equals(exprType.getName(), "1")) {
            var message = "Binary operation with incompatible types.";
            addErrorReport(returnStmt, message);
            return null;
        }

        if (Objects.equals(exprType.getName(), "2")) {
            var message = "Undefined variable.";
            addErrorReport(returnStmt, message);
            return null;
        }

        if (exprType.getName().equals("6")) {
            var message = "Can't use this in main";
            addErrorReport(returnStmt, message);
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
