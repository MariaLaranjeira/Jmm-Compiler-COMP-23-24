package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.List;
import java.util.stream.Collectors;

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
            //type
            case INTEGER_LITERAL -> new Type("int", false);
            case BOOLEAN_LITERAL -> new Type("boolean", false);

            case NEW_OBJECT -> getNewObjectType(expr, table);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case ARRAY_INITIALIZER -> getArrayType(expr, table);
            case BINARY_OP -> getBinExprType(expr, table);
            case FUNCTION_CALL -> getReturnType(expr, table);
            case THIS_EXPR -> new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
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
                throw new RuntimeException("Inconsistent types in array initializer: found type "
                        + childType.getName() + " but expected " + expectedType.getName());
            }
        }

        return new Type(expectedType.getName(), true);
    }

    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {
        String operator = binaryExpr.get("op");

        JmmNode leftNode = binaryExpr.getChildren().get(0);
        JmmNode rightNode = binaryExpr.getChildren().get(1);

        Type leftType = getExprType(leftNode, table);
        Type rightType = getExprType(rightNode, table);

        if(leftType.isArray() || rightType.isArray()){
            throw new RuntimeException("Arrays cannot be used in arithmetic operations");
        }

        if (!leftType.equals(rightType)) {
            throw new RuntimeException("Type mismatch between operands of expression '" + binaryExpr + "'");
        }

        return switch (operator) {
            case "+", "-", "*", "/" ->
                    new Type("int", false);
            case "==", "!=", "<", ">", "<=", ">=" ->
                    new Type("boolean", false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private static Type getReturnType(JmmNode functionCall, SymbolTable table) {
        String methodName = functionCall.get("value");

        return table.getReturnType(methodName);
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String varName = varRefExpr.get("name");

        //Var is a field
        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        for (String method : table.getMethods()) {
            //Var is a local variable
            List<Symbol> locals = table.getLocalVariables(method);
            for (Symbol local : locals) {
                if (local.getName().equals(varName)) {
                    return local.getType();
                }
            }

            // Var is a parameter
            List<Symbol> parameters = table.getParameters(method);
            for (Symbol param : parameters) {
                if (param.getName().equals(varName)) {
                    return param.getType();
                }
            }
        }

        // If the variable is not found in either fields or locals, it's an undefined variable
        throw new RuntimeException("Variable '" + varName + "' is not defined in the symbol table");
    }

    private static Type getNewObjectType(JmmNode expr, SymbolTable table) {
        String className = expr.get("value");

        return new Type(className, false);
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        if (sourceType.equals(destinationType)) {
            return true;
        }
        return false;
    }

    public static boolean isTypeImported(String typeName, SymbolTable table) {
        List<String> imports = table.getImports();
        for (String sublist : imports) {
            if (sublist.contains(typeName)) {
                return true;
            }
        }
        return false;
    }

}
