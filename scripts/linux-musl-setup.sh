#!/usr/bin/env bash

# Instructions from this page https://www.graalvm.org/reference-manual/native-image/StaticImages/#preparation

set -euo pipefail

############################################################
#                        GLOBALS                           #
############################################################

OS=$(uname -s)
ARCH=$(uname -m)
MUSL_DIST_URL="https://more.musl.cc/10.2.1/x86_64-linux-musl/x86_64-linux-musl-native.tgz"
MUSL_DIR="${HOME}/.local/musl"
MUSL_GCC_COMPILER="${MUSL_DIR}/${ARCH}-linux-musl-native/bin/${ARCH}-linux-musl-gcc"

############################################################
#                        FUNCTIONS                         #
############################################################

die() {
  echo "FATAL: $@" 1>&2
  exit 1
}

script_init() {
  # make sure that we're running on linux
  local os=$(uname -s)
  test "${os}" = "Linux" || die "Unsupported operating system: ${os}"

  # make sure that we're running on a supported architecture
  local arch=$(uname -m)
  test "$arch" != "x86_64" -a "${arch}" != "i686" && die "Unsupported architecture: ${arch}"
}

if [ -f "$MUSL_GCC_COMPILER" ]; then
  echo "MUSL is already setup at ${MUSL_DIR}"
  exit 0
fi

mkdir "$MUSL_DIR" || true

cd "$MUSL_DIR"

wget https://more.musl.cc/10.2.1/x86_64-linux-musl/x86_64-linux-musl-native.tgz

tar zxvf x86_64-linux-musl-native.tgz

TOOLCHAIN_DIR=$(pwd)/x86_64-linux-musl-native

CC=$TOOLCHAIN_DIR/bin/gcc

wget https://zlib.net/zlib-1.2.12.tar.gz

tar zxvf zlib-1.2.11.tar.gz

cd zlib-1.2.11

./configure --prefix="$TOOLCHAIN_DIR" --static
make
make install

export PATH=$PATH:${TOOLCHAIN_DIR}/bin