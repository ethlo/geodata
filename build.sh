#!/bin/sh

rm -rf dist
mkdir -p dist

move() {
  name=${PWD##*/}
  target="../dist/$name-image.tar.gz"
  docker save "ethlo/$name:1.0.0-SNAPSHOT" | gzip >"$target"
  echo "Saved to $target"
}

mvn clean install -DskipTests

cd geodata-server || exit
mvn -DskipTests spring-boot:build-image
move
cd ..

cd geodata-importer || exit
mvn -DskipTests spring-boot:build-image
move
cd ..
