package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;

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
            case INTEGER_TYPE -> getType(expr);
            case BOOLEAN_TYPE -> getType(expr);
            case FLOAT_TYPE -> getType(expr);
            case DOUBLE_TYPE -> getType(expr);
            case STRING_TYPE -> getType(expr);
            case ID_TYPE -> getType(expr);
            case ARRAY_TYPE -> getArrayType(expr, table);
            case INTEGER_LITERAL -> getType(expr);
            case BOOLEAN_LITERAL -> getType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);

            case BINARY_OP -> getBinExprType(expr, table);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    public static String getIntTypeName() {
        return "int";
    }

    private static Type getType(JmmNode jmmNode) {
        return new Type (jmmNode.get("value"), false);
    }

    public static Type getArrayType(JmmNode array, SymbolTable table){
        JmmNode baseTypeNode = array.getChildren().get(0);
        Type baseType = getExprType(baseTypeNode, table);
        return new Type(baseType.getName(), true);
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
        // TODO: Simple implementation that needs to be expanded
        String methodName = functionCall.get("name");
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


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        if (sourceType.getName().equals(destinationType.getName())) {
            return true;
        }
        return false;
    }

    public static boolean isTypeImported(Type type, SymbolTable table) {
        return table.getImports().contains(type.getName());
    }

}
