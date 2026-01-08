/*
 * Created on Wed May 11 2022
 *
 * 
 * The MIT License (MIT)
 * Copyright (c) 2022 Peterson Yuhala, Institut d'Informatique Université de Neuchâtel (IIUN)
 *
 * NB: I really do not know how to handle copyright texts :(-- Peterson Yuhala
 * 
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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

package com.oracle.truffle.polyt.partitioner;

import org.graalvm.polyglot.Source;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CodeWriter {

    private static final String INDENT4 = "    ";
    private final List<String> lines;
    private final StringBuilder currentLine;
    private int indentLevel = 0;

    public CodeWriter() {
        this.lines = new ArrayList<>();
        this.currentLine = new StringBuilder(100);
    }

    /**
     * Write results to output file
     * 
     * @param fileName
     * @param dirPath
     * @return
     */
    public Path writeFile(String fileName, Path dirPath) {
        assert currentLine.length() == 0 : "last line not finished";

        Path outputFile = dirPath.resolve(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException ex) {
            System.out.println("Failed to write to output file");
        }
        return outputFile;
    }

    public void indent() {
        indentLevel++;
    }

    public void outdent() {
        indentLevel--;
    }

    public void appendln(String str) {
        append(str);
        appendln();
    }

    public void appendln() {
        assert currentLine.indexOf("\n") == -1 : "line must not contain newline character";
        lines.add(currentLine.toString());
        currentLine.delete(0, currentLine.length());
    }

    public CodeWriter append(String str) {
        assert !str.contains("\n") : "line must not contain newline character";
        currentLine.append(str);
        return this;

    }

    public CodeWriter indents() {
        assert currentLine.length() == 0 : "indenting in the middle of a line";
        for (int i = 0; i < indentLevel; i++) {
            append(INDENT4);
        }
        return this;

    }

    public void clearWriter() {
        /**
         * This clear causes issues. 
         * TODO
         */
        // this.lines.clear();

    }

}
