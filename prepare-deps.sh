#!/usr/bin/env bash
echo "##teamcity[blockOpened name='prepare-deps' description='Prepare jps build utilities']"
set -x
set -e

# this script home dir
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd ${DIR}
./gradlew prepareSandbox --parallel --stacktrace

mkdir ${DIR}/utils
cd ${DIR}/utils
curl -# -L "https://github.com/snrostov/dist-compare/releases/download/0.3/dist-compare-1.0-SNAPSHOT.zip" \
     -o "dist-compare.zip"
unzip -qo dist-compare.zip
rm -rf dist-compare
mv dist-compare-1.0-SNAPSHOT dist-compare

JPS_VERSION="2019.1"
curl -# -L "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/jps-standalone/${JPS_VERSION}/jps-standalone-${JPS_VERSION}.zip" \
     -o "jps-standalone.zip"
rm -rf jps-standalone
unzip -qo jps-standalone.zip -d jps-standalone

echo "##teamcity[blockClosed name='prepare-deps']"
