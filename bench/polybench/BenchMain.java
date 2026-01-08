
/*
 * Created on Wed Oct 6 2022
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

import polybench.BenchGenerator.BenchType;
import polybench.BenchGenerator.Guest;
import polybench.BenchGenerator.VarType;



public class BenchMain {


    public static int numFunctions = 20;
    public static Guest guest = Guest.JS;

    public static void main(String[] args) {

        BenchGenerator benchGenJs = new BenchGenerator(Guest.JS, numFunctions, 0, BenchType.CPU);
        BenchGenerator benchGenPy =
                new BenchGenerator(Guest.PYTHON, numFunctions, 0, BenchType.CPU);


        // Runtime benchmark
        for (int i = 10; i <= 100; i += 10) {

            // generateBenchFile(i, BenchType.IO);
            // generateBenchFile(i, BenchType.CPU);
            benchGenJs.buildJSProgram(i);
            // benchGenPy.buildPythonProgram(i);

        }

        // Types benchmark
        for (int i = 1000; i <= 10000; i += 1000) {
            // BenchGenerator benchGen = new BenchGenerator(guest, 0, 0, BenchType.IO);
            // benchGen.generateTypeBenchJs(VarType.DOUBLE, i);
            // benchGen.generateTypeBenchPy(VarType.INT, i);
        }
    }



    public static void generateBenchFile(int uPercent, BenchType type) {

        BenchGenerator benchGen = new BenchGenerator(guest, numFunctions, uPercent, type);
        benchGen.generateBench();
    }

}
