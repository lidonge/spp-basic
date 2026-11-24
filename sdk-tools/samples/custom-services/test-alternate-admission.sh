#!/usr/bin/env bash
# Run after installing the Basic SDK. Both builds use the sample's same SPP and PHY.
set -eu
cd "$(dirname "$0")"
stage=$(mktemp -d)
trap 'rm -rf -- "$stage"' EXIT
cp -R src/main/spp/. "$stage/"
cp src/test/resources/alternate-admission/ledger-admission.app "$stage/"
mvn -o -B -ntp clean test -Dtest=AdmissionPlayTest \
    -Dsample.spp.source="$stage" -Dsample.alternate.admission=true
