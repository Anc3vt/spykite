#!/bin/bash

build="$(cat src/main/resources/build.txt)"
build=$((build+1))
echo $build > src/main/resources/build.txt
