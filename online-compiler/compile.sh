#!/bin/bash
set -e
SOURCE_FILE=$1
echo "--- Starting Compiler ---"
python3 src/lexer.py "$SOURCE_FILE"
javac src/Parser.java
(cd go && go run SemanticAnalyzer.go) # Run Go from its own directory
(cd cpp && g++ CodeGenerator.cpp -o CodeGenerator && ./CodeGenerator) # Run C++ from its own directory
echo "--- Compilation Successful ---"