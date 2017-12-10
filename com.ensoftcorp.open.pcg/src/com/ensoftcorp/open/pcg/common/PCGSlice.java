package com.ensoftcorp.open.pcg.common;

import java.util.ArrayList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.slice.analysis.DataDependenceGraph;

public class PCGSlice {

	public static Q getPCGSlice(Q events, int iterations){
		return getLastPCGSliceIteration(events, iterations).getPCG();
	}
	
	public static PCG getLastPCGSliceIteration(Q events, int iterations){
		List<PCG> pcgs = getPCGSliceIterations(events, iterations);
		return pcgs.get(pcgs.size()-1);
	}
	
	public static List<PCG> getPCGSliceIterations(Q events, int iterations){
		events = events.nodes(XCSG.ControlFlow_Node);
		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
		List<PCG> pcgs = new ArrayList<PCG>();
		pcgs.add(PCGFactory.create(events));
		while(iterations == Integer.MAX_VALUE || iterations-pcgs.size() > 0){
			PCG lastPCG = pcgs.get(pcgs.size()-1);
			
			Graph dfg = CommonQueries.dfg(function).eval();
			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
			Q eventDataDependencies = ddg.getGraph().predecessors(lastPCG.getPCG().retainNodes());

			if(lastPCG.getPCG().eval().nodes().size() == lastPCG.getPCG().union(eventDataDependencies).eval().nodes().size()){
				break; // fixed point
			} else {
				pcgs.add(PCGFactory.create(lastPCG.getPCG().union(eventDataDependencies)));
			}
		}
		return pcgs;
	}
	
}
