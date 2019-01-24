#!/usr/bin/env bash

set -x
set -e

IDEA="/Users/jetbrains/Library/Application Support/JetBrains/Toolbox/apps/IDEA-C/ch-0/183.5153.38/IntelliJ IDEA CE.app"
KOTLIN_PLUGIN="/Users/jetbrains/kotlin/dist/artifacts/ideaPlugin/Kotlin"

#https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/jps-standalone/2018.3.3/jps-standalone-2018.3.3.zip
JPS="/Users/jetbrains/Downloads/jps-standalone-2018.3.3"

JDK="/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home"
PLUGIN="/Users/jetbrains/Library/Caches/IntelliJIdea2018.3/plugins-sandbox/plugins"
PLUGIN_ID="com.your.company.unique.plugin.id"

PROJECT="/Users/jetbrains/tasks/kwjps/wjpstc"
BRANCH="origin/rr/snrostov/jpsbuild"
LOCAL="/Users/jetbrains/idea-gradle-jps-build-app/local_test"

JAVA="${JDK}/bin/java"

function checkout() {
    cd ${PROJECT}
    git fetch
    git reset --hard origin/rr/snrostov/jpsbuild
    git clean -qfdx

    echo "jpsBuild=true" >> gradle.properties
}

function importIdeaProject() {
    IDEA_LIB="${IDEA}/Contents/lib"
    IDEA_CP="${JDK}/lib/tools.jar"
    for jar in log4j.jar jdom.jar trove4j.jar openapi.jar util.jar extensions.jar bootstrap.jar idea_rt.jar idea.jar
    do
        IDEA_CP="${IDEA_CP}:${IDEA_LIB}/${jar}"
    done

    ${JAVA} \
        -Xmx3G \
        -Xms256m \
        -ea \
        -Didea.system.path="${LOCAL}" \
        -Didea.config.path="${LOCAL}/config" \
        -Djava.awt.headless=true \
        -Didea.required.plugins.id="${PLUGIN_ID}" \
        -Didea.plugins.path="${PLUGIN}" \
        -Dfile.encoding=UTF-8 \
        -Didea.skip.indices.initialization=true \
        -classpath "${IDEA_CP}" \
        com.intellij.idea.Main \
        importAndSave \
        ${PROJECT} \
        ${JDK}
}

function build() {
    IDEA_PLUGINS="${IDEA}/Contents/plugins"
    IDEA_LIB="${IDEA}/Contents/lib"

    IDEA_CP="${JDK}/lib/tools.jar"
    for jar in platform-api.jar
    do
        IDEA_CP="${IDEA_CP}:${IDEA_LIB}/${jar}"
    done

    JPS_CP="${JDK}/lib/tools.jar:${IDEA_CP}"
    for jar in ${JPS}/*
    do
        JPS_CP="${JPS_CP}:$jar"
    done
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
        -i \
        --all-modules \
        --artifacts "dist" \
        --config "${LOCAL}/config"
}

#checkout
#importIdeaProject
build