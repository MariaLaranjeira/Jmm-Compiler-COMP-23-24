package pt.up.fe.comp2024.backend;

import com.sun.jdi.ObjectReference;
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
        generators.put(OpCondInstruction.class, this::generateOpCondition);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCondition);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInstruction);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        //System.out.println(code);
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

        if (extended != null && !extended.equals("Object")) {
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
                case "INT32[]" -> paramCode.append("[I");
                case "BOOLEAN" -> paramCode.append("Z");
                case "STRING[]" -> paramCode.append("[Ljava/lang/String;");
                case "VOID" -> paramCode.append("V");
                case "SHORT" -> paramCode.append("S");
                case "CLASS" -> paramCode.append("L");
                case "STRING" -> paramCode.append("[Ljava/lang/String;");
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
            case "SHORT" -> code.append("S").append(NL);
            case "INT32[]" -> code.append("[I").append(NL);
            default -> throw new NotImplementedException(returnType);
        }

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {

            for (var label : method.getLabels(inst)){
                code.append(label).append(":").append(NL);
            }

            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);

            if (inst.getInstType() == InstructionType.CALL) {
                var callInst = (CallInstruction) inst;
                if (callInst.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    code.append(TAB).append("pop").append(NL);
                }
            }

        }



        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        Operand op = getField.getObject();
        String prev = generateOperand(op);
        code.append(prev);

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

        Operand op = putField.getObject();
        String prev = generateOperand(op);
        code.append(prev);

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

        return code.toString();
    }

    private String generaterateCallInstruction (CallInstruction callInstruction){
        var code = new StringBuilder();
        String invokeType = callInstruction.getInvocationType().toString();

        switch (invokeType) {
            case "NEW":
                var tmp = callInstruction.getCaller();
                Operand lhs = (Operand) tmp;

                if(lhs.getType().getTypeOfElement() == ElementType.ARRAYREF) {
                    for (var argument : callInstruction.getArguments())
                        code.append(generators.apply(argument));

                    code.append("newarray int");
                } else {
                    code.append("new ").append(lhs.getName()).append(NL).append("dup");
                }

                break;
            case "invokespecial":
                var tmp2 = callInstruction.getCaller();
                Operand lhs2 = (Operand) tmp2;
                code.append(generators.apply(lhs2));
                code.append("invokespecial ");

                var callerClass = lhs2.getType().toString();
                code.append(callerClass,10,callerClass.length()-1).append("/");

                //code.append(ollirResult.getOllirClass().getClassName()).append("/");
                code.append("<init>()V");
                break;
            case "invokestatic":

                for (var arg : callInstruction.getArguments()) {
                    code.append(generators.apply(arg));
                }
                //code.append(NL);
                code.append("invokestatic ");
                Operand lhs3 = (Operand) callInstruction.getCaller();
                code.append(lhs3.getName()).append("/");
                LiteralElement le = (LiteralElement) callInstruction.getOperands().get(1);
                code.append(le.getLiteral(), 1, le.getLiteral().length() - 1);

                code.append("(");
                for ( Element p : callInstruction.getArguments()) {
                    var paramType = p.getType().toString();
                    var paramTypeAppend = switch (paramType) {
                        case "INT32" -> "I";
                        case "BOOLEAN" -> "Z";
                        case "STRING[]" -> "[Ljava/lang/String;";
                        case "VOID" -> "V";
                        case "SHORT" -> "S";
                        case "INT32[]" -> "[I";
                        default -> throw new NotImplementedException(paramType);
                    };
                    code.append(paramTypeAppend);
                }
                code.append(")");

                var returnType = callInstruction.getReturnType().getTypeOfElement().toString();
                var returnTypeAppend = switch (returnType) {
                    case "INT32" -> "I";
                    case "BOOLEAN" -> "Z";
                    case "VOID" -> "V";
                    case "SHORT" -> "S";
                    case "INT32[]" -> "[I";
                    case "STRING[]" -> "[Ljava/lang/String;";
                    default -> throw new NotImplementedException(returnType);
                };
                code.append(returnTypeAppend);
                break;
            case "invokevirtual":
                var call = callInstruction.getCaller();
                var callerOp = (Operand) call;
                code.append(generators.apply(callerOp));
                for (var arg : callInstruction.getArguments()) {
                    code.append(generators.apply(arg));
                }
                code.append("invokevirtual ");

                var callerClass2 = callerOp.getType().toString();
                if (callerClass2.contains("OBJECTREF")) {
                    code.append(callerClass2,10,callerClass2.length()-1).append("/");
                } else if (callerClass2.contains("THIS")) {
                    code.append(callerClass2,5,callerClass2.length()-1).append("/");
                } else if (callerClass2.contains("CLASS")){
                    code.append(callerClass2,6,callerClass2.length()-1).append("/");
                }

                //code.append(ollirResult.getOllirClass().getClassName()).append("/");
                LiteralElement le2 = (LiteralElement) callInstruction.getOperands().get(1);
                code.append(le2.getLiteral(), 1, le2.getLiteral().length() - 1);
                code.append("(");
                for ( Element p : callInstruction.getArguments()) {
                    var paramType = p.getType().toString();
                    var paramTypeAppend = switch (paramType) {
                        case "INT32" -> "I";
                        case "BOOLEAN" -> "Z";
                        case "STRING[]" -> "[Ljava/lang/String;";
                        case "VOID" -> "V";
                        case "SHORT" -> "S";
                        case "INT32[]" -> "[I";
                        default -> throw new NotImplementedException(paramType);
                    };
                    code.append(paramTypeAppend);
                }
                code.append(")");

                var returnType2 = callInstruction.getReturnType().getTypeOfElement().toString();
                var returnTypeAppend2 = switch (returnType2) {
                    case "INT32" -> "I";
                    case "BOOLEAN" -> "Z";
                    case "VOID" -> "V";
                    case "SHORT" -> "S";
                    case "INT32[]" -> "[I";
                    case "STRING[]" -> "[Ljava/lang/String;";
                    default -> throw new NotImplementedException(returnType2);
                };
                code.append(returnTypeAppend2);
                break;
        }

        code.append(NL);
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();
        var lhs = assign.getDest();
        var rhs = assign.getRhs();

        if(lhs instanceof ArrayOperand aop){
            switch (aop.getName()) {
                case "this" -> code.append("aload_0").append(NL);
                default -> code.append("aload ").append(currentMethod.getVarTable().get(aop.getName()).getVirtualReg()).append(NL);
            }

            code.append(generators.apply(aop.getIndexOperands().get(0)));
        }

        if(rhs instanceof SingleOpInstruction && ((SingleOpInstruction) assign.getRhs()).getSingleOperand() instanceof ArrayOperand aop){

            switch (aop.getName()) {
                case "this" -> code.append("aload_0").append(NL);
                default -> code.append("aload ").append(currentMethod.getVarTable().get(aop.getName()).getVirtualReg()).append(NL);
            }

            code.append(generators.apply(((ArrayOperand) ((SingleOpInstruction) assign.getRhs()).getSingleOperand()).getIndexOperands().get(0)));

            code.append("iaload ").append(NL);

        } else {
            code.append(generators.apply(assign.getRhs()));
        }


        // store value in the stack in destination
        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        if(operand instanceof ArrayOperand){
            return code.append("iastore").append(NL).toString();
        }

        switch(operand.getType().toString()) {
            case "INT32" -> code.append("istore ");
            case "BOOLEAN" -> code.append("istore ");
            default -> code.append("astore ");
        }

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        code.append(reg).append(NL);


        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        String l = literal.getLiteral();
        int n = Integer.parseInt(l);

        if (n >= 0 && n <= 5) {
            return "iconst_" + n + NL;
        } else if (n >=6 && n<= 127) {
            return "bipush " + n + NL;
        } else if (n >= 128 && n <= 32767) {
            return "sipush " + n + NL;
        } else {
            return "ldc " + n + NL;
        }
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var operandType = operand.getType().toString();

        if (operandType.contains("OBJECTREF") || operandType.contains("ARRAYREF")) {
            return "aload " + reg + NL;
        }
        else if (operandType.contains("THIS")) {
            return "aload_0" + NL;
        }
        else if (operandType.contains("CLASS")) {
            return "aload " + reg + NL;
        }

        switch(operandType) {
            case "INT32" -> {
                return "iload " + reg + NL;
            }
            case "BOOLEAN" -> {
                return "iload " + reg + NL;
            }
            default -> {
                return "aload " + reg + NL;
            }
        }
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        switch (binaryOp.getOperation().getOpType()) {
            case ADD -> code.append("iadd").append(NL);
            case SUB -> code.append("isub").append(NL);
            case MUL -> code.append("imul").append(NL);
            case DIV -> code.append("idiv").append(NL);
            case XOR -> code.append("ixor").append(NL);
            case AND, ANDB -> code.append("iand").append(NL);
            case OR, ORB -> code.append("ior").append(NL);
            case NOTB, NOT -> code.append("iconst_1\nixor").append(NL);
            case EQ -> code.append("if_icmpeq ").append(NL);
            case NEQ -> code.append("if_icmpne ").append(NL);
            case SHR -> code.append("ishr").append(NL);
            case SHL -> code.append("ishl").append(NL);
            case SHRR -> code.append("iushr").append(NL);
            case LTH -> code.append("isub\niflt ");
            case GTH -> code.append("if_icmpgt ").append(NL);
            case LTE -> code.append("if_icmplt ");
            case GTE -> code.append("if_icmpgt ");
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        }

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

    private String generateOpCondition(OpCondInstruction opCondInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(opCondInstruction.getCondition()));

        String label = opCondInstruction.getLabel();

        code.append(label).append(NL);
        //var ops = opCondInstruction.getCondition().getOperands();

        //for (var op : ops) {
        //    code.append(generators.apply(op));
        //}

        return code.toString();
    }

    private  String generateGoto(GotoInstruction gotoInstruction){
        var code = new StringBuilder();
        String label = gotoInstruction.getLabel();
        code.append("goto "+label).append(NL);
        return code.toString();
    }

    private String generateSingleOpCondition(SingleOpCondInstruction singleOpCondInstruction) {
        var code = new StringBuilder();

        code.append(generators.apply(singleOpCondInstruction.getOperands().get(0)));

        String label = singleOpCondInstruction.getLabel();

        code.append("ifne ").append(singleOpCondInstruction.getLabel()).append(NL);

        return code.toString();

    }

    private String generateUnaryOpInstruction(UnaryOpInstruction unaryOpInstruction) {
        var code = new StringBuilder();

        code.append(generators.apply(unaryOpInstruction.getOperand()));

        switch (unaryOpInstruction.getOperation().getOpType()) {
            case  NOT-> code.append("ineg").append(NL);
            case NOTB -> code.append("iconst_1\nixor").append(NL);
            default -> throw new NotImplementedException(unaryOpInstruction.getOperation().getOpType());
        }
        return code.toString();
    }

}
