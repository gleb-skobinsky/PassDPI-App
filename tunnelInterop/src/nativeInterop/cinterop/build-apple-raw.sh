#!/bin/bash

set -e

# buildStatic iphoneos -mios-version-min=15.0 arm64
buildStatic()
{
     echo "build for $1, $2, min version $3"

     local MIN_VERSION="-m$1-version-min=$3"
     make PP="xcrun --sdk $1 --toolchain $1 clang" \
          CC="xcrun --sdk $1 --toolchain $1 clang" \
          CFLAGS="-arch $2 $MIN_VERSION" \
          LFLAGS="-arch $2 $MIN_VERSION -Wl,-Bsymbolic-functions" static

     local OUTPUT_DIR="../bin_$1_$2"
     mkdir -p $OUTPUT_DIR
     local OUTPUT_ARCH_FILE="$OUTPUT_DIR/libhev-socks5-tunnel.a"

     libtool -static -o $OUTPUT_ARCH_FILE \
                   bin/libhev-socks5-tunnel.a \
                   third-part/lwip/bin/liblwip.a \
                   third-part/yaml/bin/libyaml.a \
                   third-part/hev-task-system/bin/libhev-task-system.a
     make clean
}

cd hev-socks5-tunnel

buildStatic macosx x86_64 10.14
buildStatic macosx arm64 10.14
buildStatic iphoneos arm64 15.0
buildStatic iphonesimulator x86_64 15.0
buildStatic iphonesimulator arm64 15.0