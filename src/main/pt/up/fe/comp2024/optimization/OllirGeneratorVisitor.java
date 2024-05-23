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

    private int whileNum = 0;
    private int ifNum = 0;

    private String currentMethod;

    private final SymbolTable table;

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
        addVisit(METHOD_DECL, this::visitMethodDecl);;
        addVisit(RETURN_STMT, this::visitReturn);

        //Not done
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("ExprStmt", this::visitExprStmt);
        addVisit("FunctionCall", this::visitFunctionCall);

        addVisit("ConditionalStmt", this::conditionalExprVisit);
        addVisit("IfStmt", this::ifStmtVisit);
        addVisit("ElseExpr", this::elseExprVisit);
        addVisit("WhileStmt", this::whileVisit);


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
                if(child.getKind().equals("VarStmt")){
                //VarDecl
                var result = visit(child);
                code.append(result);
            }
            else{
                //methodDecl
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

    //Done (mais ao menos)
    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method public ");
        boolean isMain = node.getKind().equals("MainMethodDecl");

        //Get the current Method
        if (node.getAttributes().contains("name")) {
            currentMethod = node.get("name");
        } else {
            currentMethod = "main";
        }

        // Main method declaration
        if (isMain) {
            code.append("static main(args.array.String).V {\n");
        }
        // Normal method declaration
        else {
            code.append(node.get("name")).append("(");

            //Parameters
            List<Symbol> parameters = table.getParameters(currentMethod);
            if (!parameters.isEmpty()) {
                List<String> paramCodes = new ArrayList<>();
                for (Symbol param : parameters) {
                    paramCodes.add(getCode(param));
                }
                code.append(String.join(", ", paramCodes));
            }
            code.append(")");

            //Return Type
            String returnName = OptUtils.toOllirType(table.getReturnType(node.get("name")));
            code.append(returnName).append(" {\n");
        }

        //Todo: Come here again
        //The rest of the code before method name and parameters
        for (JmmNode child : node.getChildren()) {
            if (!child.getKind().equals("VarStmt")) {
                code.append(visit(child));
            }
        }

        if(isMain){
            code.append("\nret.V;\n");
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    //Done (but not sure)
    private String visitReturn(JmmNode node, Void unused) {
        Type retType = table.getReturnType(currentMethod);

        StringBuilder code = new StringBuilder();
        OllirExprResult expr = exprVisitor.visit(node.getJmmChild(0));

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        JmmNode returnChild = node.getChildren().get(0);

        //if child is literal show value
        if (returnChild.getKind().equals("IntegerLiteral") || returnChild.getKind().equals("BooleanLiteral")) {
            code.append(returnChild.get("value")).append(OptUtils.toOllirType(retType));
        }
        //if child is variable show name
        else if (returnChild.getKind().equals("VarRefExpr")) {
            code.append(returnChild.get("name")).append(OptUtils.toOllirType(retType));
        }

        code.append(";");

        return code.toString();
    }

    //TODO: NOT COMPLETE
    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        code.append(lhs.getComputation())
                .append(rhs.getComputation());

        code.append(lhs.getCode())
                .append(SPACE)
                .append(ASSIGN);

        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(typeString)
                .append(SPACE)
                .append(rhs.getCode())
                .append(END_STMT);

        if(node.getChild(1).getKind().equals("NewObject")){
            code.append(visit(node.getChild(1)));
            String newVarName = node.getChild(0).get("name");
            code.append(newVarName).append(typeString).append(SPACE).append(ASSIGN).append(typeString).append(SPACE).append(rhs.getComputation()).append(typeString).append(END_STMT);
        }

        return code.toString();
    }

    //TODO: NOT SURE WE NEED IT
    private String visitExprStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var firstChild = node.getChild(0);
        if(firstChild.getKind().equals("FunctionCall")){
            code.append(visit(firstChild));
        }
        return code.toString();
    }

    private String buildConstructor() {
        StringBuilder code = new StringBuilder();
        code.append(".construct ").append(table.getClassName()).append("().V {\n");
        code.append("invokespecial(this,\"<init>\").V;");
        code.append(NL);
        code.append(R_BRACKET);

        return code.toString();
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

        //Get the computations before the function Call
        if(node.getNumChildren()>1){
            for (int i = 1; i < node.getNumChildren(); i++) {
                JmmNode child = node.getChild(i);
                OllirExprResult result =  exprVisitor.visit(child);
                code.append(result.getComputation());
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
                code.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(functionName).append("\"");
            }
            else{
                first = "this";
                code.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(functionName).append("\"");
            }
        }

        // in here we have an error with value and name
        if(node.getNumChildren()>1){
            code.append(", ");
            if(node.getNumChildren()>1){
                for (int i = 1; i < node.getNumChildren(); i++) {
                    JmmNode child = node.getChild(i);
                    OllirExprResult result =  exprVisitor.visit(child);
                    code.append(result.getCode());
                }
            }
        }

        code.append(")");
        code.append(returnCode);
        code.append(";");

        return code.toString();
    }


    //TODO: IM HERE!

    private String conditionalExprVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Visit the if statement
        code.append(visit(node.getJmmChild(0))); // if(condition)
        code.append("goto if" + ifNum + ";\n");

        // Visit the else statement if it exists
        if (node.getNumChildren() > 1) {
            code.append(visit(node.getJmmChild(1))); //stmt else
            code.append("goto endif" + ifNum + ";\n");
        }

        code.append("if" + ifNum + ":\n");
        code.append(visit(node.getJmmChild(0).getJmmChild(1))); // stmt of the if(condition)

        code.append("endif" + ifNum + ":\n");

        ifNum++;
        return code.toString();
    }


    private String ifStmtVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();;

        // Visit the condition expression
        OllirExprResult conditionCode = exprVisitor.visit(node.getJmmChild(0));

        code.append(conditionCode.getComputation());
        code.append("if (").append(conditionCode.getCode()).append(") ");

        return code.toString();
    }

    private String elseExprVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Visit the else body
        code.append(visit(node.getJmmChild(0)));

        return code.toString();
    }

    private String whileVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        String whileLabel = "whileCond" + whileNum;
        String endWhile = "whileEnd" + whileNum;
        String body = "whileLoop" + whileNum;

        // Add the while label
        code.append(whileLabel).append(":\n");

        // Visit the condition expression
        OllirExprResult conditionCode = exprVisitor.visit(node.getJmmChild(0));

        // If the condition is true go to the  body of the WhileLoop
        code.append(conditionCode.getComputation());
        code.append("if (").append(conditionCode.getCode()).append(") goto ").append(body).append(";\n");
        // Else end Loop
        code.append("goto ").append(endWhile).append(";\n");

        // The Body
        code.append(body).append(":\n");
        // Body of the while loop
        code.append(visit(node.getJmmChild(1)));
        code.append("goto ").append(whileLabel).append(";\n");

        // The End Loop
        code.append(endWhile).append(":\n");

        whileNum++;
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

    private String getVarType(String nome, JmmNode id) {

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
            if (currentMethod.equals(method) || currentMethod.equals("")) {
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
