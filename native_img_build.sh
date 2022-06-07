#!/bin/sh

############################################################
# runtime env vars
VERBOSE=${VERBOSE:-0}
USE_STATIC=${USE_STATIC:-1}
USE_LLVM=${USE_LLVM:-0}
############################################################

SYSTEM=$(uname -s | tr '[A-Z]' '[a-z]')
ARCH=$(uname -m)
BIN_OUT="$(pwd)/build/tsc4j-${SYSTEM}-${ARCH}"
DEFAULT_JAR_FILE="$(pwd)/tsc4j-uberjar/build/libs/tsc4j.jar"

die() {
  echo "FATAL: $@" 1>&2
  exit 1
}

post_process() {
  local file="$1"
  test -x "$file" || die "Can't post-process non-executable: $file"
  local dst="${file}.upx"

  echo "posprocessing binary with upx: $file"
  rm -f "$dst"
  upx -o "$dst" "$file"
}

native_img_compile() {
  local jar="$1"
  test -f "$jar" || jar="$DEFAULT_JAR_FILE"
  test -f "$jar" || die "Bad uber-jar to compile to native image: '$jar'"

  local build_dir=$(dirname "$BIN_OUT")
  mkdir -p "$build_dir" || die "can't create build_dir: $build_dir"
  cd "$build_dir" || die "can't enter build dir: $build_dir"

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

script_init() {
  test -z "$GRAALVM_HOME" && die "Undefined env var \$GRAALVM_HOME, please install graalvm from https://github.com/graalvm/graalvm-ce-builds/releases"
  test ! -d "$GRAALVM_HOME" && die "Bad graalvm directory: $GRAALVM_HOME"
  test ! -x "$GRAALVM_HOME/bin/native-image" && die "GraalVM installation $GRAALVM_HOME doesn't contain native-image binary, install it with: 'gu install native-image'"

  # check for required tools
  local bin=""

  # look for required binaries
  local bin_name=""
  for bin_name in upx; do
    bin=$(which "$bin_name" 2>/dev/null)
    test -x "$bin" || die "Can't find binary on the system: $bin_name"
  done
}

do_run() {
  script_init
  native_img_compile "$@" || die "native-image build failed."
  post_process "$BIN_OUT"

  echo ""
  echo "produced binaries have been placed to: $(dirname "${BIN_OUT}")"
}

do_run "$@"

# vim:shiftwidth=2 softtabstop=2 expandtab
# EOF
