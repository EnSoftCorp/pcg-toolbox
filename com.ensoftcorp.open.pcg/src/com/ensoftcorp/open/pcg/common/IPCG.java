package com.ensoftcorp.open.pcg.common;

import java.util.HashMap;
import java.util.Map;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.commons.analysis.CommonQueries;

public class IPCG {
	
	public static interface IPCGEdge {
		/**
		 * Tag applied to CFG edges that are retained in the final PCG
		 */
		public static final String InterproceduralEventFlow_Edge = "InterproceduralEventFlow_Edge";
	}

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
		Q expandableFunctions = ipcgCallGraph.retainNodes().difference(eventFunctions);
		return expandableFunctions;
	}
	
	public static AtlasSet<Node> getImplicitCallsiteEvents(Q events, Q selectedAncestors, Q selectedExpansions){
		events = events.nodes(XCSG.ControlFlow_Node);
		Q eventFunctions = getFunctionsContainingEvents(events);
		Q ipcgCallGraph = getIPCGCallGraph(eventFunctions, selectedAncestors);
		Q ipcgFunctions = ipcgCallGraph.retainNodes();
		Q expandableFunctions = ipcgCallGraph.retainNodes().difference(eventFunctions);
		selectedExpansions = selectedExpansions.intersection(expandableFunctions);
		
		// for each expanded function, get the target callsites within the ipcg
		// call graph and create corresponding ipcg event edges for each callsite
		// and finally create the pcg itself with the callsites to ipcg call graph
		// functions added as events
		AtlasSet<Node> implicitCallsiteEvents = new AtlasHashSet<Node>();
		Q expandedFunctions = eventFunctions.union(selectedExpansions);
		for(Node expandedFunction : expandedFunctions.eval().nodes()){
			Q expandedFunctionControlFlowNodes = Common.toQ(expandedFunction).contained().nodes(XCSG.ControlFlow_Node);
			AtlasSet<Node> expandedFunctionEvents = new AtlasHashSet<Node>();
			expandedFunctionEvents.addAll(expandedFunctionControlFlowNodes.intersection(events).eval().nodes());
			Q expandedFunctionCallsitesCF = Common.universe().nodes(XCSG.CallSite).parent().intersection(expandedFunctionControlFlowNodes);
			for(Node expandedFunctionCallsiteCF : expandedFunctionCallsitesCF.eval().nodes()){
				Q expandedFunctionCallsites  = Common.toQ(expandedFunctionCallsiteCF).children().nodes(XCSG.CallSite);
				for(Node expandedFunctionCallsite : expandedFunctionCallsites.eval().nodes()){
					Q expandedFunctionCallsiteTargets = Common.toQ(CallSiteAnalysis.getTargets(expandedFunctionCallsite));
					Q expandedFunctionCallsiteCallGraphRestrictedTargets = expandedFunctionCallsiteTargets.intersection(ipcgFunctions);
					if(!expandedFunctionCallsiteCallGraphRestrictedTargets.eval().nodes().isEmpty()){
						implicitCallsiteEvents.add(expandedFunctionCallsiteCF);
					}
				}
			}
		}
		
		// remove explicit events
		for(Node event : events.eval().nodes()){
			implicitCallsiteEvents.remove(event);
		}
		
		return implicitCallsiteEvents;
	}
	
	public static Q getIPCG(Q events, Q selectedAncestors, Q selectedExpansions){
		return getIPCG(events, selectedAncestors, selectedExpansions, false);
	}
	
	public static Q getIPCG(Q events, Q selectedAncestors, Q selectedExpansions, boolean exceptionalControlFlow){
		events = events.nodes(XCSG.ControlFlow_Node);
		selectedAncestors = selectedAncestors.intersection(getAncestorFunctions(events));
		Q eventFunctions = getFunctionsContainingEvents(events);
		Q ipcgCallGraph = getIPCGCallGraph(eventFunctions, selectedAncestors);
		Q ipcgFunctions = ipcgCallGraph.retainNodes();
		Q expandableFunctions = ipcgCallGraph.retainNodes().difference(eventFunctions);
		selectedExpansions = selectedExpansions.intersection(expandableFunctions);
		
		AtlasSet<Edge> ipcgEdges = new AtlasHashSet<Edge>();
		Map<Node,PCG> pcgs = new HashMap<Node,PCG>();
		
		// for each expanded function, get the target callsites within the ipcg
		// call graph and create a PCG with the relevant callsites as added events
		Q expandedFunctions = eventFunctions.union(selectedExpansions);
		for(Node expandedFunction : expandedFunctions.eval().nodes()){
			Q expandedFunctionControlFlowNodes = Common.toQ(expandedFunction).contained().nodes(XCSG.ControlFlow_Node);
			if(expandedFunctionControlFlowNodes.eval().nodes().isEmpty()){
				Log.warning("Function " + CommonQueries.getQualifiedFunctionName(expandedFunction) + " has no CFG body.");
				continue;
			}
			AtlasSet<Node> expandedFunctionEvents = new AtlasHashSet<Node>();
			expandedFunctionEvents.addAll(expandedFunctionControlFlowNodes.intersection(events).eval().nodes());
			Q expandedFunctionCallsitesCF = Common.universe().nodes(XCSG.CallSite).parent().intersection(expandedFunctionControlFlowNodes);
			for(Node expandedFunctionCallsiteCF : expandedFunctionCallsitesCF.eval().nodes()){
				Q expandedFunctionCallsites  = Common.toQ(expandedFunctionCallsiteCF).children().nodes(XCSG.CallSite);
				for(Node expandedFunctionCallsite : expandedFunctionCallsites.eval().nodes()){
					Q expandedFunctionCallsiteTargets = Common.toQ(CallSiteAnalysis.getTargets(expandedFunctionCallsite));
					Q expandedFunctionCallsiteCallGraphRestrictedTargets = expandedFunctionCallsiteTargets.intersection(ipcgFunctions);
					if(!expandedFunctionCallsiteCallGraphRestrictedTargets.eval().nodes().isEmpty()){
						expandedFunctionEvents.add(expandedFunctionCallsiteCF); // set only keeps 1 copy, ok for multiple targets
					}
				}
			}
			PCG pcg = PCGFactory.create(Common.toQ(expandedFunctionEvents), exceptionalControlFlow);
			pcgs.put(pcg.getFunction(), pcg);
		}
		
		// for each PCG create ipcg event edges from each callsite
		// to the callsite target's pcg master entry
		for(Node expandedFunction : expandedFunctions.eval().nodes()){
			Q expandedFunctionControlFlowNodes = Common.toQ(expandedFunction).contained().nodes(XCSG.ControlFlow_Node);
			Q expandedFunctionCallsitesCF = Common.universe().nodes(XCSG.CallSite).parent().intersection(expandedFunctionControlFlowNodes);
			for(Node expandedFunctionCallsiteCF : expandedFunctionCallsitesCF.eval().nodes()){
				Q expandedFunctionCallsites  = Common.toQ(expandedFunctionCallsiteCF).children().nodes(XCSG.CallSite);
				for(Node expandedFunctionCallsite : expandedFunctionCallsites.eval().nodes()){
					Q expandedFunctionCallsiteTargets = Common.toQ(CallSiteAnalysis.getTargets(expandedFunctionCallsite));
					Q expandedFunctionCallsiteCallGraphRestrictedTargets = expandedFunctionCallsiteTargets.intersection(ipcgFunctions);
					for(Node expandedFunctionCallsiteCallGraphRestrictedTarget : expandedFunctionCallsiteCallGraphRestrictedTargets.eval().nodes()){
						if(CommonQueries.isEmpty(Common.toQ(expandedFunctionCallsiteCallGraphRestrictedTarget).intersection(expandedFunctions))){
							// target is a non-expanded function, we just create an edge to the function itself
							try {
								Edge ipcgEdge = getOrCreateIPCGEdge(expandedFunctionCallsiteCF, expandedFunctionCallsiteCallGraphRestrictedTarget);
								ipcgEdges.add(ipcgEdge);
							} catch (IllegalArgumentException e){
								Log.error("Error creating IPCG edge", e);
							}
						} else {
							// taget is an expanded function, we create an edge to the function's pcg master entry
							try {
								if(pcgs.containsKey(expandedFunctionCallsiteCallGraphRestrictedTarget)){
									Node masterEntry = pcgs.get(expandedFunctionCallsiteCallGraphRestrictedTarget).getMasterEntry();
									Edge ipcgEdge = getOrCreateIPCGEdge(expandedFunctionCallsiteCF, masterEntry);
									ipcgEdges.add(ipcgEdge);
								} else {
									Log.warning("PCG for function " + CommonQueries.getQualifiedFunctionName(expandedFunctionCallsiteCallGraphRestrictedTarget) + " was not computed. IPCG will be incomplete.");
								}
							} catch (IllegalArgumentException e){
								Log.error("Error creating IPCG edge", e);
							}
						}
					}
				}
			}
		}
		
		// combine all the pcgs
		AtlasSet<Edge> resultEdges = new AtlasHashSet<Edge>();
		AtlasSet<Node> resultNodes = new AtlasHashSet<Node>();
		for(PCG pcg : pcgs.values()){
			
			// add in one step of containment
			// this is because some relationships are summarized as call edges and produce disjoint graphs
			// if we don't include structural relationships at least up to the function level
			Q containedPCG = pcg.getPCG().union(Common.universe().edges(XCSG.Contains).reverseStep(pcg.getPCG()));
			Graph pcgG = containedPCG.eval();
			resultEdges.addAll(pcgG.edges());
			resultNodes.addAll(pcgG.nodes());
			
			// ipcg edges from callsites have already been added, but we need to 
			// add ipcg edges from the master exit to back up the stack to the callsite
			for(Node callsite : Common.toQ(ipcgEdges).predecessors(Common.toQ(pcg.getMasterEntry())).eval().nodes()){
				
				// for now just returning the callsite
				Edge ipcgEdge = getOrCreateIPCGEdge(pcg.getMasterExit(), callsite);
				ipcgEdges.add(ipcgEdge);
				
				// TODO: consider if its better to return to the callsites control flow successor
//				// TODO: decide how to choose a callsite successor, if we did this should the successors be considered events earlier on?
//				// what if the callsite is a condition/switch statement? there are two or more successors??
//				// what if callsite is a control flow exit? the successor is the master exit
//				Q controlFlowEdges = exceptionalControlFlow ? Common.universe().edges(XCSG.ControlFlow_Edge) : Common.universe().edges(XCSG.ControlFlow_Edge, XCSG.ExceptionalControlFlow_Edge);
//				for(Node callsiteSuccessor : controlFlowEdges.successors(Common.toQ(callsite)).eval().nodes()){
//					Edge ipcgEdge = getOrCreateIPCGEdge(pcg.getMasterExit(), callsiteSuccessor);
//					ipcgEdges.add(ipcgEdge);
//				}
			}
		}
		
		// add the ipcg nodes and edges
		for(Edge ipcgEdge : ipcgEdges){
			resultNodes.add(ipcgEdge.from());
			resultNodes.add(ipcgEdge.to());
		}
		// add in the custom ipcg edges
		resultEdges.addAll(ipcgEdges);
		
		// create the resulting graph
		Q ipcg = Common.toQ(new UncheckedGraph(resultNodes, resultEdges));
		
		// return the ipcg with the call graph
		return ipcg.union(ipcgCallGraph);
	}
	
	public static Q getFunctionsContainingEvents(Q events){
		events = events.nodes(XCSG.ControlFlow_Node);
		return CommonQueries.getContainingFunctions(events);
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
		if(to == null){
			throw new IllegalArgumentException("to is null");
		}
		
		Q pcgEdges = Common.universe().edgesTaggedWithAny(IPCGEdge.InterproceduralEventFlow_Edge).betweenStep(Common.toQ(from), Common.toQ(to));
		if (pcgEdges.eval().edges().isEmpty()) {
			Edge intermediateEdge = Graph.U.createEdge(from, to);
			intermediateEdge.tag(IPCGEdge.InterproceduralEventFlow_Edge);
			return intermediateEdge;
		} else {
			return pcgEdges.eval().edges().one();
		}
	}

}
