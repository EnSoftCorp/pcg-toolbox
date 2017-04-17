package com.ensoftcorp.open.pcg.common;

import java.util.LinkedList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.StandardQueries;
import com.ensoftcorp.open.java.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;
import com.ensoftcorp.open.pcg.factory.PCGFactory;

public class IPCG2 {

	public static Q getAncestorFunctions(Q events){
		events = events.nodes(XCSG.ControlFlow_Node);
		Q eventFunctions = getFunctionsContainingEvents(events);
		Q ipcgCallGraph = getIPCGCallGraph(eventFunctions, Common.empty());
		Q ipcgFunctions = ipcgCallGraph.retainNodes();
		Q callEdges = Common.universe().edges(XCSG.Call);
		return callEdges.reverse(ipcgFunctions).difference(ipcgFunctions);
	}
	
	public static Q getExpandableFunctions(Q events, Q selectedAncestors){
		events = events.nodes(XCSG.ControlFlow_Node);
		Q eventFunctions = getFunctionsContainingEvents(events);
		Q ipcgCallGraph = getIPCGCallGraph(eventFunctions, selectedAncestors);
		Q ipcgFunctions = ipcgCallGraph.retainNodes();
		Q expandableFunctions = ipcgCallGraph.retainNodes().difference(eventFunctions);
		return expandableFunctions;
	}
	
	public static Q getIPCG(Q events, Q selectedAncestors, Q selectedExpansions){
		events = events.nodes(XCSG.ControlFlow_Node);
		Q eventFunctions = getFunctionsContainingEvents(events);
		Q ipcgCallGraph = getIPCGCallGraph(eventFunctions, selectedAncestors);
		Q ipcgFunctions = ipcgCallGraph.retainNodes();
		Q expandableFunctions = ipcgCallGraph.retainNodes().difference(eventFunctions);
		selectedExpansions = selectedExpansions.intersection(expandableFunctions);
		
		AtlasSet<Edge> ipcgEdges = new AtlasHashSet<Edge>();
		List<Graph> pcgs = new LinkedList<Graph>();
		
		// for each expanded function, get the target callsites within the ipcg
		// call graph and create corresponding ipcg event edges for each callsite
		// and finally create the pcg itself with the callsites to ipcg call graph
		// functions added as events
		Q expandedFunctions = eventFunctions.union(selectedExpansions);
		for(Node expandedFunction : expandedFunctions.eval().nodes()){
			Q expandedFunctionControlFlowNodes = Common.toQ(expandedFunction).contained().nodes(XCSG.ControlFlow_Node);
			AtlasSet<Node> expandedFunctionEvents = new AtlasHashSet<Node>();
			expandedFunctionEvents.addAll(expandedFunctionControlFlowNodes.intersection(events).eval().nodes());
			Q expandedFunctionCallsitesCF = Common.universe().nodes(XCSG.CallSite).parent().intersection(expandedFunctionControlFlowNodes);
			for(Node expandedFunctionCallsiteCF : expandedFunctionCallsitesCF.eval().nodes()){
				Q expandedFunctionCallsites  = Common.toQ(expandedFunctionCallsiteCF).children().nodes(XCSG.CallSite);
				for(Node expandedFunctionCallsite : expandedFunctionCallsites.eval().nodes()){
					Q expandedFunctionCallsiteTargets = CallSiteAnalysis.getTargetMethods(expandedFunctionCallsite);
					Q expandedFunctionCallsiteCallGraphRestrictedTargets = expandedFunctionCallsiteTargets.intersection(ipcgFunctions);
					for(Node expandedFunctionCallsiteCallGraphRestrictedTarget : expandedFunctionCallsiteCallGraphRestrictedTargets.eval().nodes()){
						expandedFunctionEvents.add(expandedFunctionCallsiteCF); // set only keeps 1 copy, ok for multiple targets
						if(CommonQueries.isEmpty(Common.toQ(expandedFunctionCallsiteCallGraphRestrictedTarget).intersection(expandedFunctions))){
							// target is a non-expanded function
							Edge ipcgEdge = getOrCreateIPCGEdge(expandedFunctionCallsiteCF, expandedFunctionCallsiteCallGraphRestrictedTarget);
							ipcgEdges.add(ipcgEdge);
						} else {
							Node cfRoot = Common.toQ(expandedFunctionCallsiteCallGraphRestrictedTarget).children().nodesTaggedWithAny(XCSG.controlFlowRoot).eval().nodes().one();
							Edge ipcgEdge = getOrCreateIPCGEdge(expandedFunctionCallsiteCF, cfRoot);
							ipcgEdges.add(ipcgEdge);
						}
					}
				}
			}
			Q pcg = PCGFactory.PCGForFunction(Common.toQ(expandedFunction), Common.toQ(expandedFunctionEvents));
			pcgs.add(pcg.eval());
		}
		
		// union all the pcgs together
		AtlasSet<Edge> resultEdges = new AtlasHashSet<Edge>();
		AtlasSet<Node> resultNodes = new AtlasHashSet<Node>();
		for(Graph pcg : pcgs){
			Q pcgQ = Common.toQ(pcg);
			pcgQ = pcgQ.difference(pcgQ.nodes(PCGNode.EventFlow_Master_Entry, PCGNode.EventFlow_Master_Exit));
			resultEdges.addAll(pcgQ.eval().edges());
			resultNodes.addAll(pcgQ.eval().nodes());
		}
		// add in the custom ipcg edges
		resultEdges.addAll(ipcgEdges);
		// create the resulting graph
		Q ipcg = Common.toQ(new UncheckedGraph(resultNodes, resultEdges));
		return ipcg.union(ipcgCallGraph);
	}
	
	public static Q getFunctionsContainingEvents(Q events){
		events = events.nodes(XCSG.ControlFlow_Node);
		return StandardQueries.getContainingFunctions(events);
	}
	
	public static Q getIPCGCallGraph(Q eventFunctions, Q selectedAncestors){
		Q callEdges = Common.universe().edges(XCSG.Call);
		selectedAncestors = callEdges.reverse(eventFunctions).intersection(selectedAncestors);
		Q ipcgFunctions = eventFunctions.union(selectedAncestors);
		Q ipcgCallGraph = ipcgFunctions.union(callEdges.between(ipcgFunctions, ipcgFunctions));
		return ipcgCallGraph;
	}
	
	private static Edge getOrCreateIPCGEdge(Node from, Node to){
		if(from == null){
			throw new IllegalArgumentException("from is null");
		}
		if(CommonQueries.isEmpty(Common.toQ(from).children().nodes(XCSG.CallSite))){
			throw new IllegalArgumentException("from \"" + from.address().toAddressString() + "\" is not a Control Flow CallSite Node");
		}
		if(to == null){
			throw new IllegalArgumentException("to is null");
		}
		if(!to.taggedWith(XCSG.Function) && !to.taggedWith(XCSG.controlFlowRoot)){
			throw new IllegalArgumentException("to \"" + to.address().toAddressString() + "\" is not a Function Node or a Control Flow Root");
		}
		Q PCGEdges = Common.universe().edgesTaggedWithAny(PCGEdge.EventFlow_Edge).
				betweenStep(Common.toQ(from), Common.toQ(to));
		if (PCGEdges.eval().edges().isEmpty()) {
			Edge intermediateEdge = Graph.U.createEdge(from, to);
			intermediateEdge.tag(PCGEdge.EventFlow_Edge);
			return intermediateEdge;
		} else {
			return PCGEdges.eval().edges().one();
		}
	}
	
}
