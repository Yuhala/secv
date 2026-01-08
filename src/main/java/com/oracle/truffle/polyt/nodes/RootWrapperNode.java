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
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic instrumentation node for monitoring function calls. All function calls with tainted
 * parameters taint the corresponding methods.
 */

public class RootWrapperNode extends PolyTaintNode {

    public RootWrapperNode(PolyTaintInstrument instrument, EventContext context) {
        super(instrument, context);
    }

    @Override
    public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex,
            Object inputValue) {

        // TODO: get params

    }

    @Override
    public void onEnter(VirtualFrame vFrame) {

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

        // Logger.log("onEnter::rootWrapperNode python desc: " + instrumentedNode.getDescription());

        if (!seen) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seen = true;

        }

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
                Logger.log("setting null param types for python function >>>> ");
                Logger.log("python node info: " + instrumentedNode.getDescription());
                polyFunc.setParamTypes(null);
                break;

            case RUBY:
                break;

            default:
                break;
        }

        return polyFunc;
    }

    /**
     * This wrapper method permits us to obtain return types for the seen methods.
     */
    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        /**
         * We don't instrument builtins
         */
        if (isBuiltInFunction()) {
            return;
        }

        if (!returnRegistered) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            returnRegistered = true;
            // instrument.addTainted(instrumentedSourceSection);
            // String rootFullName = getRootFuncFullName();
            String source = getInstrumentedSource();

            instrument.tracker.addReturnType(source, result);

        }
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
     * @param vFrame
     * @return
     */
    public void handleNeutral(VirtualFrame vFrame) {

        Object[] args = vFrame.getArguments();
        if (args.length < 3) {
            // this function has no arguments (for JS)
            // return false;
        }
        // param could be variable or function call itself
        List<String> tokens =
                extractSourceInfo(instrumentedNode.getSourceSection(), SourceInfoID.FUNC_CALL);
        if (tokens.isEmpty()) {
            return;
        }

        String funcName = tokens.get(0);
        String fullName = getSourceFileName() + "." + funcName;

        if (isTaintedMethod(fullName)) {
            taintCaller();
        }
        // System.out.println("------------ List of tainted inputs: " + funcName +
        // "---------");
        // todo: skip index 0
        /**
         * Test if any arguments of the function are tainted variables
         */
        for (String string : tokens) {
            String tempVar = getRootFuncFullName() + "." + string;
            Logger.log("handleNeutral::tempVar >>>>> " + tempVar);
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

        Logger.log("adding seen method: " + methodName);
        instrument.tracker.addSeenMethod(methodName, polyFunc);

    }

    /**
     * Checks if a function or method is a builtin function in any of the supported guest languages
     * 
     * @param funcNode
     * @return
     */
    public boolean isBuiltInFunction() {
        // more generic test
        boolean builtIn = isGenericBuiltIn();

        // Logger.log("node name:" + instrumentedNode.toString() + "isBuiltInFunction: "
        // + builtIn);
        return builtIn;
    }

    /**
     * This is simply a hack and probably not the best way. We use the toString description of the
     * node to infer if it is a builtin function or not. For JS, we test for keywords/tags like
     * "(intermediate value)" to know builtin function names. For python, we check if the node
     * description contains the primary file name to know if it is not a builtin function.
     * 
     * @return
     */
    public boolean isGenericBuiltIn() {
        // ruby specific test
        boolean isBuiltin = false;

        /**
         * Node.toString should return a string which contains the filename of the node's parent
         * file
         */
        String desc = instrumentedNode.toString();
        String primaryFileFullName = PolyTaintInstrument.primaryFile;
        String[] parts = primaryFileFullName.split("/");
        String primaryFileSimpleName = parts[parts.length - 1];

        // Logger.log("primary file: " + primaryFileSimpleName + " instrumented node
        // desc: " + desc);

        boolean isJSBuiltIn = desc.contains(SourceFragmentConsts.JS_BUILTIN_TAG);
        boolean isPythonBuiltIn = !desc.contains(primaryFileSimpleName);

        if (isJSBuiltIn || isPythonBuiltIn) {
            isBuiltin = true;

        } else {
            Logger.log("primary file: " + primaryFileSimpleName
                    + " non-built-in instrumented node desc: " + desc);

            InstrumentableNode inode = (InstrumentableNode) instrumentedNode;

            // PNode pnode = (PNode) inode;
            Logger.log("node obj : " + inode.getNodeObject());

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

}
