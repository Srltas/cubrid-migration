#!/bin/sh

# Resolve the directory of the script
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
DIR=`dirname "$PRG"`

# Find Java
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
  fi
fi

if [ -z "$JAVA_CMD" ]; then
  JAVA_CMD=java
fi

# Check java
if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
    echo "Error: Java not found. Please set JAVA_HOME." >&2
    exit 1
fi

"$JAVA_CMD" -Xms40M -Xmx1400M -jar "$DIR/migration.jar" "$@"
