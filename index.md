---
layout: default
---

## Overview
The goal of path-sensitive analysis is to achieve accuracy by accounting precisely for the execution behavior along each path of a control flow graph (CFG). A practical adoption of PSA is hampered by two roadblocks: (a) the exponential growth of the number of CFG paths, and (b) the exponential complexity of a path feasibility check. We introduce projected control graph (PCG) as an optimal mathematical abstraction to address these roadblocks.

The PCG follows from the simple observation that for any given analysis problem, the number of distinct relevant execution behaviors may be much smaller than the number of CFG paths. The PCG is a projection of the CFG to retain only the relevant execution behaviors and elide duplicate paths with identical execution behavior. A mathematical definition of PCG and an efficient algorithm to transform CFG to PCG are presented.

More details on PCGs can be found at [https://www.ece.iastate.edu/kcsl/apsec2016-pcg](https://www.ece.iastate.edu/kcsl/apsec2016-pcg).

## Features
The aim of this project is to make PCG analysis practical to client analyses. The most useful features are outlined below.

- Factory methods to compute Projected Control Graphs (PCGs) and Interprocedural Project Control Graphs (IPCGs)
- Smart View to compute PCGs for selection events
- PCG Builder view to iteratively build PCGs and IPCGs
- PCG Log view to track and load previous PCG computations across analysis sessions

## Getting Started
Ready to get started?

1. First [install](/pcg-toolbox/install) the Toolbox Commons plugin
2. Then check out the provided [tutorials](/pcg-toolbox/tutorials) to jump start your analysis

## Source Code
Need additional resources?  Checkout the [Javadocs](/pcg-toolbox/javadoc/index.html) or grab a copy of the [source](https://github.com/EnSoftCorp/pcg-toolbox).