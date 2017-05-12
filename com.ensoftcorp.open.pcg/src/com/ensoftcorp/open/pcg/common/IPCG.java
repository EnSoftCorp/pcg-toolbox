package com.ensoftcorp.open.pcg.common;

import java.util.LinkedList;
import java.util.List;

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
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.java.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;

public class IPCG {
	
	public static interface IPCGNode {
		/**
		 * Tag applied to the newly created master entry node
		 */
		public static final String InterproceduralEventFlow_Master_Entry = "InterproceduralEventFlow_Master_Entry";
		
		/**
		 * Tag applied to the newly create master exit node
		 */
		public static final String InterproceduralEventFlow_Master_Exit = "InterproceduralEventFlow_Master_Exit";
		
		/**
		 * Tag applied to nodes that are retained in the final PCG
		 */
		public static final String InterproceduralEventFlow_Node = "InterproceduralEventFlow_Node";
		
		/**
		 * The name attribute applied to the EventFlow_Master_Entry of the PCG
		 */
		public static final String InterproceduralEventFlow_Master_Entry_Name = "\u22A4";
		
		/**
		 * The name attribute applied to the EventFlow_Master_Exit of the PCG
		 */
		public static final String InterproceduralEventFlow_Master_Exit_Name = "\u22A5";
	}
	
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
					Q expandedFunctionCallsiteTargets = CallSiteAnalysis.getTargetMethods(expandedFunctionCallsite);
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
							try {
								Edge ipcgEdge = getOrCreateIPCGEdge(expandedFunctionCallsiteCF, expandedFunctionCallsiteCallGraphRestrictedTarget);
								ipcgEdges.add(ipcgEdge);
							} catch (IllegalArgumentException e){
								Log.error("Error creating IPCG edge", e);
							}
						} else {
							try {
								Node cfRoot = Common.toQ(expandedFunctionCallsiteCallGraphRestrictedTarget).children().nodesTaggedWithAny(XCSG.controlFlowRoot).eval().nodes().one();
								Edge ipcgEdge = getOrCreateIPCGEdge(expandedFunctionCallsiteCF, cfRoot);
								ipcgEdges.add(ipcgEdge);
							} catch (IllegalArgumentException e){
								Log.error("Error creating IPCG edge", e);
							}
						}
					}
				}
			}
			Q pcg = PCG.create(Common.toQ(expandedFunctionEvents), exceptionalControlFlow);
			pcgs.add(pcg.eval());
		}
		
		// union all the pcgs together
		AtlasSet<Edge> resultEdges = new AtlasHashSet<Edge>();
		AtlasSet<Node> resultNodes = new AtlasHashSet<Node>();
		for(Graph pcg : pcgs){
			Q pcgQ = Common.toQ(pcg);
			resultEdges.addAll(pcgQ.eval().edges());
			resultNodes.addAll(pcgQ.eval().nodes());
		}
		// specifically add in the nodes for the edge since some nodes
		// (ex: intermediate callsite targets) may not be in the resulting nodes yet
		// this leads to really confusing behaviors later if not careful
		for(Edge ipcgEdge : ipcgEdges){
			resultNodes.add(ipcgEdge.from());
			resultNodes.add(ipcgEdge.to());
		}
		// add in the custom ipcg edges
		resultEdges.addAll(ipcgEdges);
		
		// create the resulting graph
		Q ipcg = Common.toQ(new UncheckedGraph(resultNodes, resultEdges));
		
		// cleanup the master entry and exit nodes
		ipcg = calculateMasterEntryExitNodes(ipcg, ipcgCallGraph);
		
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
//		boolean isCFCallsite = !CommonQueries.isEmpty(Common.toQ(from).children().nodes(XCSG.CallSite)); // a special case of CFNode
		boolean isCFNode = from.taggedWith(XCSG.ControlFlow_Node);
		boolean isMasterEntry = from.taggedWith(IPCGNode.InterproceduralEventFlow_Master_Entry);
		if(!isCFNode && !isMasterEntry){
			throw new IllegalArgumentException("from \"" + from.address().toAddressString() + "\" is not a Control Flow Node or Interprocedural EventFlow Master Entry Node");
		}
		if(to == null){
			throw new IllegalArgumentException("to is null");
		}
		
		boolean isCFRoot = to.taggedWith(XCSG.ControlFlow_Node);
		boolean isFunction = to.taggedWith(XCSG.Function);
		boolean isMasterExit = to.taggedWith(IPCGNode.InterproceduralEventFlow_Master_Exit);
		if(!isCFRoot && !isFunction && !isMasterExit){
			throw new IllegalArgumentException("to \"" + to.address().toAddressString() + "\" is not a Function Node or a Control Flow Root or Master Exit Node");
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

	/**
	 * IPCG is a concatenation of multiple PCG's each with its own entry and
	 * exit nodes. This function, removes all those entry and exit nodes and
	 * creates a single entry and exit node for the IPCG.
	 * 
	 * @param pcg
	 * @return
	 */
	private static Q calculateMasterEntryExitNodes(Q ipcg, Q callGraph){
		// get the old master entry and exit nodes
		Q masterEntryNodes = ipcg.nodesTaggedWithAny(PCGNode.EventFlow_Master_Entry);
		Q masterExitNodes = ipcg.nodesTaggedWithAny(PCGNode.EventFlow_Master_Exit);
		
		// get the call graph roots that will serve as entry points to the ipcg
		// intentionally not retaining edges here because call graph may be
		// disconnected or just contain one node
		Q ipcgEntryFunctions = callGraph.roots();

		// get the entry function event roots
		Q eventEdges = ipcg.edges(PCGEdge.EventFlow_Edge);
		Q entryFunctionEventRoots = eventEdges.successors(masterEntryNodes).intersection(CommonQueries.cfg(ipcgEntryFunctions));

		// create new master entry node
		Node ipcgMasterEntryNode = createInterproceduralMasterEntryNode();
		
		// add an edge from the master entry node to each entry function event root
		for(Node entryFunctionEventRoot : entryFunctionEventRoots.eval().nodes()){
			try {
				getOrCreateIPCGEdge(ipcgMasterEntryNode, entryFunctionEventRoot);
			} catch (IllegalArgumentException e){
				Log.error("Error creating IPCG edge", e);
			}
		}
		
		// create new master exit node
		Node ipcgMasterExitNode = createInterproceduralMasterExitNode();
		
//		// add edges to master exit from ALL pcg exits
//		for(Node callGraphFunction : callGraph.eval().nodes()){
//			Q exitEvents = eventEdges.predecessors(masterExitNodes).intersection(CommonQueries.cfg(callGraphFunction));
//			for(Node exitEvent : exitEvents.eval().nodes()){
//				Edge ipcgMasterExitEdge = getOrCreateIPCGEdge(exitEvent, ipcgMasterExitNode);
//			}
//		}
		
		// add edges to master exit from leaf function pcg exits
		for(Node callGraphFunctionLeaf : callGraph.leaves().eval().nodes()){
			Q exitEvents = eventEdges.predecessors(masterExitNodes).intersection(CommonQueries.cfg(callGraphFunctionLeaf));
			for(Node exitEvent : exitEvents.eval().nodes()){
				try {
					getOrCreateIPCGEdge(exitEvent, ipcgMasterExitNode);
				} catch (IllegalArgumentException e){
					Log.error("Error creating IPCG edge", e);
				}
			}
		}
		
		// TODO: shouldn't returns be implicit events?
		// add premature event exist edges
		// TODO: more cases than just control flow conditions and return statements to master exits?
		for(Node callGraphFunctionNonLeaf : callGraph.difference(callGraph.leaves()).eval().nodes()){
			Q controlFlowConditions = Common.universe().nodes(XCSG.ControlFlowCondition);
			Q returnStatements = Common.universe().nodes(XCSG.ReturnValue).parent(); // return CF nodes
			Q potentialEventExits = CommonQueries.cfg(callGraphFunctionNonLeaf).intersection(controlFlowConditions.union(returnStatements));
			Q exitEvents = eventEdges.predecessors(masterExitNodes).intersection(potentialEventExits);
			for(Node exitEvent : exitEvents.eval().nodes()){
				try {
					getOrCreateIPCGEdge(exitEvent, ipcgMasterExitNode);
				} catch (IllegalArgumentException e){
					Log.error("Error creating IPCG edge", e);
				}
			}
		}
		
		Q ipcgEdges = Common.universe().edges(IPCGEdge.InterproceduralEventFlow_Edge);
		Q ipcgEntryEdges = ipcgEdges.forwardStep(Common.toQ(ipcgMasterEntryNode));
		Q ipcgExitEdges = ipcgEdges.reverseStep(Common.toQ(ipcgMasterExitNode));
		
		// the final result is the original master entry/exit nodes removed with the new master entry/exit nodes and edges added
		Q result = ipcg.difference(masterEntryNodes, masterExitNodes).union(ipcgEntryEdges, ipcgExitEdges);
		return Common.toQ(result.eval());
	}

	// TODO: query if already there first, is that even possible?
	// Idea: after the fact check if the master entry node is equivalent to one that already exists
	private static Node createInterproceduralMasterEntryNode() {
		Node masterEntryNode = Graph.U.createNode();
		masterEntryNode.tag(IPCGNode.InterproceduralEventFlow_Master_Entry);
		masterEntryNode.tag(IPCGNode.InterproceduralEventFlow_Node);
		masterEntryNode.attr().put(XCSG.name, IPCGNode.InterproceduralEventFlow_Master_Entry_Name);
		return masterEntryNode;
	}
	
	// TODO: query if already there first, is that even possible?
	// Idea: after the fact check if the master exit node is equivalent to one that already exists
	private static Node createInterproceduralMasterExitNode() {
		Node masterExitNode = Graph.U.createNode();
		masterExitNode.tag(IPCGNode.InterproceduralEventFlow_Master_Exit);
		masterExitNode.tag(IPCGNode.InterproceduralEventFlow_Node);
		masterExitNode.attr().put(XCSG.name, IPCGNode.InterproceduralEventFlow_Master_Exit_Name);
		return masterExitNode;
	}
	
}
