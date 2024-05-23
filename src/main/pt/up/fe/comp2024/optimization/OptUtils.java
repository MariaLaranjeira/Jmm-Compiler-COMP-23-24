package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {
        tempNumber += 1;
        return tempNumber;
    }

    public static String getCurrentTemp(){
        return "tmp" + tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {
        TYPE.checkOrThrow(typeNode);

        String typeName;
        if(typeNode.getKind().equals("ArrayType")){
            typeName = typeNode.getChildren().get(0).get("value");
            return toOllirTypeArray(typeName);
        }

        typeName = typeNode.get("value");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        if(type == null) return ".V";
        if (type.isArray())  return toOllirTypeArray(type.getName());
        return toOllirType(type.getName());
    }

    public static String toOllirType(String typeName) {
        return "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void", "static void" -> "V";
            case "String" -> "String";
            default -> typeName;
        };
    }

    public static String toOllirTypeArray(String typeName) {
        return ".array." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void", "static void" -> "V";
            case "String" -> "String";
            default -> typeName;
        };
    }


}
