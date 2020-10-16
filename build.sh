#!/bin/sh
mvn clean install -DskipTests
cd geodata-server
mvn spring-boot:build-image
cd ..

