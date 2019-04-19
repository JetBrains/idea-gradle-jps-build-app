#!/usr/bin/env bash

set -x
set -e

REV="master" # note: changing this requires bare clone config update

###################

JDK="$JDK_18"

# this script home dir
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SANDBOX="${DIR}/tasks/kwjps/sandbox"

if [[ ! -d "${DIR}/utils" ]]; then
    ${DIR}/prepare-deps.sh
fi

IDEA_SANDBOX="${DIR}/local"
JPS_STANDALONE="${DIR}/utils/jps-standalone"
DIST_COMPARE_BIN="${DIR}/utils/dist-compare/bin/dist-compare"

GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"
IDEA="${GRADLE_CACHE}/com.jetbrains.intellij.idea/ideaIC/2019.1/b0bd766fbab61007aefe7922895a1726afe11b14/ideaIC-2019.1"

mkdir -p "$SANDBOX"
GIT_BARE="${SANDBOX}/gitbare"
JPS_PROJECT="${SANDBOX}/jps"
GRADLE_PROJECT="${SANDBOX}/wgradle"
REPORT_DIR="${SANDBOX}/report"

KOTLIN_PLUGIN="${IDEA_SANDBOX}/plugins/Kotlin"
JAVA="${JDK}/bin/java"

PLUGIN_ID="org.jetbrains.gradle-import-and-save"
DEBUG=false

#####################

function gitbare() {
    echo "##teamcity[blockOpened name='git-clone-${GIT_BARE}' description='Git clone ${GIT_BARE}']"
    if [[ -d "${GIT_BARE}" ]]; then
        cd ${GIT_BARE}
        git fetch
    else
        git clone --bare --depth=1 -b ${REV} https://github.com/JetBrains/kotlin.git ${GIT_BARE}
    fi
    echo "##teamcity[blockClosed name='git-clone-${GIT_BARE}']"
}

function checkout() {
    if [[ -d "$1" ]]; then
        cd ${1}
        git fetch
        git reset --hard "origin/${REV}"
        git clean -qfdx
    else
        git clone --shared ${GIT_BARE} -b ${REV} ${1}
    fi
}

function jpsImportIdeaProject() {
    pushd ${SANDBOX}/jps
    echo "##teamcity[blockOpened name='import-jps-project' description='Import jps project']"

    IDEA_LIB="${IDEA}/lib"
    IDEA_CP="${JDK}/lib/tools.jar"
    for jar in log4j.jar jdom.jar trove4j.jar openapi.jar util.jar extensions.jar bootstrap.jar idea_rt.jar idea.jar
    do
        IDEA_CP="${IDEA_CP}:${IDEA_LIB}/${jar}"
    done

    ARGS=()
    ARGS+=("-Xmx3G")
    ARGS+=("-Xms256m")
    ARGS+=("-ea")
    if [[ "$DEBUG" = true ]] ; then
        ARGS+=("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5005,suspend=y")
    fi
    ARGS+=("-Didea.system.path=${IDEA_SANDBOX}")
    ARGS+=("-Didea.config.path=${IDEA_SANDBOX}/config")
    ARGS+=("-Djava.awt.headless=true")
    ARGS+=("-Didea.plugins.path=${IDEA_SANDBOX}/plugins")
    ARGS+=("-Didea.required.plugins.id=org.jetbrains.kotlin,${PLUGIN_ID}")
    ARGS+=("-Dfile.encoding=UTF-8")
    ARGS+=("-Didea.skip.indices.initialization=true")
    ARGS+=("-classpath "${IDEA_CP}"")
    ARGS+=("com.intellij.idea.Main")
    ARGS+=("importAndSave")
    ARGS+=("${JPS_PROJECT}")
    ARGS+=("${JDK}")

    ${JAVA} ${ARGS[@]}

    echo "##teamcity[blockClosed name='import-jps-project']"
    popd
}

function jpsBuild() {
    pushd ${SANDBOX}/jps
    echo "##teamcity[blockOpened name='jps-build' description='Run jps build']"

    IDEA_PLUGINS="${IDEA}/plugins"
    IDEA_LIB="${IDEA}/lib"

    JPS_CP="${JDK}/lib/tools.jar"

    # idea
    for jar in platform-api.jar
    do
        JPS_CP="${IDEA_CP}:${IDEA_LIB}/${jar}"
    done

    # jps
    for jar in ${JPS_STANDALONE}/*
    do
        JPS_CP="${JPS_CP}:$jar"
    done

    # kotlin
    for jar in jps/kotlin-jps-plugin.jar kotlin-stdlib.jar kotlin-reflect.jar kotlin-plugin.jar
    do
        JPS_CP="${JPS_CP}:${KOTLIN_PLUGIN}/lib/${jar}"
    done

    ${JAVA} \
        -Xmx3G \
        -Xms256m \
        -Djps.kotlin.home="${KOTLIN_PLUGIN}/kotlinc" \
        -classpath "${JPS_CP}" \
        org.jetbrains.jps.build.Standalone \
        "${JPS_PROJECT}" \
        --artifacts "dist" \
        --all-modules \
        --config "${IDEA_SANDBOX}/config"

    # -i
    # --modules "idea_main,idea_test"

    echo "##teamcity[blockClosed name='jps-build']"
    popd
}

echo "Updating shared git bare clone ${GIT_BARE}..."
gitbare

################ JPS

echo "Updating ${JPS_PROJECT}..."
checkout ${JPS_PROJECT}
echo "jpsBuild=true" >> ${JPS_PROJECT}/gradle.properties

jpsImportIdeaProject
jpsBuild

################ Gradle

echo "##teamcity[blockOpened name='gradle-build' description='Running gradle build']"
echo "Updating ${GRADLE_PROJECT}..."
checkout ${GRADLE_PROJECT}
#echo "jpsBuild=true" >> gradle.properties
cd ${GRADLE_PROJECT}
./gradlew clean  dist ideaPlugin --parallel
echo "##teamcity[blockClosed name='gradle-build']"

################ Compare

echo "##teamcity[blockOpened name='compare-distributions' description='Compare distributions']"
rm -rf ${REPORT_DIR}
mkdir -p ${REPORT_DIR}/diff
${DIST_COMPARE_BIN} ${GRADLE_PROJECT}/dist ${JPS_PROJECT}/dist ${REPORT_DIR}
echo "##teamcity[blockClosed name='compare-distributions']"
