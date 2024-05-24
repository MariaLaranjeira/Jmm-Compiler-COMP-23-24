package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.JavammParser;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final SymbolTable table;

    public String currentMethod;

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
        addVisit("ArrayInitializer", this::visitArrayInitializer);

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
        StringBuilder computation = new StringBuilder();
        var rhs = visit(node.getJmmChild(1));
        String code = OptUtils.getTemp() + ".i32";

        computation.append(code)
                .append(" :=").append(".i32 ")
                .append(node.getChild(0).get("name"))
                .append("[").append(rhs.getCode()).append("].i32;\n");

        return new OllirExprResult(code, rhs.getComputation()+ computation);

    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var rhs = visit(node.getJmmChild(0));

        code.append("new(array, ").append(rhs.getCode()).append(").array.i32");

        return new OllirExprResult(code.toString(), rhs.getComputation());
    }

    private OllirExprResult visitArrayInitializer(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        int numberOfChildren = node.getChildren().size();
        code.append("new(array, ").append(numberOfChildren).append(".i32)").append(".array.i32");

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        return new OllirExprResult(node.get("name") + ollirType);
    }

    private OllirExprResult visitNewObject(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        String objectType = jmmNode.get("value");

        code.append("new(").append(objectType).append(").").append(objectType);
        return new OllirExprResult(code.toString());

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

    private OllirExprResult visitFunctionCall(JmmNode node, Void unused) {
        String functionName = node.get("value");
        String first;
        String returnCode;
        String nameCall;
        Type returnType = table.getReturnType(functionName);
        returnCode = OptUtils.toOllirType(returnType);

        boolean isImported = false;
        boolean isLocalVariable = false;

        //check if first parameter is in imports
        String className = table.getClassName();

        for(var imported : table.getImports()){
            if (node.getChild(0).get("name").equals(imported)) {
                isImported = true;
                break;
            }
        }
        for(var variable : table.getLocalVariables(currentMethod)){
            if(variable.getName().equals(node.getChild(0).get("name"))){
                isLocalVariable = true;
                break;
            }
        }

        //Verify if we have a this call function
        if(node.getJmmChild(0).get("name").equals("this"))  nameCall = OptUtils.getTemp();
        else nameCall = node.getJmmChild(0).get("name");

        //Get Parameters Types, so i can know if we have a vararg
        List<Symbol> parameters = table.getParameters(functionName);
        int numParameters = parameters.size(); //number of parameters
        int numParametersCallFunction = node.getNumChildren(); //num of parameters in call function
        StringBuilder computation = new StringBuilder();
        List<String> codes = new ArrayList<>();

        //Get the computations before the function Call
        //computations of the parameters in the function call
        if(numParametersCallFunction > 1){
            for (int i = 1; i < numParametersCallFunction; i++) {
                //If it is a vararg = array creation
                if(numParametersCallFunction != numParameters && numParameters - 1 < i){
                    String temp = OptUtils.getTemp();
                    String callType = temp + ".array.i32";

                    //Get computation of the vararg - array
                    for(int j = i; j < numParametersCallFunction; j++) {
                        JmmNode child = node.getChild(j);
                        OllirExprResult arrayElem = visit(child);

                        computation.append(arrayElem.getComputation());
                        //assign each element of the array to the value
                        computation.append(nameCall);
                        computation.append("[").append(j).append(".i32").append("].i32");
                        computation.append(" :=.i32 ").append(arrayElem.getCode()).append(END_STMT);
                    }

                    //Add code
                    codes.add(callType);
                    break;
                }

                else{
                    JmmNode child = node.getChild(i);
                    OllirExprResult result = visit(child);
                    computation.append(result.getComputation());
                    codes.add(result.getCode());
                }
            }
        }

        String code = OptUtils.getTemp() + returnCode;

        if(isImported){
            first = node.getChild(0).get("name");
            computation.append("invokestatic(").append(first).append(", \"").append(functionName).append("\"");
        }
        else{
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(returnCode).append(SPACE);

            if(isLocalVariable){
                first = node.getChild(0).get("name");
                computation.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(functionName).append("\"");
            }
            else{
                first = "this";
                computation.append("invokevirtual(").append(first).append(".").append(className).append(", \"").append(functionName).append("\"");
            }
        }

        //Append the code of the parameters in the invocation of function
        if(node.getNumChildren()>1){
            computation.append(", ");
            for (int i = 0; i < codes.size(); i++) {
                computation.append(codes.get(i));
            }
        }

        computation.append(")");

        //Append the return code
        computation.append(returnCode);
        computation.append(";\n");

        return new OllirExprResult(code, computation);
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
