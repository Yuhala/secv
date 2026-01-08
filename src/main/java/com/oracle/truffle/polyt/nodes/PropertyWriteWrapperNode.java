/*
 * Created on Sept 12, 2022
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
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyt.PolyTaintInstrument;

import com.oracle.truffle.api.strings.TruffleString;

import com.oracle.truffle.polyt.utils.Logger;

/**
 * Generic instrumentation node for variable writes.
 */

public class PropertyWriteWrapperNode extends PolyTaintNode {

    protected String property;

    public PropertyWriteWrapperNode(PolyTaintInstrument instrument, EventContext context) {
        super(instrument, context);

        this.property = getPropertyString();

    }

    @Override
    public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {

    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {

        if (!seen) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seen = true;
            String propertyName = this.property;
            if (isSecureLInterop()) {
                // Logger.log("Object property write is secL interop:: property name: " +
                // propertyName + " rootname: " +
                // getRootFuncFullName());
                // this is a tainted global variable
                if (getFullPropertyName().contains("global")) {
                    /*
                     * This is just a test and should be fixed ASAP.
                     * All function definitions are also considered object property writes for
                     * the global js object.
                     */
                    instrument.tracker.addTaintedVariable(getFullPropertyName(), null);
                }

            }

        }
    }

    /**
     * Get the full name of the property being written to. Polytaint full names are
     * of the form:
     * sourceFile.rootName.varName
     * For example: polylgot.js.func1.secInt
     * These names will be added to the hashset if tainted and used during analysis.
     */

    public String getFullPropertyName() {

        return getRootFuncFullName() + "." + this.property;
        // return this.name;
    }

    public Object getValue(Object[] inputs) {
        return assertGetInput(0, inputs, "getValue");
    }

    public TruffleString getAttributeTString(String key) {
        // TODO: test for the guest language
        Object result = getAttribute(key);
        // assert Strings.isTString(result);
        return (TruffleString) result;
    }

    public String getPropertyString() {
        String prop = "";
        if (guestId.equals("js")) {
            prop = getAttribute("key").toString();
        } else if (guestId.equals("python")) {
            // TODO
        }
        return prop;
    }

}
