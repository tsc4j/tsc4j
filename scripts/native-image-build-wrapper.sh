#!/usr/bin/env bash

############################################################
# runtime env vars

############################################################

OS=$(uname -s | tr '[A-Z]' '[a-z]')
ARCH=$(uname -m)

############################################################
#                         FUNCTIONS                        #
############################################################

linux_os_query() {
  local item="$1"
  test -z "$item" && die "Undefined linux os query item."

  cat /etc/os-release 2>/dev/null | grep "$item="
}

run_linux() {
  local distro=$(linux_os_query "ID")
  local version=$(linux_os_query "VERSION_ID")

  # we want to build 
  true
}

run_darwin() {
  true
}

script_init() {
  true
}

do_run() {
  script_init

  # compute function to run
  local func_to_run="run_${OS}"
  local type=$(type -t $func_to_run)
  test "$type" = "function" || die "Unsupported operating system: $OS. Run $0 --help for instructions."

  # run the function
  "$func_to_run" "$@" || die "Error running $OS build function."
}

printhelp() {
  cat <<EOF
Usage: $0 [OPTIONS]

This script is a wrapper around native-image-build.sh and makes sure that
it can do it's in a most optimal way by taking care of cross os/arch issues.

OPTIONS:
  -h    --help          This help message
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
