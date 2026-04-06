#!/bin/bash
set -e

echo "=== MeaCore Launcher Build Script ==="

# Build frontend
if [ -d "frontend" ]; then
    echo "Building frontend..."
    cd frontend
    npm install --silent
    npm run build
    cd ..
    echo "Frontend built successfully."
fi

# Build and run Java
echo "Building Java launcher..."
./gradlew clean build -x test --no-daemon

echo "Starting MeaCore Launcher..."
./gradlew run --no-daemon
