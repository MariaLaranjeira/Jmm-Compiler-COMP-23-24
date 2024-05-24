package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {
    public static JmmSymbolTable build(JmmNode root) {
        var classDecl = root.getChildren(Kind.CLASS_DECL).get(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);

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
        var imports = buildImports(root);

        return new JmmSymbolTable(className,supers, imports, fields, methods, returnTypes, params, locals);
    }

    private static List<String> buildImports(JmmNode jmmNode) {
        var allImports = jmmNode.getChildren("ImportStmt");
        List<String> importsHelper = new ArrayList<>();

        for(var imp : allImports) {
            String imports = imp.get("value").substring(1, imp.get("value").length() - 1);
            String completeImport = String.join(".", imports.split(", "));

            importsHelper.add(completeImport);
        }

        return importsHelper;
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        var result = new HashMap<String, Type>();
        var methods = classDecl.getChildren(METHOD_DECL);
        for (var method : methods) {

            // If method is main
            if (!method.getAttributes().contains("name")){
                result.put("main", new Type("static void", false));
                continue;
            }

            result.put(method.get("name"), filterType(method, false));

        }
        return result;

    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        var methods = classDecl.getChildren(METHOD_DECL);
        var result = new HashMap<String, List<Symbol>>();

        for (var method : methods) {

            // If method is main
            if (!method.getAttributes().contains("name")) {
                List<Symbol> mainSymbols = new ArrayList<>();
                mainSymbols.add(new Symbol(new Type("String",true),method.get("args")));
                result.put("main", mainSymbols);

            }
            else {
                var params = method.getChildren("Params").get(0).getChildren();
                var name = method.get("name");

                // If method has no parameters
                if(params.isEmpty()) {
                    result.put(name, Collections.emptyList());
                }
                else {
                    List<Symbol> symbols = new ArrayList<>();
                    for (var param : params) {
                        boolean isVarargs = "VarargsParam".equals(param.getKind());
                        Type typeName = filterType(param, isVarargs);
                        String paramName = param.get("name");

                        symbols.add(new Symbol(typeName, paramName));
                    }
                    result.put(name, symbols);
                }
            }


        }
        return result;

    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var methods = classDecl.getChildren(METHOD_DECL);
        var result = new HashMap<String, List<Symbol>>();
        for (var method : methods) {
            var locals = getLocalsList(method);

            if (method.getAttributes().contains("name")) {
                result.put(method.get("name"), locals);
            }
            else {
                // If method is main
                result.put("main", locals);
            }

        }

        return result;
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {

        var vars = classDecl.getChildren(VAR_DECL);

        List<Symbol> symbols = new ArrayList<>();
        for (var field : vars) {
            symbols.add(new Symbol(filterType(field, false), field.get("name")));
        }

        return symbols;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        var methods = classDecl.getChildren(METHOD_DECL);
        var result = new ArrayList<String>();

        for (var method : methods) {
            if (method.getAttributes().contains("name")) {
                result.add(method.get("name"));
            }
            else {
                // If method is main
                result.add("main");
            }
        }
        return result;
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {

        var locals = methodDecl.getChildren(VAR_DECL);

        // If method has no local variables
        if (locals.isEmpty()) {
            return Collections.emptyList();
        }

        List<Symbol> symbols = new ArrayList<>();
        for (var local : locals) {
            symbols.add(new Symbol(filterType(local, false), local.get("name")));
        }

        return symbols;

    }

    private static Type filterType(JmmNode node, boolean isVarargs) {
        var aux = node.getChildren().get(0);

        if(isVarargs){
            Type type = new Type(aux.get("value"), true);
            type.putObject("isVararg", true);
            return type;
        }
        else if (aux.getKind().equals("ArrayType")) {
            return new Type(aux.getChildren().get(0).get("value"), true);
        }
        else {
            return new Type(aux.get("value"), false);
        }
    }
}
