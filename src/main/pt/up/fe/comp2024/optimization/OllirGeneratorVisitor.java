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
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("ImportStmt", this::visitImportStmt);
        //addVisit("MainMethodStmt", this::visitMainMethod);
        addVisit("VarStmt", this::visitVarDecl);
        addVisit("ExprStmt", this::visitExprStmt);
        addVisit("FunctionCall", this::visitFunctionCall);

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
        code.append(applyIndentation("ret" + OptUtils.toOllirType(retType)));

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

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        boolean isMain = false;


        if (!node.hasAttribute("name")){
            isMain = true;
        }

        // Main method declaration (TODO: refactor from hardcoded to something more functional)
        if (isMain) {
            code.append("public static main(args.array.String).V {\n");
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

                code.append(")");
                String returnName = OptUtils.toOllirType(node.getChildren().get(0));
                code.append(returnName);
                code.append(" {\n");
            }
        }

        indentation+=1;
        int i;
        if(isMain){
            i = 0;
        }else{
            i=2;
        }

        for (; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChildren().get(i);
            if (!child.getKind().equals("VarStmt")) {
                String childCode = visit(child);
                code.append(applyIndentation(childCode)); // Applying indentation
            }
        }

        indentation-=1;

        if(isMain){
            code.append(applyIndentation("ret.V;"));
            code.append(applyIndentation("}"));
        }
        else{
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
        if (superClass != null) {
            code.append(" extends ").append(superClass);
        }
        else{
            code.append(" extends ").append("Object");
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

    private String visitImportStmt(JmmNode importDeclaration, Void unused) {

        StringBuilder importStmt = new StringBuilder();
        importStmt.append("import ");

        // Extract the import value from the importDeclaration node
        String importValue = importDeclaration.get("ID");

        importStmt.append(importValue); // Append the import value directly

        importStmt.append(";\n");

        return importStmt.toString();
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

    private String visitExprStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var firstChild = node.getChild(0);
        if(firstChild.getKind().equals("FunctionCall")){
            code.append(visit(firstChild,unused));
        }
        return code.toString(); //TODO: complete with other exprstmt, to be defined in exprgenerator
    }

    // TODO: needs serious refactoring. probably in grammar as distinction between the calling class and the parameters is essential
    public String visitFunctionCall(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        String methodName = node.get("value");
        String first;
        String returnCode;

        Type returnType = table.getReturnType(methodName);

        if(returnType!=null){
            returnCode = OptUtils.toOllirType(returnType);
        }
        else{
            returnCode = ".V";
        }


        boolean isImported = false;

        //check if first parameter is in imports (hardcoded to check 1st,todo: change grammar to include params in the call)
        for(var imported : table.getImports()){
            if (node.getChild(0).get("name").equals(imported)) {
                isImported = true;
                break;
            }
        }

        //construct initial code, excluding parameters
        if(isImported){
            first = node.getChild(0).get("name");
            code.append("invokestatic(").append(first).append(", \"").append(methodName).append("\"");
        }
        else{
            first = "this";
            String className = node.getAncestor("ClassStmt").get().get("name");
            code.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(methodName).append("\"");;
        }

        if(node.getNumChildren()>1){
            code.append(",");
            for (int i = 1; i < node.getNumChildren(); i++) {
                JmmNode child = node.getChild(i);
                code.append(child.get("name"));
                code.append(getVarType(child.get("name"),child));
            }
        }

        code.append(")");
        code.append(returnCode);
        code.append(";");

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

    private String getVarType(String nome, JmmNode id) {
        String methodName = getMethodName(id);

        //check if is imported (always return .v type)
        for(var imported : table.getImports()){
            if (nome.equals(imported)) {
                return ".v";
            }
        }

        if (nome.equals(table.getClassName())) {
            return "." + nome;
        }

        if (nome.equals("boolean") || nome.equals("false") || nome.equals("true")) {
            return ".bool";
        }

        for (String method : table.getMethods()) {
            if (methodName.equals(method) || methodName.equals("")) {
                for (Symbol symbol : table.getParameters(method))
                    if (nome.equals(symbol.getName()))
                        return OptUtils.toOllirType(symbol.getType());

                for (Symbol symbol : table.getLocalVariables(method))
                    if (nome.equals(symbol.getName())) {
                        return OptUtils.toOllirType(symbol.getType());
                    }
            }
        }

        for (Symbol symbol : table.getFields())
            if (nome.equals(symbol.getName())) {
                return OptUtils.toOllirType(symbol.getType());
            }


        return ".i32"; // int
    }

    private Type getInputType(String methodName, String funcInput) {
        for (var m : table.getMethods()) {
            for (Symbol v : table.getLocalVariables(m)) {
                if (v.getName().equals(funcInput)) {
                    return v.getType();
                }
            }
        }
        return null;
    }

}
