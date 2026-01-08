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

// jdk
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

// truffle-api
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;

// polyt
import com.oracle.truffle.polyt.SourceFragmentConsts.SourceInfoID;
import com.oracle.truffle.polyt.PolyTaintInstrument;
import com.oracle.truffle.polyt.partitioner.PolyTaintFunction;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;
import com.oracle.truffle.polyt.TaintTracker;
import com.oracle.truffle.polyt.utils.Logger;

/**
 * Generic instrumentation node for variable writes.
 */

public class VarWriteWrapperNode extends PolyTaintNode {

    public VarWriteWrapperNode(PolyTaintInstrument instrument, EventContext context) {
        super(instrument, context);

        this.name = getPolyTaintNodeName(PolyTaintInstrument.guestLanguageId);

    }

    @Override
    public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex,
            Object inputValue) {
        System.out.println(">>>>> on input value event occurred >>");
    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        if (isVarBuiltin() || isMethod()) {
            return;
        }
        // Logger.log(
        // "onReturn value for variable write result object-to-string: " + result.toString());

        // printFrameArguments(vFrame);
        if (!seen) {
            /**
             * We don't instrument builtins or function calls here
             */

            CompilerDirectives.transferToInterpreterAndInvalidate();
            seen = true;
            // instrument.addTainted(instrumentedSourceSection);

            // printInstrumentationInfo(vFrame);
            // Logger.log("In variable write node: " + getFullVarName() + " rootname: " +
            // getRootFuncFullName());

            /**
             * Parent/root function node has already been seen since we "see" the methods/functions
             * in the onEnter probe.
             */
            PolyTaintFunction polyFunc = instrument.tracker.getSeenMethod(getRootFuncFullName());

            // add tainted Item to global hash set
            if (hasTaintedOperand(vFrame)) {
                instrument.tracker.addTaintedVariable(getFullVarName(), null);
                Logger.log("Adding method: " + getRootFuncFullName() + " of tainted variable: "
                        + getFullVarName());

                TaintTracker.taintFunction(polyFunc, FunctionType.TRUSTED);
                instrument.tracker.addTaintedMethod(getRootFuncFullName(), polyFunc);
            }

        }
    }

    /**
     * Tests if the variable write has an already tainted operand
     * 
     * 
     * @param varWriteNode
     * @return
     */
    public boolean hasTaintedOperand(VirtualFrame vFrame) {
        // TODO: go thru list of already tainted variables and check if they have a
        // taint tag
        boolean test = false;
        //@formatter:off
        // Object[] args = vFrame.getArguments();
        // Logger.log(">>>>>>>>> hasTainted Operand >>>>>>>>");
        // for (Object arg : args) {
        //     Logger.log("obj: " + arg.toString() + " hashcode: " + arg.hashCode());
        // }
        //@formatter:on

        // case 1: is explicit Polyglot.eval secureL instantiation
        test = isSecureLInterop();

        if (test) {
            // no need testing further
            return true;
        } else {
            /**
             * case 2: is implicit i.e atleast one tainted operand Tokenize source section and check
             * for tainted variables
             */
            List<String> tokens = extractSourceInfo(instrumentedNode.getSourceSection(),
                    SourceInfoID.VAR_WRITE_OPERANDS);
            // TODO: get regex for func calls in expressions
            for (String string : tokens) {
                String tempVar = getRootFuncFullName() + "." + string;
                String tempFunc = getSourceFileName() + "." + string;

                if (isTaintedVariable(tempVar) || isTaintedMethod(tempFunc)) {
                    return true;
                }
            }

        }

        return test;
    }

    /**
     * Get the full name of the variable being written to. Polytaint full names are of the form:
     * sourceFile.rootName.varName For example: polylgot.js.func1.secInt These names will be added
     * to the hashset if tainted and used during analysis.
     */

    public String getFullVarName() {

        return getRootFuncFullName() + "." + this.name;
        // return this.name;
    }

    public Object getValue(Object[] inputs) {
        return assertGetInput(0, inputs, "getValue");
    }

}
