@echo off

where typst >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: typst is not installed.
    echo.
    echo Install it via one of the following methods:
    echo   Windows:  winget install --id Typst.Typst
    echo   Manual:   https://github.com/typst/typst/releases
    exit /b 1
)

typst compile thesis.typ
echo Compiled thesis.pdf successfully.
