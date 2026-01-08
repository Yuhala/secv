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
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.dsl.processor.Log;
import com.oracle.truffle.polyt.partitioner.PolyTaintFunction;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;
import com.oracle.truffle.polyt.utils.Logger;

import com.oracle.truffle.polyt.PolyTaintInstrument.Guest;
import com.oracle.truffle.api.interop.InteropLibrary;

// python
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.runtime.PythonContext;

// js

import java.util.List;

/**
 * Generic instrumentation node for monitoring function calls. All function calls with tainted
 * parameters taint the corresponding methods.
 */

public class RootBodyWrapper extends PolyTaintNode {

    public RootBodyWrapper(PolyTaintInstrument instrument, EventContext context) {
        super(instrument, context);
    }

    @Override
    public void onEnter(VirtualFrame vFrame) {

        // get the function name from the frame
        // printFrameArguments(vFrame);
        String funcName = getFuncName(vFrame);
        // Logger.log("in rootbodywrapper onEnter >>>>>>> ");
        /**
         * We don't instrument builtins
         */
        if (isBuiltInRoot(funcName) || isRootClosure(funcName)) {
            return;
        }

        boolean funcSeen = functionSeen(funcName);



        if (!funcSeen) {
            // Logger.log("onEnter::RootBodyWrapper::func-full-name: " + funcName);
            // printFrameArguments(vFrame);

            // CompilerDirectives.transferToInterpreterAndInvalidate();
            // seen = true;
            PolyTaintFunction polyFunc =
                    createPolyFuncObject(funcName, FunctionType.UNKNOWN, getRootNode(), vFrame);
            String sourceBody = instrumentedNode.getSourceSection().getCharacters().toString();
            polyFunc.setSourceBody(sourceBody);

            addSeenMethod(funcName, polyFunc);

            if (isMainSymbol(funcName)) {
                // registerMain(polyFunc, source);
            }

            /**
             * If secure arguments have been passed, set the method as neutral. If it has secure
             * variables within its body it will be promoted to trusted.
             */
            // handleNeutral(vFrame);

        } else {
            PolyTaintFunction func = getSeenMethod(funcName);
            resolveInputTypes(func, vFrame);
        }

    }

    /**
     * This wrapper method permits us to obtain return types for the seen methods.
     */
    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        /**
         * Registering return types will be done in the FunctionCallWrapper nodes for python guest
         * language.
         */
        if (PolyTaintInstrument.primaryGuest == Guest.PYTHON) {
            return;
        }

        // Parse the instrumented source to get the function full name
        String funcName = getFuncName(vFrame);

        /**
         * We don't instrument builtins
         */
        if (isBuiltInRoot(funcName) || isRootClosure(funcName)) {
            return;
        }
        // String str = result == null ? "result is null" : "result is not null";

        // Logger.log("RootBodyWrapper::OnReturnValue:: " + funcName + " result info: "
        // + str);


        /**
         * If we have already registered a return type for this function, we check if this new
         * return types fits in the old one. Otherwise, we set this return type as the new return
         * type. For example, if func returned "int" in the first call, and now returns "double",
         * choose "double" as the new return type.
         * 
         * the addReturnType function does all these checks.
         */

        // boolean returnSeen = returnTypeSeen(funcName);

        instrument.tracker.addReturnType(funcName, result);

        // if (!returnSeen) {
        // // Logger.log("OnReturnValue:: " + funcName + " result: " + result.toString());
        // // printFrameArguments(vFrame);
        // CompilerDirectives.transferToInterpreterAndInvalidate();
        // returnRegistered = true;

        // instrument.tracker.addReturnType(funcName, result);

        // }
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
                // Logger.log("getting python argument types >>>> ");
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
     * Checks if this function takes a tainted variable as input
     * 
     * @param vFrame
     * @return
     */
    public void handleNeutral(VirtualFrame vFrame) {

        // Logger.log(">>>>>>>>> in handleNeutral >>>>>>>>");

        // param could be variable or function call itself
        List<String> tokens =
                extractSourceInfo(instrumentedNode.getSourceSection(), SourceInfoID.FUNC_CALL);
        if (tokens.isEmpty()) {
            return;
        }

        String funcName = getFuncName(vFrame);
        String fullName = getSourceFileName() + "." + funcName;

        // Logger.log("handleNeutral:: funcName = token.get(0) = " + funcName);

        // if (isTaintedMethod(fullName)) {
        // taintCaller();
        // }

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

            if (isTaintedVariable(tempVar)) {
                instrument.tracker.addUnresolvedNeutral(fullName);
                return;
            }
        }

    }


    /**
     * Checks for root closures e.g for java script
     * 
     * @param funcName
     * @return
     */
    public boolean isRootClosure(String funcName) {
        /**
         * 
         */
        return funcName.endsWith(".");
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
