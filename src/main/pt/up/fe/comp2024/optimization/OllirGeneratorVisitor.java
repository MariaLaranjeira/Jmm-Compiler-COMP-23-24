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
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);;
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);

        //Stmts
        addVisit("ExprStmt", this::visitExprStmt);
        addVisit("ConditionalStmt", this::conditionalExprVisit);
        addVisit("IfStmt", this::ifStmtVisit);
        addVisit("BracketsStmt", this::bracketVisit);
        addVisit("ElseStmt", this::elseExprVisit);
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

    //DONE
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

    //Done
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

    //Done
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

    //Done
    private String visitReturn(JmmNode node, Void unused) {
        Type retType = table.getReturnType(currentMethod);

        StringBuilder code = new StringBuilder();
        OllirExprResult expr = exprVisitor.visit(node.getJmmChild(0));

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);
        code.append(expr.getCode());
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

        //in case of array initialization
        int numberOfChildren = node.getJmmChild(1).getChildren().size();
        if(node.getJmmChild(1).getKind().equals("ArrayInitializer") && numberOfChildren != 0 )
            for (int i = 0; i < numberOfChildren; i++) {
                //get the elements of the array
                JmmNode child = node.getJmmChild(1).getChildren().get(i);

                OllirExprResult arrayElem = exprVisitor.visit(child);

                code.append(arrayElem.getComputation());
                //assign each element of the array to the value
                code.append(node.getJmmChild(0).get("name"));
                code.append("[").append(i).append(".i32").append("].i32");
                code.append(" :=.i32 ").append(arrayElem.getCode())
                .append(END_STMT);
            }

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
        for (JmmNode child: node.getChildren()) {
            code.append(visit(child));
        }

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
}
