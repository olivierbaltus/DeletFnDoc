#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 /path/to/jace.jar [version]"
  exit 1
fi

JACE_JAR="$1"
VERSION="${2:-5.5.12.0}"

mvn install:install-file \
  -Dfile="${JACE_JAR}" \
  -DgroupId=com.ibm.filenet \
  -DartifactId=jace \
  -Dversion="${VERSION}" \
  -Dpackaging=jar
