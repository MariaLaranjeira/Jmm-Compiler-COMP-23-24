package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

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

            case BINARY_EXPR -> getBinExprType(expr);


            
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
    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        JmmNode leftType = binaryExpr.getChildren().get(0);
        JmmNode rightType = binaryExpr.getChildren().get(1);

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



    private static Type getReturnType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        String varName = varRefExpr.get("name");

        return new Type("int", false);
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        String varName = varRefExpr.get("name");

        return new Type("int", false);
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
