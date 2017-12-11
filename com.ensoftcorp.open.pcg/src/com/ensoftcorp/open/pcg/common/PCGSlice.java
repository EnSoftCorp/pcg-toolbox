package com.ensoftcorp.open.pcg.common;

import java.util.ArrayList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.slice.analysis.DataDependenceGraph;

public class PCGSlice {

	public static PCG getReversePCGSlice(Q cfg, Q dfg, Q events, int reverse){
		return getReversePCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse);
	}
	
	public static PCG getReversePCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int reverse){
		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
		List<PCG> pcgs = new ArrayList<PCG>();
		pcgs.add(PCGFactory.create(Common.toQ(events)));
		while(reverse == Integer.MAX_VALUE || reverse-pcgs.size() > 0){
			PCG lastPCG = pcgs.get(pcgs.size()-1);
			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
			Q eventDataDependencies = ddg.getGraph().predecessors(lastPCG.getPCG().retainNodes());
			if(lastPCG.getPCG().eval().nodes().size() == lastPCG.getPCG().union(eventDataDependencies).eval().nodes().size()){
				break; // fixed point
			} else {
				pcgs.add(PCGFactory.create(lastPCG.getPCG().union(eventDataDependencies)));
			}
		}
		return pcgs.get(pcgs.size()-1);
	}
	
	// buggy but more efficient approach
//	public static PCG getReversePCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int reverse){
//		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
//		AtlasSet<Node> reverseEvents = new AtlasHashSet<Node>(events);
//		
//		// compute dominance and add implied events
//		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg, Common.toQ(cfg).roots().eval().nodes(), true, Common.toQ(cfg).leaves().eval().nodes(), true, CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
//		Sandbox sandbox = new Sandbox();
//		SandboxGraph domFrontier = DominanceAnalysis.computeSandboxedDominanceFrontier(sandbox, ucfg);
//		for(SandboxNode impliedEvent : domFrontier.forward(sandbox.nodes(events)).nodes()){
//			reverseEvents.add(impliedEvent.toAtlasNode());
//		}
//		
//		while(reverse == Integer.MAX_VALUE || (reverse--) > 0){
//			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
//			AtlasSet<Node> eventDataDependencies = ddg.getGraph().predecessors(Common.toQ(reverseEvents)).eval().nodes();
//			if(reverseEvents.size() == Common.toQ(reverseEvents).union(Common.toQ(eventDataDependencies)).eval().nodes().size()){
//				break; // fixed point
//			} else {
//				// update the set of events to include data dependencies and their implied events
//				reverseEvents.addAll(eventDataDependencies);
//				for(SandboxNode impliedEvent : domFrontier.forward(sandbox.nodes(events)).nodes()){
//					reverseEvents.add(impliedEvent.toAtlasNode());
//				}
//			}
//		}
//		return PCGFactory.create(Common.toQ(reverseEvents));
//	}
	
	public static PCG getReversePCGSlice(Q events, int forward){
		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
		return getReversePCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, forward);
	}
	
	public static PCG getForwardPCGSlice(Q events, int forward){
		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
		return getForwardPCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, forward);
	}
	
	public static PCG getForwardPCGSlice(Q cfg, Q dfg, Q events, int reverse){
		return getForwardPCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse);
	}
	
	public static PCG getForwardPCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int forward){
		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
		List<PCG> pcgs = new ArrayList<PCG>();
		pcgs.add(PCGFactory.create(Common.toQ(events), false, false));
		while(forward == Integer.MAX_VALUE || forward-pcgs.size() > 0){
			PCG lastPCG = pcgs.get(pcgs.size()-1);
			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
			Q eventDataDependencies = ddg.getGraph().successors(lastPCG.getPCG().retainNodes());
			if(lastPCG.getPCG().eval().nodes().size() == lastPCG.getPCG().union(eventDataDependencies).eval().nodes().size()){
				break; // fixed point
			} else {
				pcgs.add(PCGFactory.create(lastPCG.getPCG().union(eventDataDependencies), false, false));
			}
		}
		return pcgs.get(pcgs.size()-1);
	}
	
}
