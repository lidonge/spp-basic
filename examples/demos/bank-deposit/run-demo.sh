#!/usr/bin/env bash
set -euo pipefail
demo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$demo_dir/../.." && pwd)
cd -- "$repo_dir"
spp_environment=${SPP_ENVIRONMENT:-develop}
if [[ ${1:-examples} == examples ]]; then
  exec mvn -pl :xbod-deposit-demo -am test \
    '-Dtest=Xbod*Test' \
    -Dsurefire.failIfNoSpecifiedTests=false "-Dspp.environment=$spp_environment"
fi
if [[ ${1:-} != web ]]; then printf 'Usage: %s [examples | web]\n' "$0" >&2; exit 2; fi
if [[ ${SPP_DEMO_SKIP_BUILD:-0} != 1 ]]; then
  mvn -pl :xbod-deposit-demo -am package -DskipTests "-Dspp.environment=$spp_environment"
fi
demo_classpath=$(cat -- "$demo_dir/target/demo-runtime-classpath.txt")
exec java "-Dspp.environment=$spp_environment" -Dspp.application=xbodDeposit \
  -Dspp.web.host="${SPP_WEB_HOST:-127.0.0.1}" -Dspp.web.port="${SPP_WEB_PORT:-8089}" \
  -Dspp.xbod.datasource-url="${SPP_XBOD_JDBC_URL:-jdbc:h2:file:$demo_dir/target/xbod-deposit;DB_CLOSE_DELAY=-1;WRITE_DELAY=0}" \
  -Dspp.xbod.testing.ledger-url="${SPP_XBOD_LEDGER_URL:-jdbc:h2:file:$demo_dir/target/xbod-ledger;DB_CLOSE_DELAY=-1;WRITE_DELAY=0}" \
  -cp "$demo_dir/target/classes:$demo_classpath" \
  com.servpp.service.runtime.GeneratedWebServer
