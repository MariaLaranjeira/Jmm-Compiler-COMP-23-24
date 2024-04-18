/**
 * Copyright 2022 SPeCS.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

package pt.up.fe.comp.cp1;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;

public class GrammarTest {


    // TODO: Set name of imports grammar rule
    private static final String IMPORT = "importDecl";
    // TODO: Set name of main method grammar rule
    private static final String MAIN_METHOD = "program";
    private static final String INSTANCE_METHOD = "methodDecl";
    private static final String STATEMENT = "stmt";
    private static final String EXPRESSION = "expr";

    @Test
    public void testImportSingle() {
        TestUtils.parseVerbose("import bar;", IMPORT);
    }

    @Test
    public void testImportMulti() {
        TestUtils.parseVerbose("import bar.foo.a;", IMPORT);
    }

    @Test
    public void testClass() {
        TestUtils.parseVerbose("class Foo extends Bar {}");
    }

    @Test
    public void testVarDecls() {
        TestUtils.parseVerbose("class Foo {int a; int[] b; int c; boolean d; Bar e;}");
    }

   /*@Test
    public void testVarDeclString() {
        TestUtils.parseVerbose("String aString;", "VarDecl");
    }*/

    //not
    @Test
    public void testMainMethodEmpty() {
        TestUtils.parseVerbose("static void main(String[] args) {}",INSTANCE_METHOD);
    }

    @Test
    public void testSimpleJavaProgram_1_4() {
        TestUtils.parseVerbose("import io;\n" +
                        "class Factorial {\n" +
                        "public int computeFactorial(int num){\n" +
                        "int num_aux ;\n" +
                        "if (num < 1)\n" +
                        "num_aux = 1;\n" +
                        "else\n" +
                        "num_aux = num * (this.computeFactorial(num-1));\n" +
                        "return num_aux;\n" +
                        "}\n" +
                        "public static void main(String[] args){\n" +
                        "io.println(new Factorial().computeFactorial(10)); //assuming the existence\n" +
                        "// of the classfile io.class\n" +
                        "}\n" +
                        "}",
                MAIN_METHOD);
    }

    @Test
    public void testSimpleJavaProgram_ollirBasic() {
        String code =
                "import io;\n" +
                        "import Quicksort;\n" +
                        "\n" +
                        "class CompileBasic extends Quicksort {\n" +
                        "\n" +
                        "    int intField;\n" +
                        "    boolean boolField;\n" +
                        "\n" +
                        "    public int method1() {\n" +
                        "        int intLocal1;\n" +
                        "        boolean boolLocal1;\n" +
                        "\n" +
                        "        return 0;\n" +
                        "    }\n" +
                        "\n" +
                        "    public boolean method2(int intParam1, boolean boolParam1) {\n" +
                        "        return boolParam1;\n" +
                        "    }\n" +
                        "\n" +
                        "    public static void main(String[] args) {\n" +
                        "    }\n" +
                        "}";
        TestUtils.parseVerbose(code, MAIN_METHOD);
    }

    @Test
    public void testSimpleJavaProgram_ollirAssignment() {
        String code =
                "import io;\n" +
                        "import Quicksort;\n" +
                        "\n" +
                        "class CompileAssignment {\n" +  // Class declaration start
                        "\n" +
                        "    public static void main(String[] args) {\n" +  // Main method start
                        "    }\n" +  // Main method end
                        "\n" +
                        "    public int foo() {\n" +  // Method start
                        "        int a;\n" +  // Local variable declaration
                        "        int b;\n" +  // Local variable declaration
                        "\n" +
                        "        b = 2;\n" +  // Assignment statement
                        "\n" +
                        "        a = b;\n" +  // Assignment statement
                        "\n" +
                        "        return 0;\n" +  // Return statement
                        "    }\n" +  // Method end
                        "}";  // Class declaration end
        TestUtils.parseVerbose(code, MAIN_METHOD);
    }

    @Test
    public void testSimpleJavaProgram_ollirArithmetic() {
        String code =
                "class CompileArithmetic {\n" + // Class declaration start
                        "\n" +
                        "    public static void main(String[] args) {\n" + // Main method start
                        "    }\n" + // Main method end
                        "\n" +
                        "    public int foo() {\n" + // Method start
                        "        int a;\n" + // Local variable declaration
                        "        int b;\n" + // Local variable declaration
                        "\n" +
                        "        a = 1;\n" + // Assignment statement
                        "        b = 2;\n" + // Assignment statement
                        "\n" +
                        "        return a + b;\n" + // Return statement with arithmetic expression
                        "    }\n" + // Method end
                        "}\n"; // Class declaration end

        TestUtils.parseVerbose(code, MAIN_METHOD);
    }

    @Test
    public void testSimpleJavaProgram_ollirMethodInvocation() {
        String code =
                "import io;\n" + // Import statement
                        "\n" +
                        "class CompileMethodInvocation {\n" + // Class declaration start
                        "\n" +
                        "    public static void main(String[] args) {\n" + // Main method start
                        "    }\n" + // Main method end
                        "\n" +
                        "    public int foo() {\n" + // Method start
                        "        int a;\n" + // Local variable declaration
                        "\n" +
                        "        a = 1;\n" + // Assignment statement
                        "\n" +
                        "        io.println(a);\n" + // Method invocation statement
                        "\n" +
                        "        return 0;\n" + // Return statement
                        "    }\n" + // Method end
                        "}\n"; // Class declaration end


        TestUtils.parseVerbose(code, MAIN_METHOD);
    }


    @Test
    public void testInstanceMethodEmpty() {
        TestUtils.parseVerbose("int foo(int anInt, int[] anArray, boolean aBool, String aString) {return a;}",
                INSTANCE_METHOD);
    }



    @Test
    public void testInstanceMethodVarargs() {
        TestUtils.parseVerbose("int foo(int... ints) {return 0;}",
                INSTANCE_METHOD);
    }

    @Test
    public void testStmtScope() {
        TestUtils.parseVerbose("{a; b; c;}", STATEMENT);
    }

    @Test
    public void testStmtEmptyScope() {
        TestUtils.parseVerbose("{}", STATEMENT);
    }

    @Test
    public void testStmtIfElse() {
        TestUtils.parseVerbose("if(a){ifStmt1;ifStmt2;}else{elseStmt1;elseStmt2;}", STATEMENT);
    }

    @Test
    public void testStmtIfElseWithoutBrackets() {
        TestUtils.parseVerbose("if(a)ifStmt;else elseStmt;", STATEMENT);
    }

    @Test
    public void testStmtWhile() {
        TestUtils.parseVerbose("while(a){whileStmt1;whileStmt2;}", STATEMENT);
    }

    @Test
    public void testStmtWhileWithoutBrackets() {
        TestUtils.parseVerbose("while(a)whileStmt1;", STATEMENT);
    }

    @Test
    public void testStmtAssign() {
        TestUtils.parseVerbose("a=b;", STATEMENT);
    }

    @Test
    public void testStmtArrayAssign() {
        TestUtils.parseVerbose("anArray[a]=b;", STATEMENT);
    }

    @Test
    public void testExprTrue() {
        TestUtils.parseVerbose("true", EXPRESSION);
    }

    @Test
    public void testExprFalse() {
        TestUtils.parseVerbose("false", EXPRESSION);
    }

    @Test
    public void testExprThis() {
        TestUtils.parseVerbose("this", EXPRESSION);
    }

    @Test
    public void testExprId() {
        TestUtils.parseVerbose("a", EXPRESSION);
    }

    @Test
    public void testExprIntLiteral() {
        TestUtils.parseVerbose("10", EXPRESSION);
    }

    @Test
    public void testExprParen() {
        TestUtils.parseVerbose("(10)", EXPRESSION);
    }

    @Test
    public void testExprMemberCall() {
        TestUtils.parseVerbose("foo.bar(10, a, true)", EXPRESSION);
    }

    @Test
    public void testExprMemberCallChain() {
        TestUtils.parseVerbose("callee.level1().level2(false, 10).level3(true)", EXPRESSION);
    }

    @Test
    public void testExprLength() {
        TestUtils.parseVerbose("a.length", EXPRESSION);
    }

    @Test
    public void testExprLengthChain() {
        TestUtils.parseVerbose("a.length.length", EXPRESSION);
    }

    @Test
    public void testArrayAccess() {
        TestUtils.parseVerbose("a[10]", EXPRESSION);
    }

    @Test
    public void testArrayAccessChain() {
        TestUtils.parseVerbose("a[10][20]", EXPRESSION);
    }

    @Test
    public void testParenArrayChain() {
        TestUtils.parseVerbose("(a)[10]", EXPRESSION);
    }

    @Test
    public void testCallArrayAccessLengthChain() {
        TestUtils.parseVerbose("callee.foo()[10].length", EXPRESSION);
    }

    @Test
    public void testExprNot() {
        TestUtils.parseVerbose("!true", EXPRESSION);
    }

    @Test
    public void testExprNewArray() {
        TestUtils.parseVerbose("new int[!a]", EXPRESSION);
    }

    @Test
    public void testExprNewClass() {
        TestUtils.parseVerbose("new Foo()", EXPRESSION);
    }

    @Test
    public void testExprMult() {
        TestUtils.parseVerbose("2 * 3", EXPRESSION);
    }

    @Test
    public void testExprDiv() {
        TestUtils.parseVerbose("2 / 3", EXPRESSION);
    }

    @Test
    public void testExprMultChain() {
        TestUtils.parseVerbose("1 * 2 / 3 * 4", EXPRESSION);
    }

    @Test
    public void testExprAdd() {
        TestUtils.parseVerbose("2 + 3", EXPRESSION);
    }

    @Test
    public void testExprSub() {
        TestUtils.parseVerbose("2 - 3", EXPRESSION);
    }

    @Test
    public void testExprAddChain() {
        TestUtils.parseVerbose("1 + 2 - 3 + 4", EXPRESSION);
    }

    @Test
    public void testExprRelational() {
        TestUtils.parseVerbose("1 < 2", EXPRESSION);
    }

    @Test
    public void testExprRelationalChain() {
        TestUtils.parseVerbose("1 < 2 < 3 < 4", EXPRESSION);
    }

    @Test
    public void testExprLogical() {
        TestUtils.parseVerbose("1 && 2", EXPRESSION);
    }

    @Test
    public void testExprLogicalChain() {
        TestUtils.parseVerbose("1 && 2 && 3 && 4", EXPRESSION);
    }

    @Test
    public void testExprChain() {
        TestUtils.parseVerbose("1 && 2 < 3 + 4 - 5 * 6 / 7", EXPRESSION);
    }

    @Test
    public void testExprArrayInit() {
        TestUtils.parseVerbose("[10, 20, 30]", EXPRESSION);
    }

}
