#!/bin/sh
# Gradle wrapper - downloads and runs Gradle
# In production, download the full Gradle wrapper from Android Studio or run:
#   gradle wrapper --gradle-version 8.11.1
# This stub just shows how to invoke Gradle directly

GRADLE_VERSION="8.11.1"
GRADLE_HOME="${HOME}/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"

if [ ! -f "$GRADLE_BIN" ]; then
  echo "Gradle ${GRADLE_VERSION} not found."
  echo "Please run: gradle wrapper --gradle-version ${GRADLE_VERSION}"
  echo "Or open this project in Android Studio to generate the wrapper."
  exit 1
fi

exec "$GRADLE_BIN" "$@"
