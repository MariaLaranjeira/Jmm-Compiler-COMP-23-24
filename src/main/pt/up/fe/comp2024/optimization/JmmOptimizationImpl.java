package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;


import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        System.out.println("This is the ollir code: \n" + ollirCode + "\n End code!");

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        return ollirResult;
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        if (semanticsResult.getConfig().containsKey("optimize") && semanticsResult.getConfig().get("optimize").equals("true")) {
            System.out.println("\nEntered Constants optimizations\n");

            ConstantPropagationVisitor constantPropagationVisitor = new ConstantPropagationVisitor((SymbolTable) semanticsResult.getSymbolTable());
            ConstantFoldingVisitor constantFoldingVisitor = new ConstantFoldingVisitor();

            int props, folds;

            do {
                constantPropagationVisitor.resetCounter();
                constantFoldingVisitor.resetCounter();

                constantPropagationVisitor.visit(semanticsResult.getRootNode());
                constantFoldingVisitor.visit(semanticsResult.getRootNode());

                props = constantPropagationVisitor.getCounter();
                folds = constantFoldingVisitor.getCounter();
            } while (props != 0 || folds != 0);

        }

        return semanticsResult;
    }
}
