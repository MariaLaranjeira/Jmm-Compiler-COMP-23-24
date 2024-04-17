package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(CallInstruction.class, this::generaterateCallInstruction);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL);


        // generate super class name
        var extended = ollirResult.getOllirClass().getSuperClass();

        String defaultConstructor;

        if (extended != null) {
            code.append(".super ").append(extended).append(NL);
            defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(extended);

        } else {
            code.append(".super java/lang/Object").append(NL);
            defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial java/lang/Object/<init>()V
                    return
                .end method
                """;
        }

        // generate fields
        var fields = ollirResult.getOllirClass().getFields();
        for (var field : fields) {
            code.append(".field ").append(field.getFieldName());
            switch (field.getFieldType().toString()){
                case "INT32" -> code.append(" I").append(NL);
                case "BOOLEAN" -> code.append(" Z").append(NL);
                default -> throw new NotImplementedException(field.getFieldType());
            }
        }


        code.append(NL);

        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        //System.out.println(code);


        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";


        var methodName = method.getMethodName();
        var extra = method.isStaticMethod() ? "static " : method.isFinalMethod() ? "final " : "";

        code.append(".method ").append(modifier).append(extra).append(methodName).append("(");

        // generate parameters
        var params = method.getParams();
        var paramCode = new StringBuilder();
        for (Element param : params) {
            switch (param.getType().toString()) {
                case "INT32" -> paramCode.append("I");
                case "BOOLEAN" -> paramCode.append("Z");
                case "STRING[]" -> paramCode.append("[Ljava/lang/String;");
                default -> throw new NotImplementedException(param.getType());
            }
        }

        code.append(paramCode).append(")");

        //generate return type
        var returnType = method.getReturnType().toString();
        switch (returnType) {
            case "INT32" -> code.append("I").append(NL);
            case "BOOLEAN" -> code.append("Z").append(NL);
            case "VOID" -> code.append("V").append(NL);
            case "STRING[]" -> code.append("[Ljava/lang/String;").append(NL);
            default -> throw new NotImplementedException(returnType);
        }

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }


        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        code.append("getfield ");

        var fc = getField.getOperands().get(0).getType();
        String fieldClass = "";
        if (fc != null) {
            fieldClass = fc.toString();
        }

        if (ollirResult.getOllirClass().getSuperClass() != null){
            if (fieldClass.contains(ollirResult.getOllirClass().getSuperClass())){
                code.append(ollirResult.getOllirClass().getSuperClass()).append("/");
            }
        } else {
            code.append(ollirResult.getOllirClass().getClassName()).append("/");
        }

        var tmp = getField.getOperands().get(1);
        Operand lhs = (Operand) tmp;

        code.append(lhs.getName());

        switch (tmp.getType().toString()){
            case "INT32" -> code.append(" I");
            case "BOOLEAN" -> code.append(" Z");
            default -> throw new NotImplementedException(tmp.getType());
        }

        code.append(NL);


        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();

        code.append("aload_0").append(NL);

        code.append(generators.apply(putField.getOperands().get(2)));

        code.append("putfield ");


        var fc = putField.getOperands().get(0).getType();
        String fieldClass = "";
        if (fc != null) {
            fieldClass = fc.toString();
        }

        if (ollirResult.getOllirClass().getSuperClass() != null){
            if (fieldClass.contains(ollirResult.getOllirClass().getSuperClass())){
                code.append(ollirResult.getOllirClass().getSuperClass()).append("/");
            }
        } else {
            code.append(ollirResult.getOllirClass().getClassName()).append("/");
        }

        var tmp = putField.getOperands().get(1);
        Operand lhs = (Operand) tmp;

        code.append(lhs.getName());

        switch (tmp.getType().toString()){
            case "INT32" -> code.append(" I");
            case "BOOLEAN" -> code.append(" Z");
            default -> throw new NotImplementedException(tmp.getType());
        }

        code.append(NL).append("aload_0").append(NL);


        return code.toString();
    }

    private String generaterateCallInstruction (CallInstruction callInstruction){
        var code = new StringBuilder();

        code.append("invokespecial ");

        code.append(NL);
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();


        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // TODO: Hardcoded for int type, needs to be expanded
        code.append("istore ").append(reg).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if(returnInst.getOperand() == null) {
            return "return" + NL;
        }

        code.append(generators.apply(returnInst.getOperand()));

        if(Objects.equals(currentMethod.getReturnType().toString(), "INT32") || Objects.equals(currentMethod.getReturnType().toString(), "BOOLEAN")) {
            code.append("ireturn").append(NL);
        } else {
            code.append("return").append(NL);
        }

        return code.toString();
    }

}
