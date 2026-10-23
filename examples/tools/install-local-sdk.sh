#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 1 ]]; then
  printf 'Usage: %s /path/to/spp-basic\n' "$0" >&2
  exit 2
fi
spp_toolchain_dir=$(cd -- "$1" && pwd)
if [[ -f "$spp_toolchain_dir/basic/generator/pom.xml" ]]; then
  spp_toolchain_dir="$spp_toolchain_dir/basic"
fi
if [[ ! -f "$spp_toolchain_dir/generator/pom.xml" || ! -f "$spp_toolchain_dir/appframe/pom.xml" ]]; then
  printf 'The selected directory does not contain the S++ compiler/runtime project.\n' >&2
  exit 2
fi
mvn -f "$spp_toolchain_dir/pom.xml" -Pbasic -pl generator,appframe -am install -DskipTests
