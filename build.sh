#!/usr/bin/env bash

if [ ! -z "${JAVA_8_HOME}" ]; then
  echo JAVA_8_HOME: ${JAVA_8_HOME}
  JAVA_HOME=${JAVA_8_HOME}
fi

MVN="`which mvn`"
if [ ! -z "${MAVEN_HOME}" ]; then
  echo MAVEN_HOME: ${MAVEN_HOME}
  MVN="${MAVEN_HOME}/bin/mvn"
fi

if [ -z "$MVN" ]; then
  echo maven not found.
  exit 1
else
  rm maven_log
  $MVN -Dtycho.debug.resolver=true -X -l maven_log clean package
fi
