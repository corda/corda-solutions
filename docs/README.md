Content for the Solutions site
==============================

Installing tools on a Mac
-------------------------

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

Building the docs
-----------------

```
cd solutions
make html
```

GUI Editing Tooling for Mac
---------------------------
A nice visual git browser is (good for non-devs): https://desktop.github.com/
A visual editor that has a wysiwyg editor for markdown is: https://atom.io/ and insall the rst-preview package https://atom.io/packages/rst-preview
Note rst-preview requires you to install pandoc https://github.com/jgm/pandoc/releases


Live Editing
---------------------------
This will watch the source and auto build the html and refresh the connected browser when a file is changed.
``
sudo pip install sphinx-autobuild
``

- Try to run sphinx-autobuild - If the command is not found then check the above logs for a warning about the python plugins path not being on the main path
- edit your PATH variable to include this directory
- then run
``
cd solutions
make livehtml
``