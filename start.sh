#!/bin/bash

cd online-compiler
mvn clean package
java -jar target/your-backend.jar
