#!/bin/bash

wget https://dl.google.com/dl/android/studio/ide-zips/3.6.0.14/android-studio-ide-192.5947919-mac.zip
unzip android-studio-ide-192.5947919-mac.zip
cd android-studio-ide-192.5947919-mac
zip -r ../repacked.zip .
cd ..

mvn install:install-file -Dfile=repacked.zip -DgroupId=com.jetbrains.intellij.idea -DartifactId=ideaIC -Dversion=AS-3.6 -Dpackaging=zip
export working_dir=SET_HERE_PATH_TO_PROJECT_FOR_IMPORT
./gradlew runIde 2>&1 | tee log.txt
cat log.txt | grep teamcity > filtered.txt


