#!/usr/bin/env bash

set -x
set -e

REV=origin/master
PLUGIN_ID="org.jetbrains.gradle-import-and-save"

GRADLE_CACHE="/Users/jetbrains/.gradle/caches/modules-2/files-2.1"
IDEA="${GRADLE_CACHE}/com.jetbrains.intellij.idea/ideaIC/2018.3/70e72bdc97f330ebe4f06ad307d8928ea903cf87/ideaIC-2018.3"

SANDBOX="/Users/jetbrains/idea-gradle-jps-build-app/local"
KOTLIN_PLUGIN="${SANDBOX}/plugins/Kotlin"

#https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/jps-standalone/2018.3.3/jps-standalone-2018.3.3.zip
JPS="/Users/jetbrains/Downloads/jps-standalone-2018.3.3"

JDK="/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home"

PROJECT="/Users/jetbrains/tasks/kwjps/wjpstc"
BRANCH="origin/rr/snrostov/jpsbuild"

PLUGIN="${SANDBOX}/plugins/gradle-import-and-save"
JAVA="${JDK}/bin/java"

DEBUG=true

function checkout() {
    cd ${PROJECT}
    git fetch
    git reset --hard "$REV"
    git clean -qfdx

    echo "jpsBuild=true" >> gradle.properties
}

function importIdeaProject() {
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
    ARGS+=("-Didea.system.path=${SANDBOX}")
    ARGS+=("-Didea.config.path=${SANDBOX}/config")
    ARGS+=("-Djava.awt.headless=true")
    ARGS+=("-Didea.plugins.path=${SANDBOX}/plugins")
    ARGS+=("-Didea.required.plugins.id=org.jetbrains.kotlin,${PLUGIN_ID}")
    ARGS+=("-Dfile.encoding=UTF-8")
    ARGS+=("-Didea.skip.indices.initialization=true")
    ARGS+=("-classpath "${IDEA_CP}"")
    ARGS+=("com.intellij.idea.Main")
    ARGS+=("importAndSave")
    ARGS+=("${PROJECT}")
    ARGS+=("${JDK}")

    ${JAVA} ${ARGS[@]}
}

function build() {
    IDEA_PLUGINS="${IDEA}/plugins"
    IDEA_LIB="${IDEA}/lib"

    JPS_CP="${JDK}/lib/tools.jar"

    # idea
    for jar in platform-api.jar
    do
        JPS_CP="${IDEA_CP}:${IDEA_LIB}/${jar}"
    done

    # jps
    for jar in ${JPS}/*
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
        "${PROJECT}" \
        --artifacts "dist" \
        --all-modules \
        --config "${SANDBOX}/config"

    # -i
    # --modules "idea_main,idea_test"
}

#checkout
#importIdeaProject
build