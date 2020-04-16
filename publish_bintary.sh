#!/bin/bash
. .local.sh
./gradlew clean build bintrayUpload -PbintrayUser=$BINTRAY_USERNAME -PbintrayKey=$BINTRAY_KEY -PdryRun=false -PfatJar=false
