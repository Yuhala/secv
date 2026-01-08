/*
 * Created on Wed Mar 09 2022
 *
 * The MIT License (MIT) Copyright (c) 2022 Peterson Yuhala
 * 
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI) Copyright (c)
 * 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.polyt.nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import com.oracle.js.parser.Source;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.binary.InstanceofNode;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.instrumentation.JSTags;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.polyt.PolyTaintInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;


// python
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.util.PythonUtils;

// regex
import java.util.regex.Pattern;

// js
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.api.object.DynamicObject;

// polyt
import com.oracle.truffle.polyt.SourceFragmentConsts;
import com.oracle.truffle.polyt.SourceFragmentConsts.SourceInfoID;
import com.oracle.truffle.polyt.partitioner.PolyTaintFunction;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;
import com.oracle.truffle.polyt.PolyTaintInstrument.Guest;
import com.oracle.truffle.polyt.partitioner.PType;

import com.oracle.truffle.polyt.utils.Logger;
import java.util.List;

/**
 * Node that "wraps" AST nodes of interest (nodes that correspond to secureL
 * statements/expressions). This is defined by the filter given to the {@link Instrumenter} in
 * {@link PolyTaintInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) }
 * ), and informs the {@link PolyTaintInstrument} that we
 * {@link PolyTaintInstrument#addTainted(SourceSection) tainted/marked} it's
 * {@link #instrumentedSourceSection source section}.
 * 
 * 
 * 
 * The 3 main events we are concerned with for taint analysis are: variable writes from secureL
 * contexts, function calls with tainted variables, and eval calls with secureL.
 *
 */
public class PolyTaintNode extends ExecutionEventNode {

    public enum PYTHON_READ_KIND {
        PY_VAR_READ, PY_VAR_WRITE, PY_FUNC_READ;
    };

    protected PolyTaintInstrument instrument;
    // flag to determine already seen nodes
    @CompilerDirectives.CompilationFinal
    protected boolean seen;

    /**
     * Flag to determine if node return type has been registered. This is used only for function
     * root nodes.
     */
    @CompilerDirectives.CompilationFinal
    protected boolean returnRegistered;

    /**
     * Each node knows which {@link SourceSection} it instruments.
     */
    protected SourceSection instrumentedSourceSection;

    /**
     * Node being instrumented/wrapped by the instrumentation node
     */
    protected Node instrumentedNode;

    /**
     * Node names eg a function's name
     */
    protected String name;

    /**
     * Event context: gives us instrumented node, source section etc
     */
    protected EventContext context;

    /**
     * Corresponding root function for the instrumented node.
     */
    protected final String rootFuncName;

    protected final String sourceFileName;

    protected int inputCount;

    protected static String guestId;

    // we don't instrument new nodes
    protected final boolean isJsNew = false;

    // private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    protected static final InteropLibrary INTEROP =
            LibraryFactory.resolve(InteropLibrary.class).getUncached();

    // position of python user arguments in frame
    public static final int PYTHON_USER_ARGUMENTS_OFFSET = 9;

    // position of js user arguments in frame
    public static final int JS_USER_ARGUMENTS_OFFSET = 2;
    // max user arguments
    public static final int MAX_PYTHON_USER_ARGUMENTS = 255;
    // current frame index in arguments array
    public static final int PYTHON_CURRENT_FRAME_INDEX = 7;
    // caller frame index in arguments array
    public static final int PYTHON_CALLER_FRAME_INDEX = 6;

    public PolyTaintNode(PolyTaintInstrument instrument, EventContext context) {
        this.instrument = instrument;
        this.instrumentedSourceSection = context.getInstrumentedSourceSection();
        this.instrumentedNode = context.getInstrumentedNode();
        this.rootFuncName = context.getInstrumentedNode().getRootNode().getName();
        this.inputCount = this.getInputCount();
        // this.sourceFileName =
        // instrumentedNode.getEncapsulatingSourceSection().getSource().getName();
        this.sourceFileName = getPrimaryFileSimpleName();
        this.name = null;
        this.returnRegistered = false;
        guestId = PolyTaintInstrument.guestLanguageId;

    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        /**
         * PYuhala: this method is overidden in the subclasses.
         */
    }

    /**
     * Examines the input frame to get the function name.
     * 
     * @param source
     * @return
     */
    public String getFuncName(VirtualFrame frame) {
        String name = getPrimaryFileSimpleName() + ".";

        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                Object jsFunc = frame.getArguments()[1];
                name += JSFunction.getName((DynamicObject) jsFunc);

                // Logger.log("getFuncName:: funcObj getName: " + name);
                break;

            case PYTHON:
                RootNode root = instrumentedNode.getRootNode();
                if (root == null) {
                    return name;
                }
                // Logger.log("getFuncName rootnode name: " + root.getName());
                String rootName = root.getName();
                if (rootName != null) {
                    name += rootName;
                }

                break;
            case RUBY:
                break;
            case UNDEFINED:
                break;

        }
        return name;
    }

    public String getPolyTaintNodeName(String langId) {
        String nodeName = null;
        if (langId.equals("js")) {
            nodeName = (String) getAttribute("name");
        } else if (langId.equals("python")) {

            InstrumentableNode inode = (InstrumentableNode) instrumentedNode;

            // NodeObjectDescriptor descr = (NodeObjectDescriptor) inode.getNodeObject();

            try {
                if (inode.hasTag(StandardTags.WriteVariableTag.class)) {
                    nodeName = (String) INTEROP.readMember(inode.getNodeObject(),
                            StandardTags.WriteVariableTag.NAME);
                } else if (inode.hasTag(StandardTags.ReadVariableTag.class)) {
                    nodeName = (String) INTEROP.readMember(inode.getNodeObject(),
                            StandardTags.ReadVariableTag.NAME);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException ex) {

            }

        }

        return nodeName;
    }

    public EventContext getContext() {
        return context;
    }

    public String getRootFuncSimpleName() {
        return rootFuncName;
    }

    public String getRootFuncFullName() {
        return sourceFileName + "." + rootFuncName;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public Node getInstrumentedNode() {
        return instrumentedNode;
    }

    public Node getParentNode(Node node) {
        return node.getParent();
    }

    /**
     * pyuhala: Gets the types of the function arguments for the function being called. For JS
     * function calls, getArgument() returns: Arg 0: DynamicObject<undefined> Arg 1:
     * DynamicObject<JSFunction> Arg 2 ... : the real arguments passed to the function in the
     * program () NB: we have to find a way to provide generic types which will match different
     * types at runtime.
     * 
     * 
     * For PYTHON function calls, getArgument() returns:
     * 
     * Object callable = frame.getArguments()[0]; Object[] arguments = (Object[])
     * frame.getArguments()[1]; PKeyword[] keywords = (PKeyword[]) frame.getArguments()[2];
     * 
     * 
     */

    //@formatter:off
    /*** The layout of an argument array for a Python frame.
     *
     *                                         +-------------------+
     * INDEX_VARIABLE_ARGUMENTS             -> | Object[]          |
     *                                         +-------------------+
     * INDEX_KEYWORD_ARGUMENTS              -> | PKeyword[]        |
     *                                         +-------------------+
     * INDEX_GENERATOR_FRAME                -> | MaterializedFrame |
     *                                         +-------------------+
     * SPECIAL_ARGUMENT                     -> | Object            |
     *                                         +-------------------+
     * INDEX_GLOBALS_ARGUMENT               -> | PythonObject      |
     *                                         +-------------------+
     * INDEX_CLOSURE                        -> | PCell[]           |
     *                                         +-------------------+
     * INDEX_CALLER_FRAME_INFO              -> | PFrame.Reference  |
     *                                         +-------------------+
     * INDEX_CURRENT_FRAME_INFO             -> | PFrame.Reference  |
     *                                         +-------------------+
     * INDEX_CURRENT_EXCEPTION              -> | PException        |
     *                                         +-------------------+
     * USER_ARGUMENTS                       -> | arg_0             |
     *                                         | arg_1             |
     *                                         | ...               |
     *                                         | arg_(nArgs-1)     |
     *                                        +-------------------+
     */
    //@formatter:on
    /**
     * 
     * @param frame
     */
    public List<String> getArgumentTypes(VirtualFrame frame) {
        assert frame != null : "getArgumentTypes::frame = null";

        List<String> paramTypes = new ArrayList<String>();
        Object arg = null;
        Object[] arguments = frame.getArguments();
        int len = arguments.length;

        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                /**
                 * JS user arguments start at index = 2 in the frame.
                 */

                // Object[] argsJs = new Object[arguments.length - JS_USER_ARGUMENTS_OFFSET];
                // arraycopy(arguments, JS_USER_ARGUMENTS_OFFSET, argsJs, 0, argsJs.length);

                // printFrameArguments(frame);

                for (int i = JS_USER_ARGUMENTS_OFFSET; i < len; i++) {

                    paramTypes.add(PolyTaintFunction.getType(arguments[i]));
                }

                break;

            case PYTHON:
                /*
                 * Python user arguments are at the last index in the frame as shown in the layout
                 * above. Collect the user args until PArguments.getArgument returns null.
                 */

                // Object[] argsJs = new Object[arguments.length - JS_USER_ARGUMENTS_OFFSET];
                // arraycopy(arguments, JS_USER_ARGUMENTS_OFFSET, argsJs, 0, argsJs.length);

                // printFrameArguments(frame);

                for (int i = PYTHON_USER_ARGUMENTS_OFFSET; i < len; i++) {

                    paramTypes.add(PolyTaintFunction.getType(arguments[i]));
                }

                break;
            case RUBY:
                // TODO
                break;

        }

        return paramTypes;
    }

    public static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    /**
     * Print all the information in the python frame
     * 
     * @param frame
     */
    protected void printFrameArguments(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();

        Logger.log("------------ Frame Arguments: size = " + arguments.length + " ----------");
        for (int i = 0; i < arguments.length; i++) {
            Object o = arguments[i];
            String obj_str = o != null ? o.toString() : "null object";
            String obj_class = o != null ? o.getClass().toGenericString() : "no class";
            Logger.log("Arg " + i + " = " + obj_str + " class: " + obj_class);
        }
    }

    /**
     * pyuhala: copied implementation from ExternalFunctionNodes.java
     * 
     * @param frame
     * @return
     */
    protected Object[] preparePythonArguments(VirtualFrame frame) {
        Object[] variableArguments = PArguments.getVariableArguments(frame);

        int variableArgumentsLength = (variableArguments != null) ? variableArguments.length : 0;
        // we need to subtract 1 due to the hidden default param that carries the
        // callable
        int userArgumentLength = PArguments.getUserArgumentLength(frame) - 1;
        int argumentsLength = userArgumentLength + variableArgumentsLength;
        Logger.log("prepare-python-args:: variableArglen: " + variableArgumentsLength
                + " userArgLen: " + userArgumentLength + " argumentsLength: " + argumentsLength);
        Object[] arguments = new Object[argumentsLength];

        // first, copy positional arguments
        PythonUtils.arraycopy(frame.getArguments(), PArguments.USER_ARGUMENTS_OFFSET, arguments, 0,
                userArgumentLength);

        // now, copy variable arguments
        if (variableArguments != null) {
            PythonUtils.arraycopy(variableArguments, 0, arguments, userArgumentLength,
                    variableArgumentsLength);
        }
        return arguments;
    }

    public Object getJsArgument(VirtualFrame frame, int index) {
        return getArguments(frame)[JS_USER_ARGUMENTS_OFFSET + index];
    }

    public Object[] getArguments(VirtualFrame frame) {
        return frame.getArguments();
    }

    public void parseArgumentsGeneric() {

    }

    /**
     * @return the index of the first argument
     */
    public int getOffSet() {
        return isJsNew ? 1 : 2;
    }

    public boolean isNew() {
        return this.isJsNew;
    }

    /**
     *
     * get the node-specific attribute, in case of missing such attributes report an error
     *
     * @param key of the current InstrumentableNode
     * @return the value of this key //TODO: copyright nodeprof
     */
    @TruffleBoundary
    public Object getAttribute(String key) {
        if (PolyTaintInstrument.guestLanguageId.equals("js")) {
            Object result = null;
            try {
                result = InteropLibrary.getFactory().getUncached()
                        .readMember(((InstrumentableNode) instrumentedNode).getNodeObject(), key);
                // System.out.println("JS getAttribute: key = " + key + " value = " + (String)
                // result);

            } catch (Exception e) {
                // reportAttributeMissingError(key, e);
                System.out.println("getAttribute::Attribute " + key + " doesn't exist");
            }
            return result;
        } else if (PolyTaintInstrument.guestLanguageId.equals("python")) {
            // System.out.println("guest = python. getAttribute::Attribute " + key + "
            // doesn't exist");
            // TODO
            return null;
        } else {
            return null;
        }
    }

    @TruffleBoundary
    public Object getGlobalAttribute(String key) {
        Object result = null;
        try {

            result = INTEROP.readMember(result, key);

        } catch (Exception e) {
            // reportAttributeMissingError(key, e);
            System.out.println("getGlobalAttribute::Attribute " + key + " doesn't exist");
        }
        return result;
    }

    /**
     * retrieve the real value from the inputs with exception handler
     *
     * @param index
     * @param inputs
     * @param inputHint
     * @return the value of inputs[index]
     */
    protected Object assertGetInput(int index, Object[] inputs, String inputHint) {
        if (inputs == null) {
            reportInputsError(index, null, "InputsArrayNull", inputHint);
            return null;
        }
        if (index < inputs.length) {
            Object result = inputs[index];
            if (result == null) {

                reportInputsError(index, inputs, "InputElementNull", inputHint);
            }
            return result;
        } else {
            /**
             * if the inputs are not there, report the detail and stop the engine.
             */
            reportInputsError(index, inputs, "MissingInput", inputHint);
        }
        return null;
    }

    @TruffleBoundary
    private void reportInputsError(int index, Object[] inputs, String info, String inputHint) {
        Logger.log("[error while getting js input arguments]");

    }

    /**
     *
     * Returns the short name of the source file i.e no full path
     */
    public String getSourceFile() {
        return getPrimaryFileSimpleName();

    }

    public Node getPolyRootNode() {
        return instrumentedNode.getRootNode();
    }

    /**
     * Get root name (i.e englobing function) of instrumented node
     * 
     * @param instrumentedNode
     * @return
     */

    public String extractRootName(Node instrumentedNode) {
        RootNode rootNode = instrumentedNode.getRootNode();
        if (rootNode != null) {
            if (rootNode.getName() == null) {
                return rootNode.toString();
            } else {
                return rootNode.getName();
            }
        } else {
            return "<unknown>";
        }
    }

    /**
     * Peterson Yuhala Checks if a source section is explicitly creating a secureL context. This
     * could also be done by analysing the arguments in the virtual frame. But I prefer this way for
     * now.
     */
    public boolean hasSecureLInterop(SourceSection section) {

        if (section.hasLines()) {
            // check if the source code has a special filter string at its beginning
            CharSequence sourceChars = section.getCharacters();
            String sourceHead = sourceChars.subSequence(0, Math.min(sourceChars.length() - 1, 1000))
                    .toString().trim();
            // should be enough

            // TODO: test for other polyglot eval functions
            boolean hasJS = sourceHead.contains(SourceFragmentConsts.JS_POLY_EVAL_SECL);
            boolean hasPython = sourceHead.contains(SourceFragmentConsts.PYTHON_POLY_EVAL_SECL);
            boolean secLTest = hasJS || hasPython;

            if (secLTest) {
                // Logger.log("Eval is secureL: sourceHead is: " + sourceHead);

                return true;
            }
        }

        return false;
    }

    /**
     * Peterson Yuhala Parses source text corresponding and extracts information demanded.
     * 
     * @param source
     * @param baseName
     * @return
     */
    public List<String> extractSourceInfo(SourceSection section, SourceInfoID info) {

        String sourceHead = "";
        if (section.hasLines()) {
            CharSequence sourceChars = section.getCharacters();
            sourceHead = sourceChars.toString().replaceAll("\\s+", "");

            // sourceHead = sourceChars.subSequence(0, Math.min(sourceChars.length() - 1,
            // 2000)).toString().trim();
        }
        // System.out.println(">>>>>>>>>>> Extracting tokens for source: " +
        // sourceHead);
        String[] tokens = {};
        switch (info) {
            case FUNC_NAME:
                tokens = sourceHead.split(SourceFragmentConsts.FUNC_NAME_SPLIT_REGEX);
                break;
            case FUNC_CALL:
                tokens = sourceHead.split(SourceFragmentConsts.FUNC_CALL_SPLIT_REGEX);
                break;
            case FUNC_PARAMS:
                tokens = sourceHead.split(SourceFragmentConsts.FUNC_PARAMS_SPLIT_REGEX);
                break;

            case VAR_WRITE_OPERANDS:
                tokens = sourceHead.split(SourceFragmentConsts.VAR_WRITE_SPLIT_REGEX);
            default:
                break;
        }

        List<String> args = Arrays.asList(tokens);
        // printTokens(args);
        return args;
    }

    public void printTokens(List<String> tokens) {
        for (String string : tokens) {
            System.out.println(string);
        }
    }

    /**
     * Return corresponding source code of instrumented node
     * 
     * @return
     */
    public String getInstrumentedSource() {
        CharSequence sourceChars = instrumentedSourceSection.getCharacters();
        return sourceChars.toString();
    }

    /**
     * Checks if the read variable is tainted
     * 
     * @return
     */
    public boolean isTaintedVariable(String varName) {
        return instrument.tracker.isTaintedVariable(varName);
    }

    /**
     * Checks if the input method is tainted
     * 
     * @return
     */
    public boolean isTaintedMethod(String methodName) {
        return instrument.tracker.isTaintedMethod(methodName);
    }

    /**
     * Tests if a symbol declaration/method call should be added to the main routine of the native
     * image.
     * 
     * @param rootName
     * @return
     */
    public boolean isMainSymbol(String rootName) {
        boolean test = false;
        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                if (rootName.contains(":program")) {
                    test = true;
                }
                break;

            default:
                break;
        }
        return test;
    }

    /**
     * Checks the source section to determine if this variable write is receiving values from a
     * secureL polyglot interopt. pyuhala: can be done more smartly; TODO
     * 
     * @return
     */
    public boolean isSecureLInterop() {

        SourceSection section = instrumentedNode.getSourceSection();
        return hasSecureLInterop(section);

    }

    /**
     * Test for generic builtins: var-reads, var-writes, function calls etc
     * 
     * @return
     */
    public boolean isGenericBuiltin() {

        // boolean isBuiltin = false;
        // String source = getInstrumentedSource();

        // String desc = instrumentedNode.getDescription();
        // String primaryFileFullName = PolyTaintInstrument.primaryFile;
        // String[] parts = primaryFileFullName.split("/");
        // String primaryFileSimpleName = parts[parts.length - 1];

        // Logger.log("isGenericBuiltin: desc " + desc);

        // if (!desc.contains(primaryFileSimpleName)) {
        // isBuiltin = true;
        // } else {

        // }
        // // Logger.log("isGenericBuiltin test: " + source + " isBuiltin: " +
        // isBuiltin);
        // return isBuiltin;
        boolean isBuiltin = false;
        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                JavaScriptNode jsNode = (JavaScriptNode) instrumentedNode;
                isBuiltin = jsNode instanceof JSBuiltinNode;
                if (isBuiltin) {
                    Logger.log(">>>>>>>>>>>>>>>>> is JS BuiltIn node >>>>>>>>>>>>>>");
                }

                break;

            case PYTHON:

                break;

            default:
                break;
        }

        return isBuiltin;
    }

    /**
     * Test for generic builtins: var-reads, var-writes, function calls etc
     * 
     * @return
     */

    public boolean isVarBuiltin() {

        // Logger.log("isGenericBuiltin test: " + source + " isBuiltin: " + isBuiltin);
        return false;
    }

    /**
     * Tests if instrumented node exposes function call tag
     * 
     * @return
     */
    public boolean isMethod() {
        boolean isFunc = false;
        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                JavaScriptNode jsNode = (JavaScriptNode) instrumentedNode;
                isFunc = jsNode.hasTag(JSTags.FunctionCallTag.class)
                        || jsNode.hasTag(CallTag.class);

                break;

            case PYTHON:

                break;

            default:
                break;
        }

        return isFunc;
    }

    /**
     * Its very tricky to have this right. So far I use some key words to test for builtin routines.
     * 
     * @return
     */
    public boolean ispythonBuiltin() {
        boolean isBuiltin = false;

        // PythonContext pyContext = PythonContext.get(null);
        // final PythonModule builtins = pyContext.getBuiltins();

        // final PythonContext context = new PythonContext(this, env, new
        // PythonParserImpl(env));

        // PythonContext pyContext = new PythonContext(, env, parser)
        String source = getInstrumentedSource();

        for (String str : SourceFragmentConsts.pyBuiltIns) {
            if (source.contains(str)) {
                isBuiltin = true;
                break;
            }
        }

        // Logger.log("Instrumented source in python-built-in test: " + source + "
        // isBuiltin: " + isBuiltin);

        return isBuiltin;
    }

    /**
     * Checks if the input function name corresponds to a built in function for the given language.
     * 
     * @param funcName
     * @return
     */
    public boolean isBuiltInRoot(String funcName) {

        // Logger.log("current function: isBuiltInRoot:: funcName: " + funcName);
        boolean isBuiltin = false;
        RootNode rootNode = instrumentedNode.getRootNode();

        if (rootNode == null) {
            Logger.log("root node is null .>>>> should be builtin");
            return true;
        }

        String pfShortName = getPrimaryFileSimpleName();

        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                // JavaScriptNode jsNode = (JavaScriptNode) instrumentedNode;
                for (String str : SourceFragmentConsts.jsBuiltIns) {
                    if (funcName.contains(str)) {
                        isBuiltin = true;
                        break;
                    }
                }
                // Do second test to be sure

                if (!isBuiltin) {
                    isBuiltin = rootNode.isInternal();
                }
                break;
            case PYTHON:
                // Test 1
                /**
                 * The RootNode class can tell us if it is internal or not.
                 */

                isBuiltin = rootNode.isInternal() || instrumentedNode instanceof PythonBuiltinNode;

                // Test 2
                if (!isBuiltin) {
                    String nodeFile =
                            instrumentedNode.getEncapsulatingSourceSection().getSource().getName();

                    // Logger.log("testing builtin for function: " + funcName + "nodeFile source: "
                    // + nodeFile);
                    isBuiltin = !nodeFile.contains(pfShortName);
                }
                // Test 3: longest test
                if (!isBuiltin) {
                    for (String str : SourceFragmentConsts.pyBuiltIns) {
                        if (funcName.contains(str)) {
                            isBuiltin = true;
                            break;
                        }
                    }

                }

                break;

            default:
                break;
        }

        if (!isBuiltin) {
            // Logger.log("non-built-in function: " + funcName + " is-built-in test: " +
            // isBuiltin);
        }
        return isBuiltin;
    }

    /**
     * Tests if a string is numeric
     */

    public boolean isNumeric(String str) {
        Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");

        if (str == null) {
            return false;
        }
        return pattern.matcher(str).matches();
    }

    /**
     * Get primary file full name. E.g: /home/ubuntu/truffle-tool/myFile.py
     * 
     * @return
     */
    public static String getPrimaryFileFullName() {

        return PolyTaintInstrument.primaryFile;
    }

    /**
     * Gets primary file simple name This only works for unix-like path names
     * 
     * @return
     */
    public static String getPrimaryFileSimpleName() {

        String pfFullName = getPrimaryFileFullName();
        String[] parts = pfFullName.split("/");
        return parts[parts.length - 1];

    }

    /**
     * We only track application functions
     * 
     * @param funcNode
     */
    public void addSeenMethod(String methodName, PolyTaintFunction polyFunc) {

        // Logger.log(" ************* >>> adding seen method: " + methodName);
        instrument.tracker.addSeenMethod(methodName, polyFunc);

    }

    /**
     * The the corresponding PolyTaintFunction Object for the method with the given name.
     * 
     * @param methodName
     * @return
     */
    public PolyTaintFunction getSeenMethod(String methodName) {
        return instrument.tracker.getSeenMethod(methodName);
    }

    /**
     * PYuhala: If we have already seen input types for this function, we check if the new the new
     * input types fit in the old ones. Otherwise, we set the bigger input type as argument type to
     * prevent overflows. For example, if func took "int" in the first call, and now takes "double",
     * choose "double" as the new input type.
     * 
     * 
     */
    public void resolveInputTypes(PolyTaintFunction func, VirtualFrame vFrame) {
        // the input types already registered
        List<String> oldTypes = func.getArgumentTypes();
        int numTypes = oldTypes.size();

        if (numTypes == 0) {
            /**
             * void inputs --> no type resolution needed.
             */
            return;
        }
        // the input types in this virtual frame
        List<String> currentTypes = getArgumentTypes(vFrame);
        // the new types to be set after type resolution
        List<String> newTypes = new ArrayList<String>(numTypes);

        for (int i = 0; i < numTypes; i++) {
            String prev = oldTypes.get(i);
            String curr = currentTypes.get(i);

            String newType = PType.getLargerType(prev, curr);
            newTypes.add(newType);
        }

        // set the new argument types.
        func.setParamTypes(newTypes);


    }

}
