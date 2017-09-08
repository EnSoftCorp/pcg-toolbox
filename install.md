---
layout: page
title: Install
permalink: /install/
---

Installing the PCG Toolbox Eclipse plugin is easy.  It is recommended to install the plugin from the provided update site, but it is also possible to install from source.
        
### Installing from Update Site (recommended)
1. Start Eclipse, then select `Help` &gt; `Install New Software`.
2. Click `Add`, in the top-right corner.
3. In the `Add Repository` dialog that appears, enter &quot;Atlas Toolboxes&quot; for the `Name` and &quot;[https://ensoftcorp.github.io/toolbox-repository/](https://ensoftcorp.github.io/toolbox-repository/)&quot; for the `Location`.
4. In the `Available Software` dialog, select the checkbox next to "PCG Toolbox" and click `Next` followed by `OK`.
5. In the next window, you'll see a list of the tools to be downloaded. Click `Next`.
6. Read and accept the license agreements, then click `Finish`. If you get a security warning saying that the authenticity or validity of the software can't be established, click `OK`.
7. When the installation completes, restart Eclipse.

## Installing from Source
If you want to install from source for bleeding edge changes, first grab a copy of the [source](https://github.com/EnSoftCorp/pcg-toolbox) repository. In the Eclipse workspace, import the `com.ensoftcorp.open.pcg` Eclipse project located in the source repository.  Right click on the project and select `Export`.  Select `Plug-in Development` &gt; `Deployable plug-ins and fragments`.  Select the `Install into host. Repository:` radio box and click `Finish`.  Press `OK` for the notice about unsigned software.  Once Eclipse restarts the plugin will be installed and it is advisable to close or remove the `com.ensoftcorp.open.pcg` project from the workspace.

## Changelog
Note that version numbers are based off [Atlas](http://www.ensoftcorp.com/atlas/download/) version numbers.

### 3.1.6
- Bug fixes for sandboxing that were causing correctness and performance issues
- Released PCG Builder (for inter-procedural PCGs) and PCG Log user interfaces

### 3.1.0
- PCGs are now computed in a sandbox to prevent spurious edges when computing multiple PCGs in the same control flow graph
- PCGs can optionally be serialized in the Atlas graph and viewed with the PCG Log View
- Improved support for IPCGs
- Improved highlighting
- Broke dependencies on language specific toolbox (note that IPCG support is based on the set of installed toolbox commons projects)

### 3.0.15
- Initial release