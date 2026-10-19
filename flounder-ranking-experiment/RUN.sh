#!/usr/bin/env sh
set -eu
mvn -Dresearch.benchmark=false test
mvn -Dresearch.benchmark=true -Dresearch.batch=0 -Dresearch.samples=30 test
mvn -Dresearch.benchmark=true -Dresearch.batch=32 -Dresearch.samples=30 test
