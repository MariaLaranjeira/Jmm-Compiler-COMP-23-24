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

        //Stmts
        addVisit("ConditionalStmt", this::conditionalExprVisit);
        addVisit("IfStmt", this::ifStmtVisit);
        addVisit("BracketsStmt", this::bracketVisit);
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
        exprVisitor.currentMethod = currentMethod;

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

        code.append(";\n");

        return code.toString();
    }

    //TODO: NOT COMPLETE
    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        OllirExprResult  rhs = exprVisitor.visit(node.getJmmChild(1));
        OllirExprResult lhs = null;

        //in case of array assign
        if(node.getJmmChild(0).getKind().equals("ArrayAccess")){
            var index = exprVisitor.visit(node.getJmmChild(1));
            code.append(node.getJmmChild(0).getChild(0).get("name"));
            code.append("[").append(index.getCode()).append("].i32");
        }
        else{
            lhs = exprVisitor.visit(node.getJmmChild(0));
            code.append(rhs.getComputation());
            code.append(lhs.getCode());
        }

        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(SPACE).append(ASSIGN)
            .append(typeString).append(SPACE)
            .append(rhs.getCode()).append(END_STMT);

        if(node.getChild(1).getKind().equals("NewObject") && lhs != null){
            code.append("invokespecial(").append(lhs.getCode()).append(", \"<init>\").V;\n");
        }

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var firstChild = node.getChild(0);
        if(firstChild.getKind().equals("FunctionCall")){
            OllirExprResult expr = exprVisitor.visit(node.getJmmChild(0));
            code.append(expr.getComputation());
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

    private String conditionalExprVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        int currentIfNum = ifNum++;

        // Visit the if statement
        code.append(visit(node.getJmmChild(0))); // if(condition)
        code.append("goto if" + currentIfNum + ";\n");

        // Visit the else statement if it exists
        if (node.getNumChildren() > 1) {
            code.append(visit(node.getJmmChild(1))); //stmt else
            code.append("goto endif" + currentIfNum + ";\n");
        }

        code.append("if" + currentIfNum + ":\n");
        code.append(visit(node.getJmmChild(0).getJmmChild(1))); // stmt of the if(condition)

        code.append("endif" + currentIfNum + ":\n");

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

    private String bracketVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (JmmNode child : node.getChildren()) {
            code.append(visit(child));
        }
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
