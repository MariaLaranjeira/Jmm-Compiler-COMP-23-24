package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var classDecl = root.getChildren(Kind.CLASS_DECL).get(0);

        //SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        String supers = null;

        if(classDecl.getAttributes().contains("extend")){
            supers = classDecl.get("extend");
        }

        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        var imports = root.getChildren("ImportStmt").stream()
                .map(importDecl -> importDecl.get("value"))
                .toList();

        return new JmmSymbolTable(className,supers, imports, fields, methods, returnTypes, params, locals);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {

        var result = new HashMap<String, Type>();
        var methods = classDecl.getChildren(METHOD_DECL);
        for (var method : methods) {
            var returnType = method.getChildren().get(0).get("value");
            result.put(method.get("name"), new Type(returnType, false));
        }
        return result;

    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {

        var methods = classDecl.getChildren(METHOD_DECL);
        var result = new HashMap<String, List<Symbol>>();

        for (var method : methods) {
            var params = method.getChildren(PARAM);
            var name = method.get("name");
            if(params.isEmpty()) {
                result.put(name, Collections.emptyList());
            }
            else {
                List<Symbol> symbols = new ArrayList<>();
                for (var param : params) {
                    symbols.add(new Symbol(new Type(param.getChildren().get(0).get("value"),false), param.get("name")));
                }
                result.put(name, symbols);
            }
        }
        return result;

    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var methods = classDecl.getChildren(METHOD_DECL);
        var result = new HashMap<String, List<Symbol>>();
        for (var method : methods) {
            var locals = getLocalsList(method);
            var name = method.get("name");
            result.put(name, locals);
        }

        return result;
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {

        var vars = classDecl.getChildren(VAR_DECL);

        List<Symbol> symbols = new ArrayList<>();
        for (var field : vars) {
            symbols.add(new Symbol(new Type(field.getChildren().get(0).get("value"),false), field.get("name")));
        }

        return symbols;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                 .filter(method -> method.getAttributes().contains("name")) // out methods without a name
                 .map(method -> method.get("name"))
                 .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {

        var locals = methodDecl.getChildren(VAR_DECL);

        if (locals.isEmpty()) {
            return Collections.emptyList();
        }

        List<Symbol> symbols = new ArrayList<>();
        for (var local : locals) {
            symbols.add(new Symbol(new Type(local.getChildren().get(0).get("value"),false), local.get("name")));
        }

        return symbols;

    }
}
