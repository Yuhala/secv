/*
 * Created on Wed Mar 09 2022
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

package com.oracle.truffle.polyt.factory;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyt.PolyTaintInstrument;
import com.oracle.truffle.polyt.nodes.JSEvalWrapperNode;
import com.oracle.truffle.polyt.nodes.PolyTaintNode;
import com.oracle.truffle.polyt.nodes.PropertyWriteWrapperNode;
import com.oracle.truffle.polyt.nodes.FunctionCallWrapperNode;
import com.oracle.truffle.polyt.nodes.VarWriteWrapperNode;
import com.oracle.truffle.polyt.nodes.RootWrapperNode;
import com.oracle.truffle.polyt.nodes.PythonGenericReadWrapper;
import com.oracle.truffle.polyt.SourceFragmentConsts;
import com.oracle.truffle.polyt.EventEnum;
import com.oracle.truffle.polyt.nodes.RootBodyWrapper;

/**
 * A factory for nodes that track secureL statement executions.
 * 
 * Each time an AST node of interest is created (i.e secureL statement node), it
 * is instrumented with a node created from this factory.
 */
public final class PolyTaintEventFactory implements ExecutionEventNodeFactory {

    private PolyTaintInstrument polyTaint;
    private EventEnum eventId;

    public PolyTaintEventFactory(PolyTaintInstrument pt, EventEnum id) {
        this.polyTaint = pt;
        this.eventId = id;

    }

    @Override
    public ExecutionEventNode create(final EventContext context) {

        PolyTaintNode instrumentationNode;

        switch (eventId) {
            case JAVA_ID:
                instrumentationNode = new PolyTaintNode(polyTaint, context);
                break;

            case JS_EVAL:
                instrumentationNode = new JSEvalWrapperNode(polyTaint, context);
                break;

            case GENERIC_VAR_WRITE:
                instrumentationNode = new VarWriteWrapperNode(polyTaint, context);
                break;
            case GENERIC_VAR_READ:
                instrumentationNode = new PythonGenericReadWrapper(polyTaint, context);
                break;
            case FUNC_INVOKE:
                instrumentationNode = new FunctionCallWrapperNode(polyTaint, context);
                break;

            case PROPERTY_WRITE:
                instrumentationNode = new PropertyWriteWrapperNode(polyTaint, context);
                break;

            case ROOT_TAG:
                instrumentationNode = new RootWrapperNode(polyTaint, context);
                break;

            case ROOT_BODY:
                instrumentationNode = new RootBodyWrapper(polyTaint, context);
                break;

            default:
                instrumentationNode = new PolyTaintNode(polyTaint, context);
                break;
        }
        return instrumentationNode;

    }

}
