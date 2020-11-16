#!/bin/sh
set -e

mvn clean install -DskipTests

cd geodata-server || exit
mvn -DskipTests spring-boot:build-image
#move
cd ..

