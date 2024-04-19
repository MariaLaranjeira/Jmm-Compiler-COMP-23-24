package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Errors Table:
 0 -> Array type cannot be used on binary operation
 1 -> Binary operation cannot be between objects of different types
 2 -> If the variable is not found in either fields or locals, it's an undefined variable
 3 -> Can't compute type for expression of kind "O KIND"
 4 -> Inconsistent types in array initializer
 5 -> Operator unknown
 6 -> This in main class invalid
 99 -> Continue, imported variable
 */

public class TypeUtils {

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case LENGTH -> new Type("int", false);
            case VARARGS_PARAM -> getVarargsType(expr, table);
            case NEW_OBJECT -> getNewObjectType(expr, table);
            case PAREN_EXPR -> getExprType(expr.getChildren().get(0), table);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case ARRAY_INITIALIZER -> getArrayType(expr, table);
            case BINARY_OP -> getBinExprType(expr, table);
            case FUNCTION_CALL -> getReturnType(expr, table);
            case THIS_EXPR -> getThisType(expr, table);
            case ARRAY_ACCESS -> getArrayAccessType(expr, table);
            case INTEGER_LITERAL -> new Type("int", false);
            case BOOLEAN_LITERAL -> new Type("boolean", false);

            default -> new Type("3", false);
        };
    }

    public static String getIntTypeName() {
        return "int";
    }



    public static Type getArrayType(JmmNode arrayInitializer, SymbolTable table) {
        Type expectedType = getExprType(arrayInitializer.getChildren().get(0), table);

        for (JmmNode child : arrayInitializer.getChildren()) {
            Type childType = getExprType(child, table);
            if (!childType.equals(expectedType)) {
                return new Type("4", false);
            }
        }

        return new Type(expectedType.getName(), true);
    }

    public static Type getArrayAccessType(JmmNode arrayAccessNode, SymbolTable table) {
        JmmNode arrayNode = arrayAccessNode.getChildren().get(0);
        Type arrayType = getExprType(arrayNode, table);

        return new Type(arrayType.getName(), false);
    }

    private static Type getThisType(JmmNode binaryExpr, SymbolTable table) {
        String className = table.getClassName();
        if(className.equals("main"))
            return new Type("6", false);
        return new Type(table.getClassName(), false);
    }


    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {
        String operator = binaryExpr.get("op");

        JmmNode leftNode = binaryExpr.getChildren().get(0);
        JmmNode rightNode = binaryExpr.getChildren().get(1);

        Type leftType = getExprType(leftNode, table);
        Type rightType = getExprType(rightNode, table);

        if(leftType.isArray() || rightType.isArray()){
            return new Type("0", false);
        }

        if (!leftType.equals(rightType)) {
            return new Type("1", false);
        }

        return switch (operator) {
            case "+", "-", "*", "/" ->
                    new Type("int", false);
            case "==", "!=", "<", ">", "<=", ">=" ->
                    new Type("boolean", false);
            default ->
                    new Type("5", false);
        };
    }

    private static Type getReturnType(JmmNode functionCall, SymbolTable table) {
        String methodName = functionCall.get("value");
        Type returnType = table.getReturnType(methodName);

        if(returnType == null){
            return new Type ("99", false);
        }

        return returnType;

    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String varName = varRefExpr.get("name");
        String currentMethod = findCurrentMethodName(varRefExpr);

        //Var is a field
        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        //Var is a local variable
        List<Symbol> locals = table.getLocalVariables(currentMethod);
        for (Symbol local : locals) {
            if (local.getName().equals(varName)) {
                return local.getType();
            }
        }

        // Var is a parameter
        List<Symbol> parameters = table.getParameters(currentMethod);
        for (Symbol param : parameters) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }

        // Var is imported
        if(TypeUtils.isTypeImported(varName, table)){
            return new Type ("99", false);
        }

        // If the variable is not found in either fields or locals, it's an undefined variable
        return new Type("2", false);
    }

    private static Type getNewObjectType(JmmNode expr, SymbolTable table) {
        String className = expr.get("value");
        return new Type(className, false);
    }

    private static Type getVarargsType(JmmNode varargsParam, SymbolTable table) {
        JmmNode typeNode = varargsParam.getChildren().get(0);
        String baseTypeName = typeNode.get("name");
        boolean isArray = true;
        Type varargType = new Type(baseTypeName, isArray);
        varargType.putObject("isVararg", true);

        return varargType;
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        return sourceType.equals(destinationType);
    }

    public static boolean isTypeImported(String typeName, SymbolTable table) {
        List<String> imports = table.getImports();

        for (String imported : imports) {
            if (imported.equals(typeName)) {
                return true;
            }
        }
        return false;
    }

    private static String findCurrentMethodName(JmmNode node) {
        Optional<JmmNode> methodNode = node.getAncestor(Kind.METHOD_DECL);
        if (methodNode.isPresent()) {
            JmmNode mNode = methodNode.get();

            boolean isMain = mNode.getOptional("static").isPresent() && "void".equals(mNode.get("type"));

            if (mNode.getAttributes().contains("name")) {
                return mNode.get("name");
            } else {
                System.out.println("Method node does not contain 'name' attribute: " + mNode);
                return "main";
            }

        } else {
            System.out.println("Failed to find METHOD_DECL ancestor for node: " + node);
            return "main";
        }
    }


}
