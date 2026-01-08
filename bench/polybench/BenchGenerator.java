/*
 * Created on Wed Sept 23 2022
 *
 * The MIT License (MIT) Copyright (c) 2022 Peterson Yuhala, Institut d'Informatique Université de
 * Neuchâtel (IIUN)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package polybench;



// jdk
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.*;



/**
 * The purpose of this tool is to generate files to be instrumented by the taint analysis tool. The
 * program file generated is either in JS or Python and comprises n functions which will either be
 * trusted or not. We adopt a simple/naive approach where these functions are standalone and do not
 * interact with the other functions.
 */
public class BenchGenerator {

    public enum Guest {
        JS, RUBY, PYTHON, UNDEFINED
    };

    public enum BenchType {
        IO, CPU
    };

    public enum VarType {
        INT, DOUBLE, ARRAY
    };


    private Guest guest;
    private int totalFunctions;
    private int untrustedPercent;
    private BenchType type;

    private static final String benchDir = "/home/ubuntu/truffle-dev/generic-tools/polytaint/bench";


    private String ioTemplate = benchDir + "/templates/io";
    private String cpuTemplate = benchDir + "/templates/cpu";
    private String resultsFile;

    /*
     * Dummy statement to be included in JS functions to force them to be trusted.
     */
    private static String secLJs = "\tvar my_int = Polyglot.eval(\"secL\", \"sInt(123)\");";
    /*
     * Dummy statement to be included in JS functions to force them to be trusted.
     */
    private static String secLPy =
            "\tmy_int = polyglot.eval(language=\"secL\", string=\"sInt(123)\")";


    // private static String dummyInt = "var my_int = 123;";

    private static String dummyInt = "my_int = 123;";

    private static boolean templateSet = false;


    public BenchGenerator(Guest guestLang, int numFunctions, int untrustedPercent, BenchType type) {

        this.guest = guestLang;
        this.totalFunctions = numFunctions;
        this.untrustedPercent = untrustedPercent;

        this.type = type;


        if (guestLang == Guest.JS) {
            this.ioTemplate += ".js";
            this.cpuTemplate += ".js";
            this.resultsFile = benchDir + "/bench-" + untrustedPercent + ".js";
        } else if (guestLang == Guest.PYTHON) {
            this.ioTemplate += ".py";
            this.cpuTemplate += ".py";
            this.resultsFile = benchDir + "/bench-" + untrustedPercent + ".py";
        }



    }

    /**
     * Generates two program files: 1 with a function defining num secure types and another with the
     * same function defining only num regular types.
     * 
     * @param type
     * @param num
     */
    public void generateTypeBenchJs(VarType type, int num) {
        System.out.println(">>>>>>>>>>>>>> Generating synthetic bench defining :" + num + " types");
        String secl_types = benchDir + "/bench-secl-" + num + ".js";
        String reg_types = benchDir + "/bench-reg-" + num + ".js";

        String typeSecl = "";
        String typeReg = "";
        switch (type) {
            case INT:
                typeSecl = "Polyglot.eval(\"secL\", \"sInt(1)\");\n";
                typeReg = "1;\n";
                break;

            case DOUBLE:
                typeSecl = "Polyglot.eval(\"secL\", \"sInt(1.0)\");\n";
                typeReg = "1.0;\n";
                break;

            case ARRAY:
                typeSecl = "sArray";
                typeReg = "[]";
                break;
            default:
                break;
        }
        // right hand side of variable declaration


        String code = "";
        // define function
        code += "function var_function(){\n";
        for (int i = 0; i < num; i++) {
            code += "var var_" + i + " = " + typeSecl;
            // code += "print(var_" + i + ");";
        }
        code += "}\n";
        code += "var_function();";

        // write secl bench file
        writeFile(secl_types, code);

        code = "";

        // define function
        code += "function var_function(){\n";
        for (int i = 0; i < num; i++) {
            code += "var var_" + i + " = " + typeReg;
            // code += "print(var_" + i + ");";
        }
        code += "}\n";
        code += "var_function();";

        // write secl bench file
        writeFile(reg_types, code);


        System.out.println(">>>>>>>>>>>>>> Generation end >>>>>>>>>>>>>>>>>>>>");
    }

    /**
     * Generates two program files: 1 with a function defining num secure types and another with the
     * same function defining only num regular types.
     * 
     * @param type
     * @param num
     */
    public void generateTypeBenchPy(VarType type, int num) {
        System.out.println(">>>>>>>>>>>>>> Generating synthetic bench defining :" + num + " types");
        String secl_types = benchDir + "/bench-secl-" + num + ".py";
        String reg_types = benchDir + "/bench-reg-" + num + ".py";

        String typeSecl = "";
        String typeReg = "";
        switch (type) {
            case INT:
                typeSecl = "polyglot.eval(language=\"secL\", string=\"sInt(1)\")\n";
                typeReg = "1\n";
                break;

            case DOUBLE:
                typeSecl = "polyglot.eval(language=\"secL\", string=\"sDouble(1.0)\")\n";
                typeReg = "1.0\n";
                break;

            case ARRAY:
                typeSecl = "sArray";
                typeReg = "[]";
                break;
            default:
                break;
        }
        // right hand side of variable declaration


        String code = "import polyglot\n";
        // define function
        code += "def var_function():\n";
        for (int i = 0; i < num; i++) {
            code += "\tvar_" + i + " = " + typeSecl;
            // code += "print(var_" + i + ");";
        }

        code += "var_function()\n";

        // write secl bench file
        writeFile(secl_types, code);

        code = "";

        // define function
        code += "def var_function():\n";
        for (int i = 0; i < num; i++) {
            code += "\tvar_" + i + " = " + typeReg;
            // code += "print(var_" + i + ");";
        }

        code += "var_function()";

        // write secl bench file
        writeFile(reg_types, code);


        System.out.println(">>>>>>>>>>>>>> Generation end >>>>>>>>>>>>>>>>>>>>");
    }



    public void generateBench() {
        switch (this.guest) {
            case JS:
                // buildJSProgram();
                break;
            case PYTHON:
                // buildPythonProgram();
                break;
            default:
                break;
        }
    }


    /**
     * The template file contain some initial source code which will be written in the generated
     * files.
     */
    public void buildJSProgram(int untrustedPercent) {

        int numUntrusted = (untrustedPercent * this.totalFunctions) / 100;
        int numTrusted = this.totalFunctions - numUntrusted;

        System.out.println(">>>>>>>>>>>>>> Generating synthetic bench: num untrusted functions: "
                + numUntrusted);

        String template = "";
        this.resultsFile = benchDir + "/bench-" + numUntrusted + ".js";

        if (this.type == BenchType.IO) {
            template = this.ioTemplate;
        } else {
            template = this.cpuTemplate;
        }
        String source = "";
        String dummyInt = "var my_int = 123;";
        // Read template file line by line and build resulting string with code
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(template));
            String line = reader.readLine();
            while (line != null) {
                source += line + "\n";
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String printLines = String.valueOf(source);

        String code = "";
        // code += printLines;

        // Add the required number of trusted or untrusted functions to results file
        for (int i = 0; i < numUntrusted; i++) {
            code += "function untrusted_func_" + i + "() {\n";
            // // code += secLJs + "\n";
            code += printLines;
            code += "\n";
            code += "}\n";

        }



        // Add the required number of trusted or untrusted functions to results file
        for (int i = 0; i < numTrusted; i++) {
            code += "function trusted_func_" + i + "() {\n";
            code += secLJs + "\n";
            code += printLines;
            code += "\n";
            code += "}\n";

        }

        // invoke all the functions

        for (int i = 0; i < numUntrusted; i++) {
            code += "untrusted_func_" + i + "();\n";
        }
        for (int i = 0; i < numTrusted; i++) {
            code += "trusted_func_" + i + "();\n";
        }



        // for (int i = 0; i < numUntrusted; i++) {
        // code += "function untrusted_func_" + i + "(){\n";
        // code += dummyInt + "\n";
        // code += printLines;
        // code += "}\n";
        // }

        // Bundle up the different functions trusted or untrusted groups.
        // code += "function run_trusted (){\n";
        // code += secLJs + "\n";
        // for (int i = 0; i < numFuncs; i++) {

        // code += "trusted_func_" + i + "();\n";

        // }
        // code += "}\n";

        // code += "function run_untrusted (){\n";
        // code += dummyInt + "\n";
        // for (int i = 0; i < numUntrusted; i++) {

        // code += "untrusted_func_" + i + "();\n";

        // }
        // code += "}\n";

        // run untrusted and trusted blocks
        // code += "run_untrusted();\n";
        // code += "run_trusted();\n";

        // write code in results file.



        writeFile(this.resultsFile, code);

        // copy results to bench output folder
        // try {
        // copyFile(resultsFileJs, benchOutputDir);
        // } catch (Exception e) {
        // // TODO: handle exception
        // }
        System.out.println(">>>>>>>>>>>>>>> Synthetic bench generation complete >>>>>>>>>>>>>>>>");


    }

    public void buildPythonProgram(int numFunc) {
        // int numUntrusted = (this.untrustedPercent * this.totalFunctions) / 100;
        // int numTrusted = this.totalFunctions - numUntrusted;


        System.out.println(">>>>>>>>>>>>>> Generating synthetic bench: num functions: " + numFunc);

        String template = "";

        if (this.type == BenchType.IO) {
            template = this.ioTemplate;
        } else {
            template = this.cpuTemplate;
        }

        String source = "";
        this.resultsFile = benchDir + "/bench-" + numFunc + ".py";

        String dummyInt = "\tmy_int = 123";
        // Read template file line by line and build resulting string with code
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(template));
            String line = reader.readLine();
            while (line != null) {
                source += line + "\n";
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String printLines = String.valueOf(source);

        // Add import statements at the top
        String code = "";
        code += "import random\n\n";
        code += "import polyglot\n";

        // code += printLines;

        for (int i = 0; i <= numFunc; i++) {
            code += "def func_" + i + "():\n";
            // code += secLPy + "\n";
            code += printLines;
            code += "\n";

        }

        // invoke all functions
        for (int i = 0; i < numFunc; i++) {

            code += "func_" + i + "()\n";

        }

        // Add the required number of trusted or untrusted functions to results file
        // for (int i = 0; i < numTrusted; i++) {
        // code += "def trusted_func_" + i + "():\n";
        // code += secLPy + "\n";
        // code += printLines;


        // }
        // for (int i = 0; i < numUntrusted; i++) {
        // code += "def untrusted_func_" + i + "():\n";
        // code += dummyInt + "\n";
        // code += printLines;

        // }

        // Bundle up the different functions trusted or untrusted groups.
        // code += "function run_trusted (){\n";
        // code += secLJs + "\n";
        // for (int i = 0; i < numTrusted; i++) {

        // code += "trusted_func_" + i + "()\n";

        // }
        // // code += "}\n";

        // // code += "function run_untrusted (){\n";
        // // code += dummyInt + "\n";
        // for (int i = 0; i < numUntrusted; i++) {

        // code += "untrusted_func_" + i + "()\n";

        // }
        // code += "}\n";

        // run untrusted and trusted blocks
        // code += "run_untrusted();\n";
        // code += "run_trusted();\n";

        // write code in results file.

        writeFile(this.resultsFile, code);

        // copy results to bench output folder
        // try {
        // copyFile(resultsFileJs, benchOutputDir);
        // } catch (Exception e) {
        // // TODO: handle exception
        // }
        System.out.println(">>>>>>>>>>>>>>> Synthetic bench generation complete >>>>>>>>>>>>>>>>");
    }

    /**
     * Writes code to file
     * 
     * @param file
     * @param code
     */
    static void writeFile(String file, String code) {

        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(code);
            fileWriter.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Copies a file src to destination dest
     * 
     * @param src
     * @param dest
     * @throws IOException
     */
    public static void copyFile(String src, String dest) throws IOException {
        Path from = Paths.get(src);
        Path to = Paths.get(dest);
        Files.copy(from, to, REPLACE_EXISTING);

        // www.java67.com/2016/09/how-to-copy-file-from-one-location-to-another-in-java.html#ixzz7gpnbPQ2D
    }


}

