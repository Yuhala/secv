#!/bin/bash

#
# PYuhala: script to delete all files generated for 
# synthetic benchmark
#

bench_path=$PWD
js_wildcard="bench-*.js"
py_wildcard="bench-*.py"

js_files=$bench_path/$js_wildcard
py_files=$bench_path/$py_wildcard

rm $js_files $py_files