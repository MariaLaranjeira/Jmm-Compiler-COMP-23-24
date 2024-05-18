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
        addVisit("ImportStmt", this::visitImportStmt);
        addVisit("ClassStmt", this::visitClass);
        addVisit("VarDecl", this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);

        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);

        //addVisit("MainMethodStmt", this::visitMainMethod);
        addVisit("ExprStmt", this::visitExprStmt);
        addVisit("FunctionCall", this::visitFunctionCall);
        addVisit("ArrayAccess", this::visitArrayAccess);
        addVisit("ArrayAssignStmt", this::visitArrayAssign);
        addVisit("ArrayRef",this::visitArrayRef);

        setDefaultVisit(this::defaultVisit);
    }

    //DONE
    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    //DONE
    private String visitImportStmt(JmmNode importDeclaration, Void unused) {
        StringBuilder importStmt = new StringBuilder();
        importStmt.append("import ");

        for (String singleImport : table.getImports()) {
            if (singleImport.contains(importDeclaration.get("ID"))) {
                importStmt.append(singleImport);
            }
        }

        importStmt.append(";\n");

        return importStmt.toString();
    }

    //DONE - (MAIS AO MENOS)
    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        // Append class name
        code.append(table.getClassName());

        // Append superclass if it exists
        var superClass = table.getSuper();

        if (superClass != null) {
            code.append(" extends ").append(superClass);
        }

        code.append(L_BRACKET);

        for (var child : node.getChildren()) {
            if(child.getKind().equals("VarDecl")){
                //VarDecl
                var result = visit(child);
                code.append(result);
            }
            else{
                //ClassStmt
                code.append(visit(child));
                code.append(NL);
            }
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    //Done (mais ao menos - analisar melhor as situações)
    private String visitVarDecl(JmmNode varDeclaration, Void unused) {
        StringBuilder variable = new StringBuilder();
        JmmNode parent = varDeclaration.getJmmParent();

        if (parent.getKind().equals("ClassStmt")) {
            //if parent is classStmt add field public to string
            variable.append(".field public ");
        }
        String name = varDeclaration.get("name");
        String type = OptUtils.toOllirType(varDeclaration.getJmmChild(0));

        variable.append(name);
        variable.append(type);
        variable.append(";\n");

        return variable.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method public ");
        boolean isMain = node.getKind().equals("MainMethodDecl");

        // Main method declaration
        if (isMain) {
            code.append("static main(args.array.String).V {\n");
        }

        // Normal method declaration
        else {
            code.append(node.get("name")).append("(");

            //Parameters
            List<Symbol> parameters = table.getParameters(getMethodName(node));

            if (!parameters.isEmpty()) {
                List<String> paramCodes = new ArrayList<>();
                for (Symbol param : parameters) {
                    paramCodes.add(OptUtils.getCode(param));
                }
                code.append(String.join(", ", paramCodes));
            }
            code.append(")");
            String returnName = OptUtils.toOllirType(table.getReturnType(getMethodName(node)));
            code.append(returnName).append(" {\n");
        }


        for (int i = isMain ? 0 : 2; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChildren().get(i);
            if (!child.getKind().equals("VarStmt")) {
                String childCode = visit(child);
                code.append(applyIndentation(childCode)); // Applying indentation
            }
        }


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


    private String visitArrayAccess(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var array = exprVisitor.visit(node.getJmmChild(0));
        var index = exprVisitor.visit(node.getJmmChild(1));

        // Generate the array access code
        code.append(array.getComputation())
                .append(index.getComputation())
                .append(array.getCode()).append("[")
                .append(index.getCode())
                .append("]");

        return code.toString();
    }

    private String visitArrayAssign(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var array = exprVisitor.visit(node.getJmmChild(0));
        var index = exprVisitor.visit(node.getJmmChild(1));
        var value = exprVisitor.visit(node.getJmmChild(2));

        // Generate the array assignment code
        code.append(array.getComputation())
                .append(index.getComputation())
                .append(value.getComputation())
                .append(array.getCode()).append("[")
                .append(index.getCode())
                .append("] := ")
                .append(value.getCode())
                .append(";\n");

        return code.toString();
    }

    private String visitArrayRef(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var array = exprVisitor.visit(node.getJmmChild(0));

        // Generate the array reference code
        code.append(array.getCode());

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation())
                .append(rhs.getComputation())
                .append(lhs.getCode()).append(SPACE)
                .append(ASSIGN);

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(typeString)
                .append(SPACE)
                .append(rhs.getCode())
                .append(END_STMT);

        if(node.getChild(1).getKind().equals("NewObject")){
            code.append(applyIndentation(visit(node.getChild(1))));
            String newVarName = node.getChild(0).get("name");
            code.append(applyIndentation(newVarName)).append(typeString).append(SPACE).append(ASSIGN).append(typeString).append(SPACE).append(rhs.getComputation()).append(typeString).append(END_STMT);
        }

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

        return id + typeCode;
    }

    private String buildConstructor() {
        StringBuilder code = new StringBuilder();
        code.append(".construct ").append(table.getClassName()).append("().V {\n");
        indentation++;
        code.append(applyIndentation("invokespecial(this,\"<init>\").V;"));
        code.append(NL);
        indentation --;
        code.append(applyIndentation(R_BRACKET));


        return code.toString();
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

        String functionName = node.get("value");
        String first;
        String returnCode;

        Type returnType = table.getReturnType(functionName);

        if(returnType!=null){
            returnCode = OptUtils.toOllirType(returnType);
        }
        else{
            returnCode = ".V";
        }

        boolean isClass = false;
        boolean isImported = false;
        boolean isLocalVariable = false;
        boolean isThis = false;

        String className = table.getClassName();

        if(node.getChild(0).get("name").equals(className)){
            isClass = true;
        }

        //check if first parameter is in imports (hardcoded to check 1st,todo: change grammar to include params in the call)
        for(var imported : table.getImports()){
            if (node.getChild(0).get("name").equals(imported)) {
                isImported = true;
                break;
            }
        }

        String methodName;
        JmmNode ancestorMethodNode = node.getAncestor("MethodDecl").get();
        if(ancestorMethodNode.hasAttribute("name")){
            methodName = ancestorMethodNode.get("name");
        }else{
            methodName = "main";
        }


        for(var variable : table.getLocalVariables(methodName)){
            if(variable.getName().equals(node.getChild(0).get("name"))){
                isLocalVariable = true;
                break;
            }
        }

        //construct initial code, excluding parameters
        if(isImported){
            first = node.getChild(0).get("name");
            code.append("invokestatic(").append(first).append(", \"").append(functionName).append("\"");
        }
        else{
            if(isLocalVariable){
                first = node.getChild(0).get("name");
                //String className = node.getAncestor("ClassStmt").get().get("name");
                code.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(functionName).append("\"");;
            }else{
            first = "this";
            //String className = node.getAncestor("ClassStmt").get().get("name");
            code.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(functionName).append("\"");;}
        }

        if(node.getNumChildren()>1){
            code.append(", ");
            for (int i = 1; i < node.getNumChildren(); i++) {
                JmmNode child = node.getChild(i);
                code.append(child.get("name"));
                code.append(getVarType(child.get("name"),child));
                if(i != node.getNumChildren()-1){
                    code.append(", ");
                }
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
        //return "\t".repeat(Math.max(0, indentation)) + str; //intellij tips
        return str;
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
