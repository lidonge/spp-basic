#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
fixture="$repo_root/tests/acceptance/physical-array-lane-a"
generated="$repo_root/target/acceptance/physical-array-lane-a/generated"
cli="$repo_root/sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar"

if [[ "${MAVEN_OPTS:-}" != *-Dmaven.repo.local=* ]]; then
  maven_repo_local="${MAVEN_REPO_LOCAL:?Set MAVEN_REPO_LOCAL or maven.repo.local in MAVEN_OPTS}"
  export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }-Dmaven.repo.local=$maven_repo_local"
fi
cd "$repo_root"

mvn -Pbasic -pl :spp-basic-cli -am install -DskipTests
rm -rf "$generated"
java -jar "$cli" generate \
  --source "$fixture" --output "$generated" \
  --package com.servpp.xbod.array.generated --environment testing
python3 - "$generated/pom.xml" <<'PY'
from pathlib import Path
import sys

pom_path = Path(sys.argv[1])
pom = pom_path.read_text()
marker = "  </dependencies>"
if marker not in pom:
    raise SystemExit(f"Generated POM has no dependencies section: {pom_path}")
test_dependencies = """    <dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>
    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><version>2.2.224</version><scope>test</scope></dependency>
"""
pom_path.write_text(pom.replace(marker, test_dependencies + marker, 1))
PY
mkdir -p "$generated/src/test/java"
cp "$fixture/ArrayReadTest.java" "$generated/src/test/java/ArrayReadTest.java"
mvn -f "$generated/pom.xml" test -Dtest=ArrayReadTest
