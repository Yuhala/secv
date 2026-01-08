/*
 * Created on Wed Mar 09 2022
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

package com.oracle.truffle.polyt;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

import java.util.Map;
import java.util.Iterator;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyt.partitioner.PType;
import com.oracle.truffle.polyt.partitioner.Partitioner;
import com.oracle.truffle.polyt.partitioner.PolyTaintFunction;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;

import com.oracle.truffle.polyt.utils.Logger;

/**
 * The taint tracker does just that: tracks all the taint information: i.e tainted/coloured/trusted
 * functions and variables, neutral functions and untrusted functions.
 */
public final class TaintTracker {

    private final Set<SourceSection> loaded = new HashSet<>();
    private final Set<SourceSection> tainted = new HashSet<>();

    /**
     * Stores all application-specific functions/methods that have been tainted as well as the
     * corresponding nodes; the latter will be used to obtain useful info such as the corresponding
     * source text etc
     */
    private final Map<String, PolyTaintFunction> taintedMethods =
            new HashMap<String, PolyTaintFunction>();

    /**
     * Neutral functions can be included wholy in or out of the enclave runtime. They do not
     * explicitly instantiate secure types but could take a param at runtime which is a secure type
     * or not (e.g secure int, regular int)
     */
    private final Map<String, PolyTaintFunction> neutralMethods =
            new HashMap<String, PolyTaintFunction>();

    /**
     * Untrusted methods will be included outside only; they don't instantiate secure variables
     * explicitly nor call secure functions.
     */
    private final Map<String, PolyTaintFunction> untrustedMethods =
            new HashMap<String, PolyTaintFunction>();

    /**
     * List of all application functions/methods called/seen at runtime
     */

    private final Map<String, PolyTaintFunction> seenMethods =
            new HashMap<String, PolyTaintFunction>();

    /**
     * Maps function simple names to return values. This helps us to compute an appropriate return
     * type for the function when building the native image java application, which requires static
     * types. The caveat here is we may have different types for different runs.. choose the the
     * type with largest size.
     */
    private final Map<String, String> returnTypes = new HashMap<String, String>();

    /**
     * Stores all application specific variables or object fields that have been tainted. Variable
     * naming follows the following convention: Global variable: sourceFile.variableName Function
     * local variable: sourceFile.FuncName.variableName Class attribute: className.attribName
     */

    private final Map<String, Object> taintedVariables = new HashMap<String, Object>();

    /**
     * Contains all global variables. Trusted globals will be put in enclave only.
     */
    private final Map<String, Object> globalVariables = new HashMap<String, Object>();

    /**
     * Names of functions which will be neutral but we have insufficient information to add them to
     * neutral map. They will be added once actually enter their nodes.
     */
    private final Set<String> unresolvedNeutral = new HashSet<String>();

    private List<PolyTaintFunction> mainFunctions = new ArrayList<PolyTaintFunction>();
    private List<String> mainSources = new ArrayList<String>();

    /**
     * Add a secure/trusted method to the tainted methods set
     * 
     * @param methodName
     */
    public void addTaintedMethod(String methodName, PolyTaintFunction func) {
        // Logger.log("Adding tainted method: " + methodName);
        if (!taintedMethods.containsKey(methodName)) {
            taintedMethods.put(methodName, func);
        }

    }

    /**
     * Analyses a source code snippet to get the function name called, and associates the return
     * type to it. If a new return type found for a different run of the same function, retain the
     * larger type.
     */
    public void addReturnType(String funcName, Object result) {

        /**
         * If void return type, no need for further checks
         */
        String retType = PolyTaintFunction.getReturnType(result);
        if (retType.equals("void")) {
            returnTypes.put(funcName, retType);
        } else {
            // check if a different return type exists
            String prev = returnTypes.get(funcName);
            if (prev != null) {
                // replace return type with larger type
                String max = PType.getLargerType(prev, retType);
                returnTypes.put(funcName, max);
            } else {
                returnTypes.put(funcName, retType);
            }
        }

    }

    public Map<String, String> getReturnTypes() {
        return this.returnTypes;
    }

    public List<PolyTaintFunction> getMainFunctions() {
        return this.mainFunctions;
    }

    public List<String> getMainSources() {
        return this.mainSources;
    }

    /**
     * Add a trusted variable to the tainted variables set
     * 
     * @param variableName
     */
    public void addTaintedVariable(String variableName, Object varObj) {

        Logger.log(">>> ======== >>> adding tainted variable: " + variableName);
        if (!taintedVariables.containsKey(variableName)) {
            taintedVariables.put(variableName, varObj);
        }
    }

    public void addUnresolvedNeutral(String name) {
        Logger.log("xxxxxxxxxxxxxxxxxxx adding unresolved neutral: " + name);
        unresolvedNeutral.add(name);
    }

    /**
     * Add a seen method to the set.
     * 
     * @param variableName
     */
    public void addSeenMethod(String methodName, PolyTaintFunction func) {
        // Logger.log(">>> adding seen method: " + methodName + " >>>>");
        if (!seenMethods.containsKey(methodName)) {
            seenMethods.put(methodName, func);
        }
    }

    /**
     * Add neutral method to the neutral methods set
     * 
     * @param methodName
     */
    public void addNeutralMethod(String methodName, PolyTaintFunction func) {
        Logger.log(">>> adding neutral method: " + methodName + " >>>>");
        if (!neutralMethods.containsKey(methodName)) {
            neutralMethods.put(methodName, func);
        }
    }

    /**
     * Add untrusted method to untrusted methods set
     * 
     * @param methodName
     */
    public void addUntrustedMethod(String methodName, PolyTaintFunction func) {
        // Logger.log(">>> adding trusted method: " + methodName + " >>>>");
        if (!untrustedMethods.containsKey(methodName)) {
            untrustedMethods.put(methodName, func);
        }
    }

    public Map<String, PolyTaintFunction> getTaintedMethods() {
        return this.taintedMethods;
    }

    public Map<String, PolyTaintFunction> getSeenMethods() {
        return this.seenMethods;
    }

    public PolyTaintFunction getSeenMethod(String name) {
        return this.seenMethods.get(name);
    }

    public Map<String, PolyTaintFunction> getNeutralMethods() {
        return this.neutralMethods;
    }

    public Map<String, PolyTaintFunction> getUntrustedMethods() {
        return this.untrustedMethods;
    }

    public void addTainted(SourceSection sourceSection) {
        tainted.add(sourceSection);
    }

    public void addLoaded(SourceSection sourceSection) {
        loaded.add(sourceSection);
    }

    public Map<String, Object> getTaintedVariables() {
        return this.taintedVariables;
    }

    /**
     * Check if the variable with the name is present in the set of tainted variables
     * 
     * @param name
     * @return
     */
    public boolean isTaintedVariable(String varName) {
        return taintedVariables.containsKey(varName);
    }

    /**
     * Check if the method with the name is present in the set of tainted methods
     * 
     * @param name
     * @return
     */
    public boolean isTaintedMethod(String methodName) {
        return taintedMethods.containsKey(methodName);
    }

    public void addMainFunction(PolyTaintFunction func) {
        mainFunctions.add(func);
    }

    public void addMainSource(String src) {
        mainSources.add(src);
    }

    /**
     * Print the source code corresponding to the method definition
     * 
     * @param methods
     */
    public void printMethodSources(Map<String, PolyTaintFunction> methods) {
        Iterator it = methods.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            PolyTaintFunction func = (PolyTaintFunction) pair.getValue();
            System.out.println(func.getMethodDefition());
        }
    }

    /**
     * Classify all seen methods into their right categories
     */
    public void classifyMethods() {
        Iterator it = seenMethods.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            PolyTaintFunction func = (PolyTaintFunction) pair.getValue();
            FunctionType type = func.getFunctionType();
            String name = func.getFunctionName();
            if (isDummyMethod(name)) {
                seenMethods.remove(name);
                continue;
            }
            switch (type) {
                case TRUSTED:
                    addTaintedMethod(name, func);
                    break;
                case NEUTRAL:
                    addNeutralMethod(name, func);
                    break;
                case UNKNOWN:
                    addUntrustedMethod(name, func);
                    break;
                default:
                    addUntrustedMethod(name, func);
                    break;
            }

        }
        Logger.log("xxxxxxx Trusted methods: " + taintedMethods.size() + " Neutral methods: "
                + neutralMethods.size() + " Untrusted methods: " + untrustedMethods.size());
    }

    /**
     * Our analysis gave us hints on which functions could be neutral. We could not resolve them yet
     * because we simply parsed the source code and couldn't create the corresponding PolyFunction
     * object. However these functions were most probably called at some point; this function
     * actually tags these "unresolved" neutral functions as neutral.
     */
    public void resolveNeutralFunctions() {
        System.out.println("**************** resolving neutral funcs ****************");

        // analyzeSourcesForNeutral();
        for (String func : unresolvedNeutral) {
            if (seenMethods.containsKey(func)) {
                taintFunction(seenMethods.get(func), FunctionType.NEUTRAL);
            }
        }

    }

    /**
     * Analyze the sources of all seen functions to obtain function calls
     */
    public void analyzeSourcesForNeutral() {

    }

    /**
     * Analyse return sources and assign appropriate return types to each function. This should be
     * done at the end of taint tracking.
     */
    public void resolveReturnTypes(String sourceFile) {
        Iterator it = seenMethods.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            PolyTaintFunction func = (PolyTaintFunction) pair.getValue();
            String retType = returnTypes.get(func.getFunctionName());
            if (retType != null) {
                func.setReturnType(retType);
            } else {
                func.setReturnType("void");
            }
        }
    }

    /**
     * Prints methods of the methodType
     * 
     * @param methodType
     */
    public void printMethods(FunctionType methodType) {
        String title = "";
        Iterator it = seenMethods.entrySet().iterator();
        boolean printSeen = false;

        switch (methodType) {
            case TRUSTED:
                title = "---------------- TRUSTED methods ----------------";

                break;
            case NEUTRAL:
                title = "---------------- NEUTRAL methods ----------------";

                break;
            case UNKNOWN:
                title = "---------------- UNCLASSIFIED/UNTRUSTED methods ----------------";

                break;
            default:
                title = "---------------- SEEN methods ----------------";
                printSeen = true;
                break;
        }

        System.out.println(title);

        if (printSeen) {
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                PolyTaintFunction func = (PolyTaintFunction) pair.getValue();
                System.out.print(pair.getKey());
                System.out.println(
                        " retType: " + func.getReturnType() + " sig: " + func.getSignature());

            }
            return;
        }

        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            PolyTaintFunction func = (PolyTaintFunction) pair.getValue();
            if (func.getFunctionType() == methodType) {
                System.out.print(pair.getKey());
                System.out.println(
                        " retType: " + func.getReturnType() + " sig: " + func.getSignature());
            }
        }

    }

    /**
     * Adds a taint label to a function: TRUSTED functions will always stay the same i.e cannot be
     * downmoted .. this is the strongest taint label. NEUTRAL and UNKNOWN can be promoted to
     * TRUSTED UNKNOWN can be promoted to NEUTRAL
     * 
     * @param func
     * @param type
     */
    public static void taintFunction(PolyTaintFunction func, FunctionType type) {
        switch (func.getFunctionType()) {
            case TRUSTED:

                break;
            case NEUTRAL:
                if (type != FunctionType.UNKNOWN) {
                    func.setFunctionType(type);
                }
                break;

            case UNKNOWN:
            case UNTRUSTED:
                func.setFunctionType(type);
                break;

            default:
                break;
        }
    }

    /**
     * Tests for dummy methods or roots which represent the global method
     * 
     * @param name
     * @return
     */
    public boolean isDummyMethod(String name) {
        boolean isDummy = false;
        // for JS methods
        if (name.contains(":program")) {
            isDummy = true;
        }
        // for Ruby methods etc
        return isDummy;
    }

    public void printTaintedVariables() {
        System.out.println("---------------- List of tainted variables ----------------");
        Iterator it = taintedVariables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            System.out.println(pair.getKey());
        }
    }

    private Set<SourceSection> nonCoveredSections() {
        final HashSet<SourceSection> nonCovered = new HashSet<>();
        nonCovered.addAll(loaded);
        nonCovered.removeAll(tainted);
        return nonCovered;
    }

    Set<Integer> nonCoveredLineNumbers() {
        Set<Integer> linesNotCovered = new HashSet<>();
        for (SourceSection ss : nonCoveredSections()) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                linesNotCovered.add(i);
            }
        }
        return linesNotCovered;
    }

    Set<Integer> loadedLineNumbers() {
        Set<Integer> loadedLines = new HashSet<>();
        for (SourceSection ss : loaded) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                loadedLines.add(i);
            }
        }
        return loadedLines;
    }

}
