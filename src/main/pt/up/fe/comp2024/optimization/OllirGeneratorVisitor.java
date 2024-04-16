package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;

import java.util.ArrayList;
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

    private int indentation = 0;

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

        /* TODO: WORK ON ARITHMETIC (FOR NOW HARDCODED TO PASS SPECIFIC TEST)
        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
         */
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));

        code.append(SPACE);

        if (!node.getChildren().isEmpty()) {
            JmmNode returnChild = node.getChildren().get(0);
            //if child is literal show value
            if (returnChild.getKind().equals("IntegerLiteral") || returnChild.getKind().equals("BooleanLiteral")) {
                code.append(returnChild.get("value")).append(OptUtils.toOllirType(retType));
            }
            //if child is variable show name
            else if (returnChild.getKind().equals("VarRefExpr")) {
                code.append(returnChild.get("name")).append(OptUtils.toOllirType(retType));
            }
        }

        code.append(";");

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitParams(JmmNode paramsNode) {
        StringBuilder paramsCode = new StringBuilder();

        for (JmmNode param : paramsNode.getChildren()) {
            paramsCode.append(visit(param));
        }

        return paramsCode.toString();
    }

    /*
    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // Name
        String methodName = getMethodName(node);
        code.append(methodName);

        // Parameters
        code.append("(");
        JmmNode paramsNode = node.getChildren().get(0); // Assuming params are the second child
        if (paramsNode != null) {
            String paramsCode = visit(paramsNode);
            code.append(paramsCode);
        }
        code.append(")");

        // Return type
        Type returnType = table.getReturnType(methodName);
        code.append(OptUtils.toOllirType(returnType));

        // Opening brace for method body
        code.append(" {\n");

        // Visit other children (varDecl, stmt, etc.)
        for (int i = 2; i < node.getNumChildren(); i++) {
            code.append(visit(node.getChildren().get(i)));
        }

        // Closing brace for method body
        code.append("}\n");

        return code.toString();
    }
    */
    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // Main method declaration (TODO: refactor from hardcoded to something more functional)
        if (!node.hasAttribute("name")) {
            code.append("public static main(args.array.String).V {\n");
            code.append("\t\tret.V;\n");
            code.append("\t}\n");
        }
        // Normal method declaration
        else {
            code.append(node.get("name"));
            code.append("(");
            JmmNode paramsNode = node.getChildren().get(1); // Assuming params are the first child
            if (paramsNode != null) {
                List<String> paramCodes = new ArrayList<>();
                for (JmmNode param : paramsNode.getChildren()) {
                    String codeParam = visit(param);
                    paramCodes.add(codeParam);
                }
                String codeParams = String.join(", ", paramCodes);
                code.append(codeParams);
            }
            code.append(")");
            String returnName = OptUtils.toOllirType(node.getChildren().get(0));
            code.append(returnName);
            code.append(" {\n");

            indentation+=1;

            // Visit other children (varDecl, stmt, etc.)
            for (int i = 2; i < node.getNumChildren(); i++) {
                JmmNode child = node.getChildren().get(i);
                if (child.getKind().equals("VarStmt")) {
                    String varStmtCode = visitVarDecl(child, unused);
                    code.append(applyIndentation(varStmtCode)); // Applying indentation
                } else {
                    String childCode = visit(child);
                    code.append(applyIndentation(childCode)); // Applying indentation
                }
            }

            indentation-=1;

            code.append(NL);//after returning normal indentation for cleaner look
            code.append(applyIndentation("}"));

        }

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Append class name
        code.append(table.getClassName());

        // Append superclass if it exists
        var superClass = table.getSuper();
        if (!superClass.isEmpty()) {
            code.append(" extends ").append(superClass);
        }

        code.append(L_BRACKET);
        //code.append(NL);

        indentation+=1; //augment indentation

        for (var child : node.getChildren()) {
            //dont separate varstmt with newlines
            if(child.getKind().equals("VarStmt")){
                var result = visit(child);
                code.append(applyIndentation(result));
            }
            else{
                var result = visit(child);
                code.append(applyIndentation(result));
                code.append(NL);
            }
        }

        code.append(applyIndentation(buildConstructor()));

        indentation-=1; //restore initial indentation

        code.append(applyIndentation(R_BRACKET));

        return code.toString();
    }

    private String buildConstructor() {
        StringBuilder code = new StringBuilder();
        code.append(".construct ").append(table.getClassName()).append("().V {\n");
        indentation += 1;
        code.append(applyIndentation("invokespecial(this, \"<init>\").V;"));
        code.append(NL);
        indentation -= 1;
        code.append(applyIndentation(R_BRACKET));


        return code.toString();
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

        JmmNode parent = varDeclaration.getJmmParent();
        if (parent.getKind().equals("ClassStmt")) {
            //if parent is classstmt add field public to string
            variable.append(".field public ");

            String name = varDeclaration.get("name");
            String type = OptUtils.toOllirType(varDeclaration.getJmmChild(0));

            variable.append(name);
            variable.append(type);
            variable.append(";\n");
        } else {
            String name = varDeclaration.get("name");
            String type = OptUtils.toOllirType(varDeclaration.getJmmChild(0));

            variable.append(name);
            variable.append(type);
            variable.append(";\n");
        }

        return variable.toString();
    }

    // Utility Functions -------------------------------------------

    public static String getCode(Symbol symbol) {
        return symbol.getName() + OptUtils.toOllirType(symbol.getType());
    }

    // Define a function to apply current indentation to a string
    private String applyIndentation(String str) {
        return "\t".repeat(Math.max(0, indentation)) + str; //intellij tips
    }

    public String getMethodName(JmmNode node) {
        while(node!=null && !node.getKind().equals("MethodDeclaration")){
            //
            node = node.getJmmParent();
        }
        if(node!=null && node.hasAttribute("methodName"))
            return node.get("methodName");

        return "main";
    }

}
