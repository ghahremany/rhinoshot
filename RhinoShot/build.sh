#!/usr/bin/env bash
# Builds the debug APK with the toolchain that lives in /home/user/tools.
set -euo pipefail

export JAVA_HOME=/home/user/tools/jdk17
export ANDROID_HOME=/home/user/android-sdk
export ANDROID_SDK_ROOT=/home/user/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"

cd /home/user/RhinoShot

/home/user/tools/gradle-8.9/bin/gradle \
  --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx800m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8" \
  -Pkotlin.compiler.execution.strategy=in-process \
  "$@"
