#!/bin/sh
# Minimal Gradle wrapper launcher.
# If gradle/wrapper/gradle-wrapper.jar is missing, generate it once with:  gradle wrapper
##############################################################################

APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "ERROR: $CLASSPATH not found." >&2
  echo "Run 'gradle wrapper' once to generate it, then re-run ./gradlew." >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ]; then
  JAVACMD=$JAVA_HOME/bin/java
else
  JAVACMD=java
fi

exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
