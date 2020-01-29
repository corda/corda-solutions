#!/bin/bash

# Compiles the docs in the source folder from rst format to html using Sphinx (Sphinx should be installed in your Python folder to work)
/C/Python27/Scripts/sphinx-build.exe -b html -d build\doctrees source build/html
