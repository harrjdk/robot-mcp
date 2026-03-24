#!/usr/bin/env bash
exec java --enable-native-access=ALL-UNNAMED -jar target/robot-mcp-0.0.1-SNAPSHOT.jar "$@"
