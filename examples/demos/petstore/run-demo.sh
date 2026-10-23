#!/usr/bin/env bash
set -euo pipefail
demo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$demo_dir/../.." && pwd)
cd -- "$repo_dir"
if [[ ${1:-web} == examples ]]; then
  shift
  test_selector=PetstoreScenarioTest
  if [[ $# -gt 0 ]]; then
    test_selector+="#"
    test_selector+=$(IFS=+; printf '%s' "$*")
  fi
  exec mvn -pl :petstore-demo -am test "-Dtest=$test_selector" -Dsurefire.failIfNoSpecifiedTests=false
fi
if [[ ${1:-web} != web ]]; then printf 'Usage: %s [web [application] | examples [Java test method...]]\n' "$0" >&2; exit 2; fi
spp_environment=${SPP_ENVIRONMENT:-develop}
spp_application=${2:-${SPP_APPLICATION:-petstore}}
spp_port=8086
case "$spp_application" in
  customerService) spp_port=8087 ;;
  productService) spp_port=8088 ;;
esac
if [[ $spp_environment == testing && -z ${SPP_DOMAIN_RPC_TOKEN:-} ]]; then
  printf 'SPP_DOMAIN_RPC_TOKEN is required for testing; use the same value in all three processes.\n' >&2
  exit 2
fi
if [[ ${SPP_DEMO_SKIP_BUILD:-0} != 1 ]]; then
  mvn -pl :petstore-demo -am package -DskipTests "-Dspp.environment=$spp_environment"
fi
demo_classpath=$(cat -- "$demo_dir/target/demo-runtime-classpath.txt")
exec java "-Dspp.environment=$spp_environment" "-Dspp.application=$spp_application" \
    -Dspp.web.host="${SPP_WEB_HOST:-127.0.0.1}" -Dspp.web.port="${SPP_WEB_PORT:-$spp_port}" \
    -Dspp.petstore.datasource-url="${SPP_PETSTORE_JDBC_URL:-jdbc:h2:file:$demo_dir/target/petstore;DB_CLOSE_DELAY=-1}" \
    -Dspp.petstore.testing.rpc-token="${SPP_DOMAIN_RPC_TOKEN:-}" \
    -Dspp.petstore.testing.customer-endpoint="${SPP_CUSTOMER_ENDPOINT:-http://127.0.0.1:8087}" \
    -Dspp.petstore.testing.product-endpoint="${SPP_PRODUCT_ENDPOINT:-http://127.0.0.1:8088}" \
    -Dspp.petstore.testing.shop-url="${SPP_TESTING_SHOP_JDBC_URL:-jdbc:h2:file:$demo_dir/target/testing-shop;DB_CLOSE_DELAY=-1}" \
    -Dspp.petstore.testing.customer-url="${SPP_TESTING_CUSTOMER_JDBC_URL:-jdbc:h2:file:$demo_dir/target/testing-customer;DB_CLOSE_DELAY=-1}" \
    -Dspp.petstore.testing.product-url="${SPP_TESTING_PRODUCT_JDBC_URL:-jdbc:h2:file:$demo_dir/target/testing-product;DB_CLOSE_DELAY=-1}" \
    -Dspp.petstore.testing.ledger-url="${SPP_TESTING_LEDGER_JDBC_URL:-jdbc:h2:file:$demo_dir/target/testing-ledger;DB_CLOSE_DELAY=-1}" \
    -cp "$demo_dir/target/classes:$demo_classpath" com.servpp.service.runtime.GeneratedWebServer
