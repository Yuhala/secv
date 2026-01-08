
/*
 * Created on Wed Mar 31 2022
 *
 * The MIT License (MIT)
 * Copyright (c) 2022 Peterson Yuhala
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.oracle.truffle.polyt.nodes;

import java.util.ArrayList;
import java.util.Iterator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.polyt.PolyTaintInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.interop.InteropLibrary;

import com.oracle.truffle.polyt.partitioner.PolyTaintFunction;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;

/**
 * Specialized instrumentation nodes for javascript ASTs. These nodes take into
 * consideration language(js)-specific semantics when instrumenting the AST
 * as opposed to the more generic PolyTaintNodes which instrument generic
 * Truffle AST nodes.
 */

public class JSEvalWrapperNode extends FunctionCallWrapperNode {

    public JSEvalWrapperNode(PolyTaintInstrument instrument, EventContext context) {
        super(instrument, context);

    }

    @Override
    public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
        System.out.println(">>>>> on input value event occurred >>");
    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        if (!seen) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seen = true;

           
            // printInstrumentationInfo(vFrame);

            /**
             * We are concerned with the caller/root here if it is Polyglot.eval function.
             * The caller should have been seen at this point if it is Polyglot.eval.
             */
            SourceSection section = instrumentedNode.getSourceSection();
            if (hasSecureLInterop(section)) {
                PolyTaintFunction evalCaller = instrument.tracker.getSeenMethod(getRootFuncFullName());

                if (evalCaller != null) {
                    // these are primary taint sources
                    evalCaller.setFunctionType(FunctionType.TRUSTED);
                    instrument.tracker.addTaintedMethod(getRootFuncFullName(), evalCaller);
                }

                // taint the value-receiving variable
                taintValueReceiver(vFrame);
            }

        }
    }

    /**
     * Analyse the call to eval to detect and taint
     * the variable receiving eval's output value.
     */
    public void taintValueReceiver(VirtualFrame vFrame) {
        // TODO
    }

}
