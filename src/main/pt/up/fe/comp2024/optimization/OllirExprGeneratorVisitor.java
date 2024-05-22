package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit("VarRefExpr", this::visitVarRef);
        addVisit("IntegerLiteral", this::visitInteger);
        addVisit("BooleanLiteral", this::visitBoolean);
        addVisit("FunctionCall", this::visitFunctionCall);
        addVisit("NewObject", this::visitNewObject);
        addVisit("BinaryOp", this::visitBinExpr);
        addVisit("NotExpr", this::visitNotExpr);
        addVisit("NewArray", this::visitNewArray);
        addVisit("Length", this::visitLength);
        addVisit("ArrayAccess", this::visitArrayAccess);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        String ollirIntType = OptUtils.toOllirType("int");
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        String ollirBoolType = OptUtils.toOllirType("boolean");
        String code = node.get("value") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitNotExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append("!.bool ").append(node.getChild(0).get("value")).append(OptUtils.toOllirType("boolean"));
        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var rhs = visit(node.getJmmChild(1));

        code.append("$1.");
        code.append(node.getChild(0).get("name"));
        code.append("[").append(rhs.getCode()).append("].i32");

        return new OllirExprResult(code.toString());

    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var rhs = visit(node.getJmmChild(0));

        code.append("new(array, ").append(rhs.getCode()).append(").array.i32;");

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        return new OllirExprResult(node.get("name") + ollirType);
    }

    private OllirExprResult visitNewObject(JmmNode jmmNode, Void unused) {
        StringBuilder computation = new StringBuilder();

        String objectType = jmmNode.get("value");

        computation.append("new(").append(objectType).append(").").append(objectType);
        String code = OptUtils.getTemp();

        return new OllirExprResult(computation.toString(),code);

    }

    private OllirExprResult visitLength(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        String ollirIntType = OptUtils.toOllirType("int");

        //we only have (new) arrays of ints in this grammar
        String temp = OptUtils.getTemp();

        computation.append(temp).append(ollirIntType)
                .append(" :=").append(ollirIntType)
                .append(" arraylength(")
                .append(node.getChild(0).get("name"))
                .append(".array.i32).i32;\n");

        String code = temp + ollirIntType;

        return new OllirExprResult(code, computation);
    }

    //TODO: Analyse
    private OllirExprResult visitFunctionCall(JmmNode jmmNode, Void unused) {
        OllirGeneratorVisitor visitor = new OllirGeneratorVisitor(table);
        String visitCode = visitor.visitFunctionCall(jmmNode,unused);

        StringBuilder computation = new StringBuilder();

        // code to compute self
        Type resType = TypeUtils.getExprType(jmmNode, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(visitCode).append(SPACE).append("\n");

        return new OllirExprResult(code,computation);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        OllirExprResult lhs = visit(node.getJmmChild(0));
        OllirExprResult rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        computation.append(node.get("op")).append(resOllirType).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
