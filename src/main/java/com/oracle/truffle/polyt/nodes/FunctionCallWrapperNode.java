/*
 * Created on Wed Mar 31 2022
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

package com.oracle.truffle.polyt.nodes;

// truffle-api
import com.oracle.graal.python.nodes.PNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.polyt.PolyTaintInstrument;
import com.oracle.truffle.polyt.SourceFragmentConsts;
import com.oracle.truffle.polyt.SourceFragmentConsts.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyt.partitioner.PolyTaintFunction;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;
import com.oracle.truffle.polyt.utils.Logger;

import com.oracle.truffle.polyt.PolyTaintInstrument.Guest;
import com.oracle.truffle.api.interop.InteropLibrary;

// python
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.runtime.PythonContext;

// js
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.instrumentation.JSTags;

import java.util.ArrayList;
import java.util.List;



/**
 * Generic instrumentation node for monitoring function calls. All function calls with tainted
 * parameters taint the corresponding methods.
 */

public class FunctionCallWrapperNode extends PolyTaintNode {

    public FunctionCallWrapperNode(PolyTaintInstrument instrument, EventContext context) {
        super(instrument, context);
    }

    // @Override
    public void onEnterxx(VirtualFrame vFrame) {

        /**
         * We don't instrument builtins
         */
        if (isBuiltInFunction()) {
            return;
        }

        // String rootFullName = getRootFuncFullName();
        // String funcName = getFuncFullName();

        String source = getInstrumentedSource();

        // Parse the instrumented source to get the function full name
        String funcName = getFuncName(source);
        // Instrument only functions you have not seen yet.
        boolean funcSeen = functionSeen(funcName);



        if (!funcSeen) {
            Logger.log("onEnter::functionCallWrapper::func-full-name: " + funcName + " source: "
                    + source);
            printFrameArguments(vFrame);

            // CompilerDirectives.transferToInterpreterAndInvalidate();
            seen = true;
            PolyTaintFunction polyFunc =
                    createPolyFuncObject(funcName, FunctionType.UNKNOWN, getRootNode(), vFrame);

            addSeenMethod(funcName, polyFunc);

            if (isMainSymbol(funcName)) {
                registerMain(polyFunc, source);
            }

            /**
             * If secure arguments have been passed, set the method as neutral. If it has secure
             * variables within its body it will be promoted to trusted.
             */
            // handleNeutral(vFrame);

        }

    }

    /**
     * This callback function helps us find possible neutral functions
     */
    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        String source = getInstrumentedSource();
        /**
         * NB: we are using the source here and parsing it to obtain the corresponding function
         * call. Do not mistakenly use the overloaded variant of getFuncName with the VirtualFrame
         * parameter. This is because the latter method uses the instrumented node's RootNode to
         * obtain the function name. However the root nodes are different for CallTag and
         * RootBodyTag nodes.
         */
        String funcName = getFuncName(source);

        /**
         * We don't instrument builtins
         */
        if (isBuiltInRoot(funcName)) {
            return;
        }

        // String str = result == null ? "result is null" : "result is not null: " +
        // result.toString();

        // Logger.log("FunctionCallWrapper::OnReturnValue:: " + funcName + ", source = "
        // + source + ", result info: " + str);

        /**
         * Register return values for python here. The RootBodyCall wrapper has issues handling
         * python return values properly.
         */
        if (PolyTaintInstrument.primaryGuest == Guest.PYTHON) {

            // boolean returnSeen = returnTypeSeen(funcName);

            /**
             * If we have already registered a return type for this function, we check if this new
             * return types fits in the old one. Otherwise, we set this return type as the new
             * return type. For example, if func returned "int" in the first call, and now returns
             * "double", choose "double" as the new return type.
             * 
             * the addReturnType function does all these checks.
             */

            instrument.tracker.addReturnType(funcName, result);

            // if (!returnSeen) {

            // CompilerDirectives.transferToInterpreterAndInvalidate();
            // // returnRegistered = true;

            // instrument.tracker.addReturnType(funcName, result);

            // }
        }

        handleNeutral();

    }

    /**
     * Creates a polyFunc object with the given attributes. The vFrame is used to deduce the
     * argument types. The logic to do this varies according to the guest Language.
     * 
     * @param funcName
     * @param type
     * @param rootNode
     * @param vFrame
     * @return
     */
    public PolyTaintFunction createPolyFuncObject(String funcName, FunctionType type, Node rootNode,
            VirtualFrame vFrame) {
        PolyTaintFunction polyFunc = new PolyTaintFunction(funcName, type, rootNode, null);

        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                polyFunc.setParamTypes(getArgumentTypes(vFrame));
                break;
            case PYTHON:
                // Logger.log("Argument types for python poly function: " +
                // getArgumentTypes(vFrame));
                polyFunc.setParamTypes(getArgumentTypes(vFrame));
                break;

            case RUBY:
                break;

            default:
                break;
        }

        return polyFunc;
    }

    /**
     * Registers source to be added to main routine of final native image application.
     */
    public void registerMain(PolyTaintFunction func, String source) {
        func.setIsMainSymbol(true);
        instrument.tracker.addMainSource(source);
    }

    public String getCaller() {
        String caller = "none";
        caller = instrumentedNode.getRootNode().getQualifiedName();
        return caller;
    }

    /**
     * If this function is tainted/trusted, taint caller too. Otherwise we keep moving ...
     */
    public void taintCaller() {
        /**
         * By this time the caller has been called/seen (or is null), so we already have seen its
         * param list. Get the corresponding caller from the list of seen methods.
         */

        PolyTaintFunction caller = instrument.tracker.getSeenMethod(getRootFuncFullName());

        if (caller != null) {
            caller.setFunctionType(FunctionType.TRUSTED);
            // instrument.tracker.addTaintedMethod(getRootFuncFullName(), caller);
        }

    }

    /**
     * Checks if this function takes a tainted variable as input PYuhala: so far reading values in
     * the virtual frame haven't helped. So we parse the corresponding source to detect tainted
     * input/params.
     * 
     * NB: there is something weird about the logic of this function; I probably forgot what the
     * source section info represents exactly. But it seems to work so far so no touching.
     * 
     * @param vFrame
     * @return
     */
    public void handleNeutral() {

        // param could be variable or function call itself
        List<String> tokens =
                extractSourceInfo(instrumentedNode.getSourceSection(), SourceInfoID.FUNC_CALL);
        if (tokens.isEmpty()) {
            return;
        }

        // Logger.log("handleNeutral::tokens = " + tokens);

        String funcName = tokens.get(0);
        String fullName = getSourceFileName() + "." + funcName;

        /**
         * Test if any arguments of the function are tainted variables
         */
        for (int i = 1; i < tokens.size(); i++) {
            // skip token(0) == funcName and numeric arguments
            String token = tokens.get(i);
            if (isNumeric(token)) {
                continue;
            }
            String tempVar = getRootFuncFullName() + "." + token;
            // Logger.log(">>>> handleNeutral::tempVar-> " + tempVar);
            // Tainted input --> add corresponding function as possible neutral
            if (isTaintedVariable(tempVar)) {
                instrument.tracker.addUnresolvedNeutral(fullName);
                return;
            }
        }

    }

    /*
     * Parse a source string to get the name of the function. NB: works for JS and Python only
     */
    public String getFuncName(String source) {
        // Logger.log("getFuncName: source = " + source);
        List<String> tokens =
                extractSourceInfo(instrumentedNode.getSourceSection(), SourceInfoID.FUNC_CALL);
        if (tokens.isEmpty()) {
            return null;
        }

        // String funcSimpleName = source.split("\\(")[0];

        String funcName = tokens.get(0);
        String fullName = getSourceFileName() + "." + funcName;
        return fullName;
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
     * This is simply a hack and probably not the best way. We use the toString description of the
     * node to infer if it is a builtin function or not. For JS, we test for keywords/tags like
     * "(intermediate value)" to know builtin function names. For python, we check if the source
     * section contains some specific builtin keywords.
     * 
     * @return
     */
    public boolean isBuiltInFunction() {
        // ruby specific test
        boolean isBuiltin = false;

        /**
         * Node.toString should return a string which contains the filename of the node's parent
         * file
         */
        String desc = instrumentedNode.toString();
        String source = getInstrumentedSource();

        String primaryFileFullName = PolyTaintInstrument.primaryFile;
        String[] parts = primaryFileFullName.split("/");
        String primaryFileSimpleName = parts[parts.length - 1];

        // Logger.log("primary file: " + primaryFileSimpleName + " instrumented node
        // desc: " + desc);

        /**
         * Do test for given guest language
         */

        switch (PolyTaintInstrument.primaryGuest) {
            case JS:
                // JavaScriptNode jsNode = (JavaScriptNode) instrumentedNode;
                for (String str : SourceFragmentConsts.jsBuiltIns) {
                    if (source.contains(str)) {
                        isBuiltin = true;
                        break;
                    }
                }
                break;
            case PYTHON:

                if (!desc.contains(primaryFileSimpleName)) {
                    /**
                     * If the description doesn't contain the primary file name, we assume it is a
                     * builtin. This is a short test and only makes sense because we assume all our
                     * programs are contained in one file.
                     */
                    isBuiltin = true;

                } else {
                    /**
                     * The description could contain the primary file name and still be a builtin.
                     * Do deeper check
                     */

                    isBuiltin = ispythonBuiltin();

                }

            default:
                break;
        }

        if (!isBuiltin) {
            // Logger.log("primary file: " + primaryFileSimpleName + " non-built-in
            // instrumented node desc: " + desc);

        }

        return isBuiltin;
    }

    /**
     * Get a full name for the current function node
     * 
     * @return
     */
    public String getFuncFullName() {
        return sourceFileName + "." + this.name;
    }

    /**
     * Checks if a method has been seen by the taint tracker. Issue: according to this logic, we
     * can't resolve multiple param or return types by the same logic. Our POC implem tests should
     * not have such scenarios then.
     * 
     * @param funcName
     * @return
     */
    public boolean functionSeen(String funcName) {
        return this.instrument.tracker.getSeenMethods().containsKey(funcName);

    }

    /**
     * Checks if a return type has been registered for this function.
     * 
     * @param funcName
     * @return
     */
    public boolean returnTypeSeen(String funcName) {
        return this.instrument.tracker.getReturnTypes().containsKey(funcName);
    }

}
