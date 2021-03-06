# Projected Control Graph (PCG) Toolbox
The goal of path-sensitive analysis is to achieve accuracy by accounting precisely for the execution behavior along each path of a control flow graph (CFG). A practical adoption of PSA is hampered by two roadblocks: (a) the exponential growth of the number of CFG paths, and (b) the exponential complexity of a path feasibility check. We introduce projected control graph (PCG) as an optimal mathematical abstraction to address these roadblocks.

The PCG follows from the simple observation that for any given analysis problem, the number of distinct relevant execution behaviors may be much smaller than the number of CFG paths. The PCG is a projection of the CFG to retain only the relevant execution behaviors and elide duplicate paths with identical execution behavior. A mathematical definition of PCG and an efficient algorithm to transform CFG to PCG are presented.

More details on PCGs can be found at [https://www.ece.iastate.edu/kcsl/apsec2016-pcg](https://www.ece.iastate.edu/kcsl/apsec2016-pcg/).
