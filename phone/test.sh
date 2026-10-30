#!/usr/bin/env bash
# Run the plain-JVM logic tests (no Android device needed).
set -e
cd "$(dirname "$0")"
[ -f ~/android-env.sh ] && source ~/android-env.sh
rm -rf build/test && mkdir -p build/test
javac -encoding UTF-8 -d build/test src/com/xintiao/beat/Logic.java test/LogicTest.java
java -cp build/test LogicTest
