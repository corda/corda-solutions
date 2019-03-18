# Content for the Solutions site

## Installing tools on a Mac

Install Xcode from Apple App store.

```
xcode-select --install
```

Install macports: www.macports.org/install.php

```
sudo port install py27-sphinx
sudo port select --set python python27
sudo port select --set sphinx py27-sphinx
sudo port install py27-pip
sudo port select --set pip pip27
sudo pip install sphinx_rtd_theme
sudo pip install rst2pdf
```

## Building the docs

```
cd <repo directory>/docs 
make html
```

## Installing tools on Windows 10

1. Install “make” for Windows http://gnuwin32.sourceforge.net/packages/make.htm
2. Choose a tool to install Python such as https://chocolatey.org/install
3. For the example above when choco is successfully installed from Command Promp run:> choco install python
4. Install pip using C:\Python37>python.exe -m pip install -U pip

```

Collecting pip
  Downloading https://files.pythonhosted.org/packages/d8/f3/413bab4ff08e1fc4828dfc59996d721917df8e8583ea85385d51125dceff/pip-19.0.3-py2.py3-none-any.whl (1.4MB)
    100% |████████████████████████████████| 1.4MB 6.5MB/s
Installing collected packages: pip
  Found existing installation: pip 18.1
    Uninstalling pip-18.1:
      Successfully uninstalled pip-18.1
Successfully installed pip-19.0.3

```

5. Install sphinx as follows: C:\Python37\Scripts>pip.exe install -U sphinx
6. Install sphinx theme for documents as follows : C:\Python37\Scripts>pip.exe install sphinx_rtd_theme
7. Run the script to build the documents as follows : C:\Users\USER\Documents\GitHub\corda-solutions\docs>C:\Python37\Scripts\sphinx-build.exe -b html -d build\doctrees source build/html and you should see this output:

```

Running Sphinx v1.8.5
making output directory...
building [mo]: targets for 0 po files that are out of date
building [html]: targets for 57 source files that are out of date
updating environment: 57 added, 0 changed, 0 removed
reading sources... [100%] patterns/patterns_wip                                ancerements_process
looking for now-outdated files... none found
pickling environment... done
checking consistency... done
preparing documents... done
writing output... [100%] patterns/patterns_wip                                 ncerements_process
C:\Users\USER\Documents\GitHub\corda-solutions\docs\source\deployment\bridge-node-float.rst:35: WARNING: Could not lex literal_block as "javascript". Highlighting skipped.
C:\Users\USER\Documents\GitHub\corda-solutions\docs\source\deployment\httpproxy.rst:19: WARNING: Could not lex literal_block as "javascript". Highlighting skipped.
C:\Users\Simon Webster\Documents\GitHub\corda-solutions\docs\source\deployment\httpproxy.rst:31: WARNING: Could not lex literal_block as "javascript". Highlighting skipped.
C:\Users\USER\Documents\GitHub\corda-solutions\docs\source\deployment\node-registration.rst:34: WARNING: Could not lex literal_block as "javascript". Highlighting skipped.
generating indices... genindex
writing additional pages... search
copying images... [  7%] corda-modelling-notation\complexity\../resources/complexity/CMN2_C_Linearid-coupling-attachmentcopying images... [  9%] corda-modelling-notation\complexity\../resources/complexity/CMN2_C_Linearid_coupling_state_instcopying images... [ 12%] corda-modelling-notation\high-level-architecture\../resources/arch/CMN2_HLA_High_level_process.copying images... [100%] designs\./resources/ledger_sync.png                   ngI_Transaction_example.pngs.pngngpng
copying static files... done
copying extra files... done
dumping search index in English (code: en) ... done
dumping object inventory... done
build succeeded, 4 warnings.
 
The HTML pages are in build\html.
 
 ```


## GUI Editing Tooling for Mac

A nice visual git browser is (good for non-devs): https://desktop.github.com/
A visual editor that has a wysiwyg editor for markdown is: https://atom.io/ and insall the rst-preview package https://atom.io/packages/rst-preview
Note rst-preview requires you to install pandoc https://github.com/jgm/pandoc/releases

## Live Editing

This will watch the source and auto build the html and refresh the connected browser when a file is changed:
``
sudo pip install sphinx-autobuild
``

- Try to run sphinx-autobuild - If the command is not found then check the above logs for a warning about the python plugins path not being on the main path
- Edit your PATH variable to include this directory
- Then run:
``
cd solutions
make livehtml
``

