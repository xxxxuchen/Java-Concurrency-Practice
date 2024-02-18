#!/bin/bash

NUM_RUNS=15

javac q2.java

# Run the Java program in a loop
for ((i=1; i<=$NUM_RUNS; i++)); do
    echo "$i time:"
    java q2
done

rm q2.class
