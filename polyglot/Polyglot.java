/*
 * Created on Tue Mar 01 2022
 *
 * The MIT License (MIT) Copyright (c) 2022 Peterson Yuhala
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

package poly;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.*;

public class Polyglot {

    public static Context ctx = Context.create();

    public static void main(String[] args) {
        System.out.println("Hello Java!");
        // context without try block
        testJsFunction();

        contextWithTry();

    }

    static void contextWithTry() {
        try (Context context = Context.create()) {
            System.out.println("About to run js context");
            context.eval("js", "print('Hello JavaScript!');");

        }

    }

    /**
     * Exporting js function/method as Java method.
     */
    public static void testJsFunction() {

        Context context = Context.create();

        Source src_multi = Source.newBuilder("js", "function multi(a, b) {return a * b;}", "mul.js")
                .buildLiteral();

        Value js_multi = context.eval(src_multi);


        Value res = js_multi.execute(6, 7);
        System.out.println("Result is: " + res);


    }

    public static void helloJs() {
        // run javascript code
        ctx.eval("js", "print('Hello javascript!');");
        Value array = ctx.eval("js", "[1,2,42,4]");

        int result = array.getArrayElement(2).asInt();
        System.out.println("Result is: " + result);

        int myInt = ctx.eval("js", "2").asInt();
        System.out.println("My int in JS is: " + myInt);

    }

    public static void testSl() {
        // run simple language code
        ctx.eval("secureL", "function main() { println(\"Hello secure language!\");}");
        ctx.eval("secureL", "function main() { println(10/3);}");

        int secureInt = ctx.eval("secureL", "function main() {return 50;}").asInt();
        System.out.println("My int in SL is: " + secureInt);
    }

}

/*
 * #include <stdio.h> #include <graalvm/llvm/polyglot.h>
 * 
 * int main() { void *array = polyglot_eval("js", "[1,2,42,4]"); int element =
 * polyglot_as_i32(polyglot_get_array_element(array, 2)); printf("%d\n", element); return element; }
 */
