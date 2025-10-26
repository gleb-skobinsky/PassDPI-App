#!/bin/bash

set -e

buildStatic()
{
     echo "build for $1, $2"
     make CC=gcc AR=ar LFLAGS="-Wl,-Bsymbolic-functions" static

     local OUTPUT_DIR="../bin_$1_$2"
     mkdir -p $OUTPUT_DIR
     local OUTPUT_ARCH_FILE="$OUTPUT_DIR/libhev-socks5-tunnel.a"

     ar rcs $OUTPUT_ARCH_FILE \
                   bin/libhev-socks5-tunnel.a \
                   third-part/lwip/bin/liblwip.a \
                   third-part/yaml/bin/libyaml.a \
                   third-part/hev-task-system/bin/libhev-task-system.a
     make clean
}

cd hev-socks5-tunnel

buildStatic mingw x86_64