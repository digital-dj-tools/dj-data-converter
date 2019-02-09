#!/usr/bin/env sh

# sudo apt install openjdk-11-jdk-headless

jlink --module-path /usr/lib/jvm/java-11-openjdk-amd64/jmods/ --add-modules java.base,java.xml,java.logging --output ./jlink/linux64 \
 --strip-debug --no-man-pages --no-header-files --compress=2
# https://github.com/docker-library/openjdk/issues/217
strip -p --strip-unneeded ./jlink/linux64/lib/server/libjvm.so