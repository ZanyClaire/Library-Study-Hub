@echo off
setlocal
cd /d "%~dp0"
if not exist target\classes mkdir target\classes
javac -encoding UTF-8 -d target\classes ^
  src\main\java\common\*.java ^
  src\main\java\server\*.java ^
  src\main\java\client\*.java
if errorlevel 1 (
  pause
  exit /b 1
)
xcopy /E /I /Y src\main\resources\* target\classes\ >nul
start "" javaw -cp target\classes server.ServerFrame
