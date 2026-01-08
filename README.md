# Polytaint tool.

- `Polytaint` tool is a Truffle instrumentation taint tracking tool which analyses Polyglot programs to obtain source sections (i.e methods/functions) which access secure types defined by the `SecureL Truffle language implementation`.

- Polytaint instruments mainly function call, variable write and variable read nodes to track the desired tainted/trusted functions.
- The results of the run time analysis are then transfered to the `Partitioning` module, which partitions the application into two parts: `Trusted` java native image application which exposes the trusted functions as GraalVM entry points, and an `Untrusted` native image java applications which exposes the functions/methods which do not access secure types.
- These images are then build with GraalVM native image tool and used to build the final Intel SGX application.

- More info on the inner workings of this tool will be provided later on.

## How to analyse programs with Polytaint.

- Polytaint analyses programs in two modes: `full` and `part`.
- With `full` mode no instrumentation is done. A single java program/file is created: `Trusted.java` in the `output` folder. This java program simply runs the full guest language code snippet using the Java polyglot API. This Java file is copied to `substratevm` where it is used to create a native image which will run entirely inside an SGX enclave.
- With `part` mode the corresponding program (e.g JS, Python) is run on the JVM and instrumented accordingly. That is, program variables and functions/methods accessing/modifying `SecureL` types are registered as the program runs. Analysis divides functions into 3 categories:

  1.**_Trusted functions_**: functions which explicitly manipulate secL types or takes secure variables as parameters. NB: A secure variable is either a variable which recieves its variable directly from a `secL` scope via the polyglot API, or one whose value originates implicitly from another secL variable. Trusted functions will be exported as `static Java methods` in `Trusted.java`. They will have proxies in `Untrusted.java` which will perform Intel SGX transitions.

  2.**_Untrusted functions_**: functions which do not manipulate secure variables explicitly nor take them as input. These will be exported as `static Java methods` in `Untrusted.java`. The primary argument for this design choice is TCB reduction. They will have proxies in `Trusted.java` which perform Intel SGX transitions.

  3.**_Neutral functions_**: functions which do not explicitly declare/manipulate secure types in their body but take them as input. They are exported fully in both `Trusted.java` and `Untrusted.java`. The primary argument for this design choice is security (no secure type should be sent out) and performance (no transition needed).

At the end of analysis, the analysis results (i.e trusted, untrusted, neutral functions) are sent to the `partitioning module` which then creates 2 Java files: `Trusted.java` and `Untrusted.java` as well as other useful files (C/C++ headers, EDL files etc) to be used by the `sgx module` to build the final SGX applications. These Java files have the corresponding guest language functions exported as Java methods using the Polyglot API.

- The Java files are copied to `substratevm` and used to build two native images which will run inside (using `Trusted.java`) and outside the enclave (using `Untrusted.java`).

## Running the tool

- To run the `polytaint` tool, use the `runTaintTrack.sh` script as such: `./runTaintTrack.sh <guestLanguage> <programFile> <imageType>`.

- To partition a program on the other hand, we will run taint tracking with `imageType = part`. For example:

```
./runTaintTrack.sh js polyglot.js part

```

- The above command will analyse the program in `polyglot.js` with our `polytaint` instrumentation tool and determine which functions will be `Trusted`, `Untrusted` and `Neutral`. The result of this analysis is printed at the end of the analysis. The two Java programs are then created as described above and the files are copied to `substratevm` in the `graal` folder. Other files are also created as already explained and moved to the appropriate module.
- To build the partitioned native image, move to the `graal` folder and run the script: `./build_polytaint_images.sh`. This will build two relocatable `.o` images corresponding to `Trusted.java` native image and `Untrusted.java` native image. These object files are moved to the `sgx-module` in the `sgx` folder.
- To build the corresponding SGX application, move to the `sgx` folder and run `make clean && make`. Run the program with `./app`.

- To run a the program in `polyglot.js` entirely inside the enclave, we don't need to partition the program. So we use the command:

```
./runTaintTrack.sh js polyglot.js full

```

- This will skip taint analysis and just create a single Java application with the JavaScript program embedded. To build the corresponding native image move into the `graal` folder and run: `./build_full_poly_image.sh`. A native image relocatable file `main.o` will be created and moved to the `sgx module`.
- Similarly, to build the corresponding SGX application, move to the `sgx` folder and run `make clean && make`. Run the program with `./app`.

## MISC information

- The partitioning tool uses regular `expressions` parse out function defitions. For JS code, this is more or less feasible. However for Python source code, it is very difficult to extract the function body with a regex. To simplify the process, we introduce a `magic string`: `func_end=1` at the end of python function definitions. This is a correct python expression but we are not interested in its result. It simply makes things easier for us when parsing out function definitions. This expression will be removed in the final partitioned program.

## Possible bugs

- If you install a language locally with `gu` and use the `-f` (force) option to prevent version checks, this may lead to the polyglot API not detecting the language in the list of languages even though `gu list` lists the language as installed. This is a weird issue that I'm yet to understand. Nonetheless all works correctly when the language (e.g secureL) is installed with: `gu install -L secL-component.jar`.
