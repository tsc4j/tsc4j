#!/usr/bin/env bash

############################################################
# runtime env vars
VERBOSE=${VERBOSE:-0}
USE_STATIC=${USE_STATIC:-1}
USE_LLVM=${USE_LLVM:-0}
############################################################

SYSTEM=$(uname -s | tr '[A-Z]' '[a-z]')
ARCH=$(uname -m)
BUILD_DIR="$(pwd)/build"
BIN_OUT="${BUILD_DIR}/tsc4j-${SYSTEM}-${ARCH}"
DEFAULT_JAR_FILE="$(pwd)/tsc4j-uberjar/build/libs/tsc4j.jar"

############################################################
#                         FUNCTIONS                        #
############################################################

die() {
  echo "FATAL: $@" 1>&2
  exit 1
}

has_upx() {
  which upx >/dev/null 2>&1
}

post_process_upx() {
  has_upx || return 0
  test -f "$1" || die "Invalid binary to pre-process with upx: $1"

  local dst="$1.upx"
  rm -f "$dst"

  echo "posprocessing binary with upx: $1"
  upx -q -o "$dst" "$1" || die "Can't upx post-process file: $1"
}

post_process() {
  local f=""
  for f in "$@"; do
    post_process_upx "$f"
  done
}

native_img_compile() {
  local jar="$1"
  test -f "$jar" || jar="$DEFAULT_JAR_FILE"
  test -f "$jar" || die "Bad uber-jar to compile to native image: '$jar'"

  cd "${BUILD_DIR}" || die "can't enter build dir: ${BUILD_DIR}"

  local opt=""
  test "$VEBOSE" = "1" && opt="${opt} --verbose"

  if [ "${USE_STATIC}" = "1" ]; then
    test "$SYSTEM" = "linux" &&  opt="${opt} --static"
  fi

  test "$USE_LLVM" = "1" && opt="${opt} -H:CompilerBackend=llvm"

  echo "compiling to native image: $jar"
  rm -f "$BIN_OUT" "tsc4j"
  $GRAALVM_HOME/bin/native-image \
    --initialize-at-build-time=ch.qos.logback,org.slf4j,javax.xml,jdk.xml,com.sun.org.apache.xerces,com.sun.xml \
    --initialize-at-run-time=io.netty.util.internal.logging.Log4JLogger \
    --trace-class-initialization=ch.qos.logback,jdk.xml \
    --trace-object-instantiation=ch.qos.logback,jdk.xml \
    --enable-http \
    --enable-https \
    --no-fallback \
    $opt -jar "$jar" || die "unable to build native image."

  mv tsc4j "$BIN_OUT" || die "unable to move tsc4j binary to: $BIN_OUT"
}

action_native_image() {
  native_img_compile
}

action_post_process() {
  post_process_upx "${BIN_OUT}"
}

action_all() {
  action_native_image
  action_post_process

  echo ""
  echo "produced binaries have been placed to: ${BUILD_DIR}"
  echo ""
  file "${BIN_OUT}"*
  echo ""
  du -hc "${BIN_OUT}"*
}

script_init() {
  test -z "$GRAALVM_HOME" && die "Undefined env var \$GRAALVM_HOME, please install graalvm from https://github.com/graalvm/graalvm-ce-builds/releases"
  test ! -d "$GRAALVM_HOME" && die "Bad graalvm directory: $GRAALVM_HOME"
  test ! -x "$GRAALVM_HOME/bin/native-image" && die "GraalVM installation $GRAALVM_HOME doesn't contain native-image binary, install it with: 'gu install native-image'"

  mkdir -p "${BUILD_DIR}" || die "Can't create build dir: $BUILD_DIR"
}

do_run() {
  local action="$1"
  shift
  test -z "$action" && action="all"

  script_init

  # compute function to run
  local func_to_run="action_${action}"
  local type=$(type -t $func_to_run)
  test "$type" = "function" || die "Invalid action: $action. Run $0 --help for instructions."

  # run the function
  "$func_to_run" "$@" || die "Error running action: $action"
}

printhelp() {
  cat <<EOF
Usage: $0 [OPTIONS] [action] [args]

OPTIONS:
  -h    --help          This help message

ENV VARS:
* VERBOSE    (default: $VERBOSE)   :: verbose native-image compilation
* USE_STATIC (default: $USE_STATIC)   :: compile static binaries
* USE_LLVM   (default: $USE_LLVM)   :: use native-image llvm toolchain

ACTIONS:
* native_image          :: compile uber-jar into native image binary
* post_process          :: optionally post-process produced binaries
* all                   :: do all actions in a single step
EOF
}

############################################################
#                           MAIN                           #
############################################################

test "$1" = "-h" -o "$1" = "--help" && {
  printhelp
  exit 0
}

do_run "$@"

# vim:shiftwidth=2 softtabstop=2 expandtab
# EOF
