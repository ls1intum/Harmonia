#!/bin/bash

if ! command -v typst &> /dev/null; then
    echo "Error: typst is not installed."
    echo ""
    echo "Install it via one of the following methods:"
    echo "  macOS:   brew install typst"
    echo "  Linux:   cargo install typst-cli"
    echo "  Manual:  https://github.com/typst/typst/releases"
    exit 1
fi

typst compile seminar_paper.typ
echo "Compiled seminar_paper.pdf successfully."
