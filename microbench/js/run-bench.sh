#!/bin/bash

#
# PYuhala: script to calculate run time of polyglot
# program with and w/o instrumentation
#

base=$PWD


myCmd="js --polyglot --jvm "
cmdjs2="./runTaintTrack.sh js "

myCmdxx="graalpython --polyglot --jvm "
cmdjs2="./runTaintTrack.sh python "



results_file="$base/results.csv"
num_runs=10

avg_time() {
    #
    # usage: avg_time n command ...
    #
    name=$2;
    prog_file=$1;
    n=$num_runs; shift
    (($# > 0)) || return                   # bail if no command given
    for ((i = 0; i < n; i++)); do
        { time -p $myCmd $prog_file; } 2>&1 # ignore the output of the command
        # but collect time's output in stdout
    done | awk '
        /real/ { real = real + $2; nr++ }
        /user/ { user = user + $2; nu++ }
        /sys/  { sys  = sys  + $2; ns++}
        END    {
                 if (nr>0) printf("%f\n", real/nr);
    }' > val.txt
    
    read -r result < "val.txt"
    echo "$name,$result" >> $results_file
}



bench_file=$1
avg_time $bench_file $1

