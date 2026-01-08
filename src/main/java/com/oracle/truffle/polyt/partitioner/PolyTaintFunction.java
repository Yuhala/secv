/*
 * Created on Wed May 11 2022
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

package com.oracle.truffle.polyt.partitioner;

import org.graalvm.polyglot.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;
import com.oracle.truffle.polyt.partitioner.Partitioner.TransitionType;
import com.oracle.truffle.polyt.utils.Logger;
import com.oracle.truffle.api.nodes.Node;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

// python
import com.oracle.graal.python.builtins.objects.PNone;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the full structure of a function object used by the polytaint partitioner.
 * This structure comprises: signature: function name, runtime argument types (for dynamically typed
 * languages), return type and the corresponding AST source section
 */
public class PolyTaintFunction {

    private String functionName;
    private List<String> argumentTypes;
    private boolean isMainSymbol;
    private FunctionType functionType;
    private Node astNode;
    private String returnType = StringConstants.VOID_RET;
    private static final String unknownType = "unknown";
    private String sourceBody;

    public PolyTaintFunction(String funcName, FunctionType type, Node node,
            List<String> paramTypes) {
        this.functionName = funcName;
        this.argumentTypes = paramTypes;
        this.functionType = type;
        this.astNode = node;
        this.isMainSymbol = false;
    }

    public void setIsMainSymbol(boolean val) {
        this.isMainSymbol = val;
    }

    public void setASTNode(Node node) {
        this.astNode = node;
    }

    public void setSourceBody(String source) {
        this.sourceBody = source;
    }

    public void setParamTypes(List<String> paramTypes) {
        // Logger.log("setting param types for : " + this.functionName);
        this.argumentTypes = new ArrayList<String>(paramTypes);
        // Logger.log("after set size: " + this.argumentTypes.size());
        // for (int i = 0; i < this.argumentTypes.size(); i++) {
        // Logger.log("Type " + i + " = " + this.argumentTypes.get(i));
        // }
    }

    public String getSourceBody() {
        return this.sourceBody;
    }

    public boolean getIsMainSymbol() {
        return this.isMainSymbol;
    }

    public List<String> getArgumentTypes() {
        return this.argumentTypes;
    }

    public void setFunctionType(FunctionType type) {
        this.functionType = type;
    }

    public FunctionType getFunctionType() {
        return this.functionType;
    }

    public String getReturnType() {
        return this.returnType;
    }

    public void setReturnType(String type) {
        this.returnType = type;
    }

    public String getFunctionName() {
        return this.functionName;
    }

    /*
     * Returns the simple name corresponding to the function. The logic in here follows the naming
     * conventions I use in the polytaint code. That is, every function in a file has fullname:
     * filename.ext.funcName and the corresponding simplename is: funcName
     */
    public String getFuncSimpleName() {

        String fullName = this.getFunctionName();
        String simpleName = fullName.substring(fullName.lastIndexOf('.') + 1);
        return simpleName;
    }

    public Node getAstNode() {
        return this.astNode;
    }

    public int getArgCount() {
        return this.argumentTypes.size();
    }

    /**
     * prints the different param types taken by this function at runtime. This signature could
     * change depending on different runtime inputs.
     */
    public String getSignature() {
        String signature = "(";
        String sep = ",";
        String type;

        for (int i = 0; i < this.argumentTypes.size(); i++) {
            if (i == this.argumentTypes.size() - 1) {
                sep = "";
            }
            type = this.argumentTypes.get(i);
            signature += type + sep;
        }

        signature += ")";
        return signature;
    }

    /**
     * Get the corresponding code for the method definition
     * 
     * @return
     */
    public String getMethodDefition() {
        String source = astNode.getSourceSection().getCharacters().toString();
        return source;
    }

    /**
     * Get the runtime type of a specific argument. This information will be used when creating the
     * native image entry point in the partitioning sub-module.
     */
    public static String getType(Object var) {
        String type = unknownType;

        // Logger.log("getType for var: " + var.toString());

        if ((var instanceof Integer)) {
            type = "int";
        } else if (var instanceof Float) {
            type = "float";
        } else if (var instanceof Double) {
            type = "double";
        } else if (var instanceof Boolean) {
            type = "boolean";
        } else if (var instanceof Long) {
            type = "long";
        } else if (var instanceof Short) {
            type = "short";
        }
        // for python
        else if (var instanceof PNone) {
            type = "";
        }
        if (type.equals(unknownType)) {

            System.out.println("unknown type for:" + var + " getClass: " + var.getClass());
            type = "double";

        }
        return type;
    }

    /**
     * A Truffle polyglot context returns a Value object. This should be converted to the
     * appropriate type if need be.
     * 
     * @return
     */
    public String getContextRet() {
        String ret = "";
        String retType = this.returnType;

        if (retType.equals("bool")) {
            ret = "asBoolean()";
        } else if (retType.equals("byte")) {
            ret = "asByte()";
        } else if (retType.equals("short")) {
            ret = "asShort()";
        } else if (retType.equals("float")) {
            ret = "asFloat()";
        } else if (retType.equals("int")) {
            ret = "asInt()";
        } else if (retType.equals("long")) {
            ret = "asLong()";
        } else if (retType.equals("double")) {
            ret = "asDouble()";
        } else {
            // return long if all tests fail
            ret = "asLong()";
        }
        return ret;
    }

    /**
     * Get the appropriate type for a returned value.
     * 
     * @param result
     * @return
     */
    public static String getReturnType(Object result) {

        if (result == null) {
            return "void";
        }
        String res = result.toString();

        String retType = "unknown";
        if (res.contains("<undefined>") || res.contains("None") || res == "") {
            // for JS
            retType = "void";
            // TODO: for ruby
        } else {
            retType = getType(result);
        }
        // Logger.log("getReturnType: result class = " + retType);
        return retType;
    }

    /**
     * Returns comma separated list of parameter types For example: ["int","boolean"]
     * 
     * @return
     */
    public String getParamTypes() {
        String typeList = "[";
        String sep = ",";
        for (int i = 0; i < argumentTypes.size(); i++) {
            if (i == argumentTypes.size() - 1) {
                sep = "";
            }
            typeList += "\"" + argumentTypes.get(i) + "\"" + sep;
        }
        typeList += "]";
        return typeList;
    }

    /**
     * Returns a string with the param definitions
     * 
     * @param isGraalEntryPoint
     * @return
     */
    public String getParamSignature(boolean isGraalEntryPoint) {
        String paramSignature = isGraalEntryPoint ? "(IsolateThread thread" : "(";

        if (this.argumentTypes.size() == 0) {
            paramSignature += ")";
        } else {

            for (int i = 0; i < argumentTypes.size(); i++) {
                if (i == 0 && !isGraalEntryPoint) {
                    paramSignature += argumentTypes.get(i) + " param" + (i + 1);
                } else {
                    paramSignature += ", " + argumentTypes.get(i) + " param" + (i + 1);
                }
            }
            // close parentheses
            paramSignature += ")";
        }

        return paramSignature;
    }

    /**
     * Returns the param class name for the function: Example: file.js.funcA has param class name:
     * Param_funcA
     * 
     * @param func
     * @return
     */
    public String getParamClassName() {
        return "Param_" + this.getFuncSimpleName();
    }

    /**
     * Returns a string corresponding to a call of this function with its params. For example:
     * (param1,param2)
     * 
     * @return
     */
    public String getCallInvocation() {
        String callInvoation = "(";

        if (this.argumentTypes.size() == 0) {
            callInvoation += ")";
        } else {

            for (int i = 0; i < argumentTypes.size(); i++) {
                if (i == 0) {
                    callInvoation += "param" + (i + 1);
                } else {
                    callInvoation += ", " + "param" + (i + 1);
                }
            }
            // close parentheses
            callInvoation += ")";
        }

        return callInvoation;
    }

    /**
     * Returns a string corresponding to a call of this function with its params. For example:
     * (param1,param2)
     * 
     * @return
     */
    public String getEntryInvocation(String isolate) {
        String callInvocation = "(" + isolate;

        if (this.argumentTypes.size() == 0) {
            callInvocation += ")";
        } else {

            for (int i = 0; i < argumentTypes.size(); i++) {

                callInvocation += ", " + "param" + (i + 1);

            }
            // close parentheses
            callInvocation += ")";
        }

        return callInvocation;
    }

    /**
     * Returns a string corresponding to a call invocation of the corresponding ecall/ocall
     * transition. For example: ocall_funcA(&ret,param1,param2) or ecall_funcB(global_eid, param)
     * 
     * @return
     */
    public String getTransInvocation(boolean isEcall) {
        String callInvocation = isEcall ? "(global_eid" : "(";
        if (!returnType.equals("void")) {
            if (isEcall) {
                callInvocation += ",&ret";
            } else {
                callInvocation += "&ret";
            }
        }

        if (this.argumentTypes.size() == 0) {
            callInvocation += ")";
        } else {

            for (int i = 0; i < argumentTypes.size(); i++) {
                if (i == 0) {

                    if (isEcall || !returnType.equals("void")) {
                        /**
                         * we already have the first param (global_eid) or ret type; so add a comma
                         */
                        callInvocation += ", " + "param" + (i + 1);
                    } else {
                        callInvocation += "param" + (i + 1);
                    }
                } else {
                    callInvocation += ", " + "param" + (i + 1);
                }
            }
            // close parentheses
            callInvocation += ")";
        }

        return callInvocation;
    }

}
