
/*
 * Created on Wed Mar 31 2022
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

package com.oracle.truffle.polyt;

/**
 * This class defines a few useful string contants and regexes used as hints by the instrumentation
 * tool when analysing source sections. This strategy is not the smartest way of detecting these
 * code fragments, but is rather a shortcut
 * 
 * 
 */
public final class SourceFragmentConsts {

    public enum SourceInfoID {
        FUNC_PARAMS, FUNC_NAME, VAR_WRITE_OPERANDS, FUNC_CALL
    }

    public static final String SECUREL_ID = "secureL";

    public static final String SECL_ID = "secL";

    public static final String JS_POLY_EVAL_SECUREL = "Polyglot.eval(\"secureL\"";

    public static final String JS_POLY_EVAL_SECL = "Polyglot.eval(\"secL\"";

    public static final String PYTHON_POLY_EVAL_SECL = "polyglot.eval(language=\"secL\"";

    public static final String VAR_WRITE_SPLIT_REGEX = "[=|+|(|)|\\,|\\-|/|*|\\||]";

    // ([^,]+\(.+?\))|([^,]+)
    public static final String FUNC_CALL_SPLIT_REGEX = "[(|)|\\,]";

    public static final String FUNC_PARAMS_SPLIT_REGEX = "([^,]+\\(.+?\\))|([^,]+)";

    public static final String FUNC_NAME_SPLIT_REGEX = "\\b[^()]+\\((.*)\\)$";

    public static final String JS_BUILTIN_TAG = "(intermediate value)";

    /**
     * It is very difficult to parse out python function definitions with regular expressions. So we
     * use this "magic value" after every python function definition so our partitioner knows how to
     * extract the function definition.
     */
    public static final String PYTHON_FUNC_END = "func_end=1";



    public static final String[] pyBuiltIns = {"import", "from", "range", "randint",
            "random.randint", "len", "print", "eval(", "importlib", "object()", "builtins",
            "<module 'builtins'>", ".<module", "Codec", "IncrementalEncoder", "IncrementalDecoder",
            "BufferedIncrementalDecoder", "StreamReader", "StreamWriter", "_buffer_decode",
            "TruffleCodec", "ApplyEncoding", "encoding", "errors", "fn"};

    public static final String[] jsBuiltIns = {"eval", "range", "rand", "len", "parseInt",
            "parseFloat", "escape", "unescape", "console.log", "print", "<eval>", "<builtin>"};

    private SourceFragmentConsts() {
        // this prevents even the native class from
        // calling this ctor as well :
        throw new AssertionError();
    }
}
