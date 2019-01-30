#!/usr/bin/env bash

set -x
set -e

REV=origin/master
PROJECT="/Users/jetbrains/tasks/kwjps/wgradle"

function checkout() {
    cd ${PROJECT}
    git fetch
    git reset --hard "$REV"
    git clean -qfdx
}

function build() {
    ./gradlew clean --parallel
    ./gradlew dist ideaPlugin  --parallel
}

checkout
build