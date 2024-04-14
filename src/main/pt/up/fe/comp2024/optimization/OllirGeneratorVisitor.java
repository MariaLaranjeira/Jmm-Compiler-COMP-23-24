package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;

import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("ImportStmt", this::visitImportStmt);
        addVisit("MainMethodStmt", this::visitMainMethod);
        addVisit("VarStmt", this::visitVarDecl);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);


        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // param
        var paramCode = visit(node.getJmmChild(1));
        code.append("(" + paramCode + ")");

        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var afterParam = 2;
        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        var superClass = table.getSuper();
        if(!superClass.isEmpty()) {//if there is superclass
            code.append(" extends ").append(superClass);
        }
        code.append(L_BRACKET);
        code.append(NL);

        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }

    private String visitImportStmt(JmmNode importDeclaration, Void unused) {

        StringBuilder importStmt = new StringBuilder();
        importStmt.append("import ");

        // Extract the import value from the importDeclaration node
        String importValue = importDeclaration.get("ID");

        importStmt.append(importValue); // Append the import value directly

        importStmt.append(";\n");

        return importStmt.toString();
    }


    /*
    private String visitMainMethod(JmmNode methodDeclaration, Void unused) {
        StringBuilder method = new StringBuilder();
        method.append(".method public static main([Ljava/lang/String;).V {\n");

        for (JmmNode child : methodDeclaration.getChildren()) {
            // Ignore variable declarations
            if (!child.getKind().equals(VAR_DECL.toString())) {
                method.append(visit(child, unused));
            }
        }

        method.append("}\n");

        return method.toString();
    }
     */

    private String visitMainMethod(JmmNode methodDeclaration, Void unused) {
        StringBuilder method = new StringBuilder();
        method.append(".method ");
        method.append("public ");
        List<Symbol> parameters;
        method.append("static ");
        method.append("main(");
        parameters = table.getParameters("main");
        String codeParam = parameters.stream().map(OllirGeneratorVisitor::getCode).collect(Collectors.joining(", "));
        method.append(codeParam);
        method.append(").V");


        for (JmmNode child : methodDeclaration.getChildren()) {
            if (!child.getKind().equals("VarStmt")) {
                method.append(visit(child, unused));
            }
        }
        method.append("}\n");


        return method.toString();
    }

    private String visitVarDecl(JmmNode varDeclaration, Void unused) {

        StringBuilder variable = new StringBuilder();

        // Check if the parent node is a ClassStmt or not
        JmmNode parent = varDeclaration.getJmmParent();
        if (parent.getKind().equals(CLASS_DECL.toString())) {
            variable.append(".field private ");
        } else {
            // Extract the name and type of the variable from varDeclaration node
            String name = varDeclaration.get("name");
            String type = OptUtils.toOllirType(varDeclaration.getJmmChild(0));

            // Append the variable declaration
            variable.append(".field ");
            variable.append(name);
            variable.append(type);
            variable.append(";\n");
        }

        return variable.toString();
    }

    // Utility Functions -------------------------------------------

    public static String getCode(Symbol symbol) {
        return symbol.getName() + getTypeOfOllir(symbol.getType());
    }

    public static String getTypeOfOllir(Type type) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(".");

        if (type == null) {
            stringBuilder.append("V");
            return ".V";
        }

        if (type.isArray())
            stringBuilder.append("array.");
        String tipoJmm = type.getName();
        switch (tipoJmm) {
            case "int":
                stringBuilder.append("i32");
                break;
            case "boolean":
                stringBuilder.append("bool");
                break;
            case "void":
                stringBuilder.append("V");
                break;
            default:
                stringBuilder.append(tipoJmm);
                break;
        }
        return stringBuilder.toString();
    }

}
