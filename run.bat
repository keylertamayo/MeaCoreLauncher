@echo off
echo === MeaCore Launcher Build Script ===

cd frontend
call npm install --silent
call npm run build
cd ..

call gradlew.bat clean build -x test --no-daemon
call gradlew.bat run --no-daemon
