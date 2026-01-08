/*
 * Created on Wed Mar 09 2022
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
package com.oracle.truffle.polyt;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.Instrumenter;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyt.factory.PolyTaintEventFactory;
import com.oracle.truffle.polyt.partitioner.Partitioner;
import com.oracle.truffle.polyt.partitioner.Partitioner.FunctionType;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;

import com.oracle.truffle.js.nodes.instrumentation.JSTags;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.FunctionCallTag;
import com.oracle.truffle.polyt.TaintTracker;
import com.oracle.truffle.polyt.utils.Logger;

// Graph coloring problem: control flow graphs.

/**
 * pyuhala PolyTaint is a Truffle instrumentation tool for taint analysis and partitioning of
 * polyglot applications for Intel SGX enclaves. Polytaint leverages the use of secureL types to
 * figure out which methods should be included inside the enclave and which to keep out. The final
 * results are then used by the partitioning module of polytaint to build a trusted and untrusted
 * native image Java application which will be compiled to machine code in substratevm.
 * 
 */
@Registration(id = PolyTaintInstrument.ID, name = "Polyglot Taint", version = "0.1",
                services = PolyTaintInstrument.class)
public final class PolyTaintInstrument extends TruffleInstrument {

        @Option(name = "", help = "Enable Polytaint tool (default: true).",
                        category = OptionCategory.USER, stability = OptionStability.STABLE)
        public static final OptionKey<Boolean> ENABLED = new OptionKey<>(true);

        @Option(name = "PrintCoverage",
                        help = "Print coverage to stdout on process exit (default: true).",
                        category = OptionCategory.USER, stability = OptionStability.STABLE)
        public static final OptionKey<Boolean> PRINT_COVERAGE = new OptionKey<>(true);

        @Option(name = "PrimaryGuest", help = "GuestLanguage (default: js).",
                        category = OptionCategory.USER, stability = OptionStability.STABLE)
        public static final OptionKey<String> PRIMARY_GUEST = new OptionKey<>("");

        @Option(name = "NativeImageKind",
                        help = "Native Image Kind  (full or partitioned: default: part).",
                        category = OptionCategory.USER, stability = OptionStability.STABLE)
        public static final OptionKey<String> NATIVE_IMG_KIND = new OptionKey<>("part");

        @Option(name = "PrimaryFile", help = "Main file to be executed.",
                        category = OptionCategory.USER, stability = OptionStability.STABLE)
        public static final OptionKey<String> PRIMARY_FILE = new OptionKey<>("");

        @Option(name = "OutputFolder", help = "Folder where generated files will be written to.",
                        category = OptionCategory.USER, stability = OptionStability.STABLE)
        public static final OptionKey<String> OUTPUT_FOLDER = new OptionKey<>("");
        // @formatter:on

        public enum Guest {
                JS, RUBY, PYTHON, UNDEFINED
        };

        public enum BenchType {
                IO, CPU
        };

        public static final String ID = "polytaint";
        public boolean taintTest = false;
        /* primary guest language */
        public static Guest primaryGuest = Guest.UNDEFINED;
        /* Source file name; we deal with single source files for this POC */
        public static String primaryFile;
        /* Guest language ID */
        public static String guestLanguageId;
        /* Output folder for generated files */
        public static String outputFolder;

        public static boolean partitionApp = false;

        public static boolean doBench = false;

        /* partitioner object */
        public Partitioner partitioner;

        /**
         * Global tracker object.
         */
        public TaintTracker tracker = new TaintTracker();

        /**
         * Partitioner
         */
        // public Partitioner partitioner = new Partitioner(tracker);

        private Instrumenter instrumenter;
        private Env instrumentEnv;

        /**
         * Source section filters used by polytaint
         */

        protected static final SourceSectionFilter SECUREL_STATEMENT_FILTER = SourceSectionFilter
                        .newBuilder().tagIs(StatementTag.class, ExpressionTag.class)
                        .sourceIs((s) -> s.getLanguage().equals(SourceFragmentConsts.SECL_ID))
                        .build();

        protected static final SourceSectionFilter WRITE_VAR_TAG_FILTER =
                        SourceSectionFilter.newBuilder().tagIs(WriteVariableTag.class).build();
        protected static final SourceSectionFilter READ_VAR_TAG_FILTER =
                        SourceSectionFilter.newBuilder().tagIs(ReadVariableTag.class).build();

        protected static final SourceSectionFilter JS_EVAL_FILTER =
                        SourceSectionFilter.newBuilder().tagIs(JSTags.EvalCallTag.class).build();

        protected static final SourceSectionFilter JS_FUNCTION_CALL_FILTER = SourceSectionFilter
                        .newBuilder().tagIs(JSTags.FunctionCallTag.class).build();

        protected static final SourceSectionFilter FUNCTION_INVOCATION_FILTER =
                        SourceSectionFilter.newBuilder().tagIs(StandardTags.CallTag.class).build();

        protected static final SourceSectionFilter ROOT_BODY_FILTER = SourceSectionFilter
                        .newBuilder().tagIs(StandardTags.RootBodyTag.class).build();

        protected static final SourceSectionFilter PROPERTY_WRITE_FILTER = SourceSectionFilter
                        .newBuilder().tagIs(JSTags.WritePropertyTag.class).build();

        protected static final SourceSectionFilter ROOT_FILTER =
                        SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build();

        protected static final SourceSectionFilter ROOT_BODY_AND_FUNC_CALL = SourceSectionFilter
                        .newBuilder()
                        .tagIs(StandardTags.RootBodyTag.class, StandardTags.CallTag.class).build();

        // Constructor

        public PolyTaintInstrument() {
                super();
        }

        public Env getEnv() {
                return instrumentEnv;
        }

        public Instrumenter getInstrumenter() {
                return instrumenter;
        }

        @Override
        protected void onCreate(final Env env) {
                Logger.log("--------------->> Polytaint Instrument created");
                this.instrumentEnv = env;
                this.instrumenter = env.getInstrumenter();
                final OptionValues options = env.getOptions();

                primaryFile = PRIMARY_FILE.getValue(options);
                guestLanguageId = PRIMARY_GUEST.getValue(options);
                outputFolder = OUTPUT_FOLDER.getValue(options);

                setGuestLanguage(guestLanguageId);
                partitioner = new Partitioner(primaryFile, guestLanguageId, outputFolder);

                boolean isEnabled = true;
                // ENABLED.getValue(options);
                String imageKind = NATIVE_IMG_KIND.getValue(options);
                boolean isFull = imageKind.equals("full");

                Logger.log("------------------ Instrumentation Information -----------------");
                Logger.log("ImakeKind: " + imageKind);
                Logger.log("Primary File: " + primaryFile);
                Logger.log("Guest language: " + guestLanguageId);
                Logger.log("################################################################");



                if (isFull) {
                        partitioner.buildFullNativeImage();
                } else {
                        partitionApp = true;
                }



                /**
                 * Run dynamic analysis only for programs to be partitioned.
                 */
                env.registerService(this);
                if (isEnabled && !isFull) {
                        enable(env);
                }

        }

        public void setGuestLanguage(String langId) {

                if (langId.equals("js")) {
                        primaryGuest = Guest.JS;
                } else if (guestLanguageId.equals("python")) {
                        primaryGuest = Guest.PYTHON;
                }

        }

        /**
         * 
         *
         * @param env
         */
        @Override
        protected void onDispose(Env env) {

                // tracker.printMethodSources(tracker.getTaintedMethods());

                // partitionApp = false;


                if (partitionApp) {
                        tracker.resolveReturnTypes(primaryFile);
                        tracker.resolveNeutralFunctions();
                        tracker.classifyMethods();

                        tracker.printMethods(FunctionType.TRUSTED);
                        tracker.printMethods(FunctionType.NEUTRAL);
                        tracker.printMethods(FunctionType.UNKNOWN);
                        tracker.printMethods(FunctionType.ALL);

                        tracker.printTaintedVariables();
                        Partitioner partitioner = new Partitioner(tracker, primaryFile,
                                        guestLanguageId, outputFolder);
                        // partitioner.printMainSource();
                        partitioner.partitionApplication();
                }

        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
                return new PolyTaintInstrumentOptionDescriptors();
        }

        private void enable(final Env env) {

                /**
                 * pyuhala: We create a filter to monitor events which define taint sources (e.g
                 * secureL polyglot instantiation), or that can propagate tainted values across the
                 * program (e.g function calls, var writes, etc)
                 */
                Logger.log(">>>>>>>>>>>>>>>>>>>> enabling polytaint instrument event filters");
                if (guestLanguageId.equals("js")) {

                        instrumenter.attachExecutionEventFactory(WRITE_VAR_TAG_FILTER,
                                        new PolyTaintEventFactory(this,
                                                        EventEnum.GENERIC_VAR_WRITE));
                        // instrumenter.attachExecutionEventFactory(READ_VAR_TAG_FILTER,
                        // new PolyTaintEventFactory(this, EventEnum.GENERIC_VAR_READ));
                        instrumenter.attachExecutionEventFactory(JS_EVAL_FILTER,
                                        new PolyTaintEventFactory(this, EventEnum.JS_EVAL));
                        instrumenter.attachExecutionEventFactory(ROOT_BODY_FILTER,
                                        new PolyTaintEventFactory(this, EventEnum.ROOT_BODY));

                        instrumenter.attachExecutionEventFactory(FUNCTION_INVOCATION_FILTER,
                                        new PolyTaintEventFactory(this, EventEnum.FUNC_INVOKE));

                        // instrumenter.attachExecutionEventFactory(PROPERTY_WRITE_FILTER,
                        // new PolyTaintEventFactory(this, EventEnum.PROPERTY_WRITE));

                        // instrumenter.attachLoadSourceSectionListener(SECUREL_STATEMENT_FILTER,
                        // new GatherSourceSectionsListener(this),
                        // true);
                } else if (guestLanguageId.equals("python")) {
                        instrumenter.attachExecutionEventFactory(WRITE_VAR_TAG_FILTER,
                                        new PolyTaintEventFactory(this,
                                                        EventEnum.GENERIC_VAR_WRITE));
                        // instrumenter.attachExecutionEventFactory(READ_VAR_TAG_FILTER,
                        // new PolyTaintEventFactory(this, EventEnum.GENERIC_VAR_READ));

                        instrumenter.attachExecutionEventFactory(ROOT_BODY_FILTER,
                                        new PolyTaintEventFactory(this, EventEnum.ROOT_BODY));

                        instrumenter.attachExecutionEventFactory(FUNCTION_INVOCATION_FILTER,
                                        new PolyTaintEventFactory(this, EventEnum.FUNC_INVOKE));

                        // instrumenter.attachExecutionEventFactory(ROOT_FILTER,
                        // new PolyTaintEventFactory(this, EventEnum.ROOT_TAG));

                        instrumenter.attachLoadSourceSectionListener(SECUREL_STATEMENT_FILTER,
                                        new GatherSourceSectionsListener(this), true);
                }

        }



}
