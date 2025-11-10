#!/bin/bash

set -e

buildStatic()
{
     echo "build for $1, $2"
     make LFLAGS="-Wl,-Bsymbolic-functions" static

     local OUTPUT_DIR="../bin_$1_$2"
     mkdir -p $OUTPUT_DIR
     local OUTPUT_ARCH_FILE="$OUTPUT_DIR/libciadpi.a"

     ar rcs $OUTPUT_ARCH_FILE libciadpi.a
     make clean
}

cd byedpi

buildStatic linux x86_64
buildStatic linux arm64