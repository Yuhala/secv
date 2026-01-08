
/*
 * Created on Tue June 7 2022
 *
 * The MIT License (MIT)
 * Copyright (c) 2022 Peterson Yuhala, Institut d'Informatique Université de Neuchâtel (IIUN)
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

public enum PType {

    BOOLEAN(0, "bool"),
    BYTE(1, "byte"),
    SHORT(2, "short"),
    CHAR(2, "char"),
    FLOAT(4, "float"),
    INT(4, "int"),
    LONG(8, "long"),
    DOUBLE(8, "double");

    public int size;// number of byres
    public String name;// type name

    PType(int numBytes, String name) {
        this.size = numBytes;
        this.name = name;
    }

    public int getSize() {
        return this.size;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Return corresponding PType for a given typename
     * 
     * @param name
     * @return
     */
    public static PType getPType(String name) {
        for (PType pType : PType.values()) {
            if (pType.getName().equals(name)) {
                return pType;
            }
        }
        // return largest type if nothing found
        return LONG;
    }

    /*
     * Returns the type name with the larger size
     */
    public static String getMax(PType typeA, PType typeB) {
        if (typeA.getSize() > typeB.getSize()) {
            return typeA.getName();
        } else {
            return typeB.getName();
        }
    }

    /**
     * Compares two types and returns the larger one.
     * The type enum contains types in order of their size.
     * 
     * @return
     */
    public static String getLargerType(String previous, String current) {
        return getMax(getPType(previous), getPType(current));
    }
}