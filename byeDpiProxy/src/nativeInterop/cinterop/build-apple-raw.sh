#!/bin/bash

set -e

# buildStatic iphoneos -mios-version-min=15.0 arm64
buildStatic()
{
     echo "build for $1, $2, min version $3"

     local MIN_VERSION="-m$1-version-min=$3"
#     make static
     make PP="xcrun --sdk $1 --toolchain $1 clang" \
          CC="xcrun --sdk $1 --toolchain $1 clang" \
          CFLAGS="-arch $2 $MIN_VERSION" \
          LFLAGS="-arch $2 $MIN_VERSION -Wl,-Bsymbolic-functions" static

     local OUTPUT_DIR="../bin_$1_$2"
     mkdir -p $OUTPUT_DIR
     local OUTPUT_ARCH_FILE="$OUTPUT_DIR/libciadpi.a"

     libtool -static -o $OUTPUT_ARCH_FILE libciadpi.a
     make clean
}

cd byedpi

buildStatic macosx x86_64 10.14
buildStatic macosx arm64 10.14
buildStatic iphoneos arm64 15.0
buildStatic iphonesimulator arm64 15.0