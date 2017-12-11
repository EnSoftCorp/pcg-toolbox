package com.ensoftcorp.open.pcg.common;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.slice.analysis.ProgramDependenceGraph;

public class PCGSlice {

	public static PCG getPCGSlice(Q events, int reverse, int forward){
		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
		return getPCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, reverse, forward);
	}
	
	public static PCG getPCGSlice(Q cfg, Q dfg, Q events, int reverse, int forward){
		return getPCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse, forward);
	}
	
	public static PCG getPCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int reverse, int forward){
		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();

		// get the program dependence graph
		Q pdg = new ProgramDependenceGraph(cfg, dfg).getGraph();
		
		Q reverseSliceEvents;
		if(reverse == Integer.MAX_VALUE){
			reverseSliceEvents = pdg.reverse(Common.toQ(events));
		} else {
			reverseSliceEvents = Common.toQ(events).reverseStepOn(pdg, reverse);
		}
		
		Q forwardSliceEvents;
		if(forward == Integer.MAX_VALUE){
			forwardSliceEvents = pdg.forward(Common.toQ(events));
		} else {
			forwardSliceEvents = Common.toQ(events).forwardStepOn(pdg, forward);
		}
		
		Q sliceEvents = Common.toQ(events).union(reverseSliceEvents, forwardSliceEvents);
		return PCGFactory.create(Common.toQ(cfg), sliceEvents);
	}
	
	// what follows below is a bunch of code that was written to convince myself that 
	// iteratively computing PCGs by selecting data dependencies is actually a roundabout
	// way to compute a program slice. Instead of computing a PCG in each iteration the
	// event selection can be expressed solely in terms of data dependence and dominance
	// even this however is not as efficient as computing control dependence directly
	// One outcome and the reason this class still exists is that presenting the slice
	// results as a PCG is much more comprehensible than presenting slice results as a
	// subset of the program dependence graph. The PCG captures the flow ordering information
	// from the underlying CFG which is more digestable for a human. 
	
//	public static Q getSliceGraph(Node function){
//		Q cfg = CommonQueries.cfg(function);
//		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg.eval(), cfg.roots().eval().nodes(), true, cfg.leaves().eval().nodes(), true, CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
//		Q domFrontier = Common.toQ(DominanceAnalysis.computeDominanceFrontier(ucfg));
//		Q dfg = CommonQueries.dfg(function);
//		DataDependenceGraph ddg = new DataDependenceGraph(dfg.eval());
//		return domFrontier.union(ddg.getGraph());
//	}
//	
//	public static PCG getPCGSlice(Q events, int reverse, int forward){
//		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
//		return getPCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, reverse, forward);
//	}
//	
//	public static PCG getPCGSlice(Q cfg, Q dfg, Q events, int reverse, int forward){
//		return getPCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse, forward);
//	}
//	
//	public static PCG getPCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int reverse, int forward){
//		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
//		
//		// compute dominance and add implied events
//		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg, Common.toQ(cfg).roots().eval().nodes(), true, Common.toQ(cfg).leaves().eval().nodes(), true, CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
//		Sandbox sandbox = new Sandbox();
//		sandbox.addGraph(cfg);
//		sandbox.addGraph(dfg);
//		SandboxGraph domFrontier = DominanceAnalysis.computeSandboxedDominanceFrontier(sandbox, ucfg);
//		
//		// compute data dependence graph
//		DataDependenceGraph ddg = new DataDependenceGraph(dfg);
//		
//		// get reverse events
//		AtlasSet<Node> reverseEvents = new AtlasHashSet<Node>(events);
//		if(reverse > 1){
//			reverseEvents.addAll(getSliceEvents(reverse, sandbox, domFrontier, ddg, events, true));
//		}
//		
//		// get forward events
//		AtlasSet<Node> forwardEvents = new AtlasHashSet<Node>(events);
//		if(forward > 1){
//			forwardEvents.addAll(getSliceEvents(forward, sandbox, domFrontier, ddg, events, false));
//		}
//		
//		Q sliceEvents = Common.toQ(events).union(Common.toQ(reverseEvents), Common.toQ(forwardEvents));
//		return PCGFactory.create(Common.toQ(cfg), sliceEvents);
//	}
//
//	private static AtlasSet<Node> getSliceEvents(int steps, Sandbox sandbox, SandboxGraph domFrontier, DataDependenceGraph ddg, AtlasSet<Node> explicitEvents, boolean reverse) {
//		AtlasSet<Node> impliedEvents = new AtlasHashSet<Node>(explicitEvents);
//		impliedEvents.addAll(getImpliedEvents(sandbox, domFrontier, explicitEvents));
//		while(steps == Integer.MAX_VALUE || (steps--) > 1){
//			AtlasSet<Node> eventDataDependencies = reverse ? ddg.getGraph().predecessors(Common.toQ(impliedEvents)).eval().nodes() : ddg.getGraph().successors(Common.toQ(impliedEvents)).eval().nodes();
//			if(impliedEvents.size() == Common.toQ(impliedEvents).union(Common.toQ(eventDataDependencies)).eval().nodes().size()){
//				break; // fixed point
//			} else {
//				// update the set of events to include data dependencies and their implied events
//				impliedEvents.addAll(eventDataDependencies);
//				for(Node impliedEvent : getImpliedEvents(sandbox, domFrontier, impliedEvents)){
//					impliedEvents.add(impliedEvent);
//				}
//			}
//		}
//		return impliedEvents;
//	}
//
//	private static AtlasSet<Node> getImpliedEvents(Sandbox sandbox, SandboxGraph domFrontier, AtlasSet<Node> events) {
//		AtlasSet<Node> impliedEvents = new AtlasHashSet<Node>();
//		SandboxHashSet<SandboxNode> fontier = domFrontier.forward(sandbox.nodes(events)).nodes();
//		for(SandboxNode impliedEvent : fontier){
//			impliedEvents.add(impliedEvent.toAtlasNode());
//		}
//		return impliedEvents;
//	}
//	
//	public static PCG getReversePCGSlice(Q cfg, Q dfg, Q events, int reverse){
//		return getReversePCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse);
//	}
//	
//	public static PCG getReversePCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int reverse){
//		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
//		List<PCG> pcgs = new ArrayList<PCG>();
//		pcgs.add(PCGFactory.create(Common.toQ(events)));
//		while(reverse == Integer.MAX_VALUE || reverse-pcgs.size() > 0){
//			PCG lastPCG = pcgs.get(pcgs.size()-1);
//			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
//			Q eventDataDependencies = ddg.getGraph().predecessors(lastPCG.getPCG().retainNodes());
//			if(lastPCG.getPCG().eval().nodes().size() == lastPCG.getPCG().union(eventDataDependencies).eval().nodes().size()){
//				break; // fixed point
//			} else {
//				pcgs.add(PCGFactory.create(Common.toQ(cfg), lastPCG.getPCG().union(eventDataDependencies)));
//			}
//		}
//		return pcgs.get(pcgs.size()-1);
//	}
//	
//	public static PCG getReversePCGSlice(Q events, int forward){
//		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
//		return getReversePCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, forward);
//	}
//	
//	public static PCG getForwardPCGSlice(Q events, int forward){
//		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
//		return getForwardPCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, forward);
//	}
//	
//	public static PCG getForwardPCGSlice(Q cfg, Q dfg, Q events, int reverse){
//		return getForwardPCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse);
//	}
//	
//	public static PCG getForwardPCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int forward){
//		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
//		List<PCG> pcgs = new ArrayList<PCG>();
//		pcgs.add(PCGFactory.create(Common.toQ(events)));
//		while(forward == Integer.MAX_VALUE || forward-pcgs.size() > 0){
//			PCG lastPCG = pcgs.get(pcgs.size()-1);
//			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
//			Q eventDataDependencies = ddg.getGraph().successors(lastPCG.getPCG().retainNodes());
//			if(lastPCG.getPCG().eval().nodes().size() == lastPCG.getPCG().union(eventDataDependencies).eval().nodes().size()){
//				break; // fixed point
//			} else {
//				pcgs.add(PCGFactory.create(Common.toQ(cfg), lastPCG.getPCG().union(eventDataDependencies)));
//			}
//		}
//		return pcgs.get(pcgs.size()-1);
//	}
	
}
