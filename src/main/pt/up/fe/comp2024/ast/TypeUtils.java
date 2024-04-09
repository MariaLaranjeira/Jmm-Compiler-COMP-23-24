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
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case INTEGER_LITERAL -> new Type("int", false);
            case BOOLEAN_LITERAL -> new Type("boolean", false);
            case PARENTESIS -> getExprType(expr.getChildren().get(0), table);
            case NEGATION -> new Type("boolean", false);
            case LENGTH -> new Type("int", false);
            case BINARY_EXPR -> getBinExprType(expr, table);

            //not created
            case NEW_CLASS -> new Type(expr.get("classname"), false);

            case VAR_REF_EXPR -> getVarExprType(expr, table);

            case FUNCTION_CALL -> getReturnType(expr, table);

            case ARRAY_ACCESS -> new Type("int", false);

            case ARRAY_DECLARATION -> new Type("int", false);

            case METHOD_DECL -> new Type("int", false);

            case OBJECT -> new Type("int", false);

            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    public static String getIntTypeName() {
        return "int";
    }

    //get the binary expression type of an operation
    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table ) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        JmmNode leftNode = binaryExpr.getChildren().get(0);
        JmmNode rightNode = binaryExpr.getChildren().get(1);

        Type leftType = getExprType(leftNode, table);
        Type rightType = getExprType(rightNode, table);

        if (!leftType.equals(rightType)) {
            throw new RuntimeException("Type mismatch between operands of expression '" + binaryExpr + "'");
        }

        switch (operator) {
            case "+", "-", "*", "/":
                return new Type("int", false);
            case "==", "!=", "<", ">", "<=", ">=":
                return new Type("boolean", false);
            default:
                throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        }
    }


    private static Type getReturnType(JmmNode functionCall, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        String methodName = functionCall.get("name");
        return table.getReturnType(methodName);
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        String varName = varRefExpr.get("name");

        // First, try to find the variable in the fields
        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        // If not found in fields, search in local variables of each method
        for (String method : table.getMethods()) {
            List<Symbol> locals = table.getLocalVariables(method);
            for (Symbol local : locals) {
                if (local.getName().equals(varName)) {
                    return local.getType();
                }
            }

            // Also check in method parameters
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
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}
