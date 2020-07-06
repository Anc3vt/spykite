#!/bin/bash

echo "Spykite release..."

java=$JAVA_HOME/bin/java

WS=~/workspace

mv $WS/ancevt-utils/src/main/resources/ /tmp/ancevt-utils-src-main-resources -v
mv $WS/messaging/src/main/resources/ /tmp/messaging-src-main-resources -v
mv $WS/repl/src/main/resources/ /tmp/repl-res -v

cd ../repl
mvn clean package install

cd ../ancevt-utils
mvn clean package install

cd ../messaging
mvn  clean package install

cd ../spykite

./increment-build.sh

mvn clean package install

DIR=~/Software/Spykite

mkdir -p $DIR

rm $DIR/*jar -rf
rm $DIR/log/* -rf
rm $WS/cianeed/skfx.jar -rf

cp target/spy*ies.jar $DIR/skfx.jar -v
cp target/spy*ies.jar $WS/cianeed/skfx.jar -v
cp target/spy*ies.jar $WS/spykite/skfx.jar -v
cp target/spy*ies.jar ~/Software/Cianeed/skfx.jar -v
cp target/spy*ies.jar ~/Shared/cian/skfx.jar -v

mv /tmp/ancevt-utils-src-main-resources $WS/ancevt-utils/src/main/resources -v
mv /tmp/messaging-src-main-resources $WS/messaging/src/main/resources -v
mv /tmp/repl-res $WS/repl/src/main/resources -v

echo

cd $DIR
$java -jar skfx.jar --version
