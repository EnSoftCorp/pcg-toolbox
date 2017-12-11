package com.ensoftcorp.open.pcg.common;

import java.util.ArrayList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.algorithms.DominanceAnalysis;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitControlFlowGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.preferences.CommonsPreferences;
import com.ensoftcorp.open.commons.sandbox.Sandbox;
import com.ensoftcorp.open.commons.sandbox.SandboxGraph;
import com.ensoftcorp.open.commons.sandbox.SandboxNode;
import com.ensoftcorp.open.slice.analysis.DataDependenceGraph;

public class PCGSlice {

	public static PCG getPCGSlice(Q events, int reverse, int forward){
		Node function = CommonQueries.getContainingFunction(events.eval().nodes().one());
		return getPCGSlice(CommonQueries.cfg(function), CommonQueries.dfg(function), events, reverse, forward);
	}
	
	public static PCG getPCGSlice(Q cfg, Q dfg, Q events, int reverse, int forward){
		return getPCGSlice(cfg.eval(), dfg.eval(), events.eval().nodes(), reverse, forward);
	}
	
	public static PCG getPCGSlice(Graph cfg, Graph dfg, AtlasSet<Node> events, int reverse, int forward){
		boolean preConditions = reverse >= 1;
		boolean postConditions = forward >= 1;
		events = Common.toQ(events).intersection(Common.toQ(cfg)).nodes(XCSG.ControlFlow_Node).eval().nodes();
		
		// compute dominance and add implied events
		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg, Common.toQ(cfg).roots().eval().nodes(), true, Common.toQ(cfg).leaves().eval().nodes(), true, CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
		Sandbox sandbox = new Sandbox();
		sandbox.addGraph(cfg);
		sandbox.addGraph(dfg);
		SandboxGraph domFrontier = DominanceAnalysis.computeSandboxedDominanceFrontier(sandbox, ucfg);
		
		// compute data dependence graph
		DataDependenceGraph ddg = new DataDependenceGraph(dfg);
		
		// get reverse events
		AtlasSet<Node> reverseEvents = new AtlasHashSet<Node>(events);
		if(reverse > 1){
			for(SandboxNode impliedEvent : domFrontier.forward(sandbox.nodes(reverseEvents)).nodes()){
				reverseEvents.add(impliedEvent.toAtlasNode());
			}
			while(reverse == Integer.MAX_VALUE || (reverse--) > 1){
				AtlasSet<Node> eventDataDependencies = ddg.getGraph().predecessors(Common.toQ(reverseEvents)).eval().nodes();
				if(reverseEvents.size() == Common.toQ(reverseEvents).union(Common.toQ(eventDataDependencies)).eval().nodes().size()){
					break; // fixed point
				} else {
					// update the set of events to include data dependencies and their implied events
					reverseEvents.addAll(eventDataDependencies);
					for(SandboxNode impliedEvent : domFrontier.forward(sandbox.nodes(reverseEvents)).nodes()){
						reverseEvents.add(impliedEvent.toAtlasNode());
					}
				}
			}
		}
		
		// get forward events
		AtlasSet<Node> forwardEvents = new AtlasHashSet<Node>(events);
		if(forward > 1){
			for(SandboxNode impliedEvent : domFrontier.reverse(sandbox.nodes(forwardEvents)).nodes()){
				forwardEvents.add(impliedEvent.toAtlasNode());
			}
			while(forward == Integer.MAX_VALUE || (forward--) > 1){
				AtlasSet<Node> eventDataDependencies = ddg.getGraph().successors(Common.toQ(forwardEvents)).eval().nodes();
				if(forwardEvents.size() == Common.toQ(forwardEvents).union(Common.toQ(eventDataDependencies)).eval().nodes().size()){
					break; // fixed point
				} else {
					// update the set of events to include data dependencies and their implied events
					forwardEvents.addAll(eventDataDependencies);
					for(SandboxNode impliedEvent : domFrontier.reverse(sandbox.nodes(forwardEvents)).nodes()){
						forwardEvents.add(impliedEvent.toAtlasNode());
					}
				}
			}
		}
		
		Q sliceEvents = Common.toQ(events).union(Common.toQ(reverseEvents), Common.toQ(forwardEvents));
		return PCGFactory.create(Common.toQ(cfg), sliceEvents, preConditions, postConditions);
	}
	
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
		pcgs.add(PCGFactory.create(Common.toQ(events)));
		while(forward == Integer.MAX_VALUE || forward-pcgs.size() > 0){
			PCG lastPCG = pcgs.get(pcgs.size()-1);
			DataDependenceGraph ddg = new DataDependenceGraph(dfg);
			Q eventDataDependencies = ddg.getGraph().successors(lastPCG.getPCG().retainNodes());
			if(lastPCG.getPCG().eval().nodes().size() == lastPCG.getPCG().union(eventDataDependencies).eval().nodes().size()){
				break; // fixed point
			} else {
				pcgs.add(PCGFactory.create(lastPCG.getPCG().union(eventDataDependencies)));
			}
		}
		return pcgs.get(pcgs.size()-1);
	}
	
}
