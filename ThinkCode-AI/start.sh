#!/bin/bash
cd online-compiler
mvn clean package -DskipTests
java -jar target/online-compiler-0.0.1-SNAPSHOT.jar