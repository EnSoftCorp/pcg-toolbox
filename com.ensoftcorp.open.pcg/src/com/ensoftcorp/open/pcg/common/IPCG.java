package com.ensoftcorp.open.pcg.common;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.LinkedList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CFG;
import com.ensoftcorp.open.commons.analysis.StandardQueries;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;
import com.ensoftcorp.open.pcg.factory.PCGFactory;

public class IPCG {
	/**
	 * Construct and inter-procedural PCG for the given functions and the events of interest
	 * @param functions
	 * @param events
	 * @return
	 */
	public static Q getIPCG(Q functions, Q events){
		List<IPCGEdge> allIPCGEdges = new LinkedList<IPCGEdge>();
		//For each function in functions, get all callSites in the function. For each call site
		//find the target functions of that callSite
		for (GraphElement function: functions.eval().nodes()){
			Q callSites = Common.toQ(function).contained().nodesTaggedWithAny(XCSG.CallSite);
			
			for (GraphElement callSite: callSites.eval().nodes()){
				Q callSiteCFNode = Common.toQ(callSite).parent();
				//Find all callSite, targetFunctionCfRoot pairs
				List<IPCGEdge> iPCGEdges = findFunctionsForCallSites(callSiteCFNode, functions);
				if (!iPCGEdges.isEmpty()){
					//Add iPCGEdges to allIPCGEdges list
					allIPCGEdges.addAll(iPCGEdges);
					//Mark call site as an event
					events = events.union(callSiteCFNode);
					
					//Mark the targetFunctionCfRoot as an event as well
					for (IPCGEdge iPCGEdge: iPCGEdges){
						
						if(iPCGEdge.toTargetFunctionCFRootNode != null && !iPCGEdge.toTargetFunctionCFRootNode.taggedWith(XCSG.Function)) {
							// Generally, for functions defined in the app, the control flow root node of the target function should be included as an event, 
							// so that flow through that function can be shown in the PCG. 
							// The second condition is needed because sometimes toTargetFunctionCFRootNode can be a function outside the app, 
							// e.g., java.io.BufferedReader.readLine() which is in the JDK. In this case, toTargetFunctionCFRootNode is not null,
							// but at the same time we don't have the control flow root node of that function in the index.
							events = events.union(Common.toQ(iPCGEdge.toTargetFunctionCFRootNode));
						}
					}
				}
			}
		}
		
		//For each function, 
		//Get it's PCG with respect to events
		Q iPCG = Common.empty();
		for (GraphElement function: functions.eval().nodes()){
			Q PCG = PCGFactory.PCGForFunction(Common.toQ(function), events);
			iPCG = iPCG.union(PCG);
		}
		
		//Add an interprocedural PCG edge from CallSite event to the target functions's 
	    //master control flow node.
		for (IPCGEdge edge: allIPCGEdges){
			if(edge != null && edge.getEdge() != null) {
				iPCG = iPCG.union(Common.toQ(edge.getEdge()));
			}
		}
		
		//Create a single master entry/exit nodes and delete all previous
		//master/exit nodes. 
		iPCG = cleanUpMasterEntryExitNodes(iPCG);
		
		return iPCG;
	}
	
	
	public static Q getICFG(Q functions){
		Q events = Common.empty();
		List<IPCGEdge> allIPCGEdges = new LinkedList<IPCGEdge>();
		//For each function in functions, get all callSites in the function. For each call site
		//find the target functions of that callSite
		for (GraphElement function: functions.eval().nodes()){
			Q callSites = Common.toQ(function).contained().nodesTaggedWithAny(XCSG.CallSite);
			
			for (GraphElement callSite: callSites.eval().nodes()){
				Q callSiteCFNode = Common.toQ(callSite).parent();
				//Find all callSite, targetFunctionCfRoot pairs
				List<IPCGEdge> iPCGEdges = findFunctionsForCallSites(callSiteCFNode, functions);
				if (!iPCGEdges.isEmpty()){
					//Add iPCGEdges to allIPCGEdges list
					allIPCGEdges.addAll(iPCGEdges);
					//Mark call site as an event
					events = events.union(callSiteCFNode);
					
					//Mark the targetFunctionCfRoot as an event as well
					for (IPCGEdge iPCGEdge: iPCGEdges){
						
						if(iPCGEdge.toTargetFunctionCFRootNode != null && !iPCGEdge.toTargetFunctionCFRootNode.taggedWith(XCSG.Function)) {
							events = events.union(Common.toQ(iPCGEdge.toTargetFunctionCFRootNode));
							
							// add the function 'm' containing this event 'e' (control flow root of 'm') to the set of functions when computing the PCG
							// this is not strictly necessary. if 'm' is not added to the PCG at this point, then the iPCG will only contain
							// the control flow node containing the callsite to this function in m's caller, but not contain 'e' because 'm' was not included  
							// of course, if the input parameter 'functions' contained 'm', then it is redundant to add 'm' here.
							Q functionContainingControlFlowRootEvent = Common.toQ(StandardQueries.getContainingFunction(iPCGEdge.toTargetFunctionCFRootNode));
							if(functionContainingControlFlowRootEvent != null && !CommonQueries.isEmpty(functionContainingControlFlowRootEvent)) {
								functions = functions.union(functionContainingControlFlowRootEvent);
							}
						}
					}
				}
			}
		}
		
		//For each function, 
		//Get it's CFG with respect to events
		Q iCfg = Common.empty();
		for (GraphElement function: functions.eval().nodes()){
			Q cfg = CFG.cfg(Common.toQ(function));
			iCfg = iCfg.union(cfg);
		}
		
		//Add an interprocedural CFG edge from CallSite event to the target functions's 
	    //master control flow node.
		for (IPCGEdge edge: allIPCGEdges){
			if(edge != null && edge.getEdge() != null) {
				iCfg = iCfg.union(Common.toQ(edge.getEdge()));
			}
		}
		
		//Create a single master entry/exit nodes and delete all previous
		//master/exit nodes. 
		iCfg = cleanUpMasterEntryExitNodes(iCfg);
		
		return iCfg;
	}
	
	/**
	 * Given a callSite controlFlowNode, find all targetFunctions that are reachable
	 * from a forward call graph from that callSite node.
	 * @param callSite
	 * @param functions
	 * @returns a list of {callSite, controlFlowRoot} pair, where the callSite is the callSite passed to this function
	 *          and the controlFlowRoot is the root for each target function found in "functions" which can be reached
	 *          from the callSite
	 */
	private static List<IPCGEdge> findFunctionsForCallSites(Q callsiteCFNode, Q functions) {
		List<IPCGEdge> PCGEdgeList = new LinkedList<IPCGEdge>();

		AtlasSet<Node> forwardCallFromTarget = new AtlasHashSet<Node>();
		
		// TODO: resolve dynamic dispatches in java, for now just cheating with a forward from CF node on per control call edges
		// this could be satisfied with CallSiteAnalysis.getTargetMethods in JavaCommonsToolbox
		// ...but can we do this agnostic of the language...without PER_CONTROL_FLOW CALL edges...
		Q perControlCallEdges = Common.universe().edgesTaggedWithAny(Attr.Edge.CALL, XCSG.Call);
		forwardCallFromTarget.addAll(perControlCallEdges.forward(callsiteCFNode).eval().nodes());
		
		// this is the start of the proper way to do it and works for C (except for function pointer calls...)
		// however for Java it could pull in this like Java interface methods
		// ...so only do it if the forwardCallFromTarget just contains the original function
		// so this really only come into play for C codes since Java will have the Attr.Edge.CALL edges
		if(forwardCallFromTarget.size() == 1){
			Q callsiteDFNode = callsiteCFNode.contained().nodesTaggedWithAny(XCSG.CallSite);
			Q callsiteTarget = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).forwardStep(callsiteDFNode);
			forwardCallFromTarget.addAll(Common.universe().edgesTaggedWithAny(XCSG.Call).forward(callsiteTarget).eval().nodes());
		}
		
		for (Node function : functions.eval().nodes()){
			if (forwardCallFromTarget.contains(function)){
				Node cfRoot = Common.toQ(function).contained().nodesTaggedWithAny(XCSG.controlFlowRoot).eval().nodes().one();
				if(cfRoot != null) {
					PCGEdgeList.add(new IPCGEdge(callsiteCFNode.eval().nodes().one(), cfRoot));
				} else {
					// For functions outside the indexed app, e.g., standard library functions, we don't have the control flow root node, 
					// so just add the function node itself.   
					PCGEdgeList.add(new IPCGEdge(callsiteCFNode.eval().nodes().one(), function));
				}
			}
		}
		return PCGEdgeList;
	}
	
	
	//IPCG is a concatenation of multiple PCG's each with its own entry and exit nodes. 
	//This functions, removes all those entry and exit nodes and creates a single entry and exit
	//node for the IPCG.
	private static Q cleanUpMasterEntryExitNodes(Q PCG){
		
		AtlasHashSet<Node> nodesToDelete = new AtlasHashSet<Node>();
		AtlasHashSet<Edge> edgesToDelete = new AtlasHashSet<Edge>();
		
		Q PCGEdges = PCG.edgesTaggedWithAll(PCGEdge.EventFlow_Edge);
		
		
		//Cleanup MasterEntryNodes
		
		Q masterEntryNodes = PCG.nodesTaggedWithAny(PCGNode.EventFlow_Master_Entry);
		Q masterExitNodes = PCG.nodesTaggedWithAny(PCGNode.EventFlow_Master_Exit);
	
		//Get successors (filter out directly connected exit nodes)
		Q successorsWithOrigin = PCGEdges.forwardStep(masterEntryNodes).difference(masterExitNodes);
		
		//Delete old masterEntryNodes and edges
		for (GraphElement node: masterEntryNodes.eval().nodes()){
			nodesToDelete.add(node);
		}
		
		for (GraphElement edge: successorsWithOrigin.eval().edges()){
			edgesToDelete.add(edge);
		}
		
		//Create new Master Entry Node
		Node newMasterEntryNode = Graph.U.createNode();
		newMasterEntryNode.tag(PCGNode.EventFlow_Master_Entry);
		newMasterEntryNode.tag(PCGNode.EventFlow_Node);
		newMasterEntryNode.attr().put(XCSG.name, PCGNode.EventFlow_Master_Entry_Name);
		
		PCG = PCG.union(Common.toQ(newMasterEntryNode));
		
		//Connect new Master entry node to successors of old node 
		Q successors = successorsWithOrigin.difference(masterEntryNodes);
		for (GraphElement node: successors.eval().nodes()){
			Edge edge = Graph.U.createEdge(newMasterEntryNode, node);
			edge.tag(PCGEdge.EventFlow_Edge);
			PCG = PCG.union(Common.toQ(edge));
		}

		
		//Cleanup MasterExitNodes
		PCGEdges = PCG.edgesTaggedWithAll(PCGEdge.EventFlow_Edge);

		Q predecessorsWithOrigin = PCGEdges.reverseStep(masterExitNodes).difference(masterEntryNodes);
		
		
		//Delete old masterExitNodes and edges
		for (GraphElement node: masterExitNodes.eval().nodes()){
			nodesToDelete.add(node);
		}
		
		for (GraphElement edge: predecessorsWithOrigin.eval().edges()){
			edgesToDelete.add(edge);
		}
		
		//Create new Master Exit Node
		Node newMasterExitNode = Graph.U.createNode();
		newMasterExitNode.tag(PCGNode.EventFlow_Master_Exit);
		newMasterExitNode.attr().put(XCSG.name, PCGNode.EventFlow_Master_Exit_Name);
		PCG = PCG.union(Common.toQ(newMasterExitNode));

		
		//Connect predecessors of old node to new Master exit node
		Q predecessors = predecessorsWithOrigin.difference(masterExitNodes);
		for (GraphElement node: predecessors.eval().nodes()){
			Edge edge = Graph.U.createEdge(node, newMasterExitNode);
			edge.tag(PCGEdge.EventFlow_Edge);
			PCG = PCG.union(Common.toQ(edge));

		}

		PCG = removeFromQ(PCG, nodesToDelete, edgesToDelete);
		return PCG;
	}
	
	
	
	public static Q removeFromQ(Q q, AtlasHashSet<Node> nodesToRemove, AtlasHashSet<Edge> edgesToRemove){

		//Create new AtlasHashSet and remove the nodes and edges
	  AtlasHashSet<Node> nodes = new AtlasHashSet<Node>(q.eval().nodes());
	  for (Node node: nodesToRemove){
		  nodes.remove(node);
	  }
	  
	  AtlasHashSet<Edge> edges = new AtlasHashSet<Edge>(q.eval().edges());
	  for (Edge edge: edgesToRemove){
		  edges.remove(edge);
	  }
	  /*
	  //Delete nodes and edges from universe
	  for (Edge edge: edgesToRemove){
		  Graph.U.delete(edge);
	  }
	  for(Node node: nodesToRemove){
		  Graph.U.delete(node);
	  }*/
	  
	  return Common.toQ(new UncheckedGraph(nodes, edges));
	}
	
	

	public static class IPCGEdge {
		Node fromCallSiteCFNode;
		Node toTargetFunctionCFRootNode;
		
		public IPCGEdge(Node from, Node to){
			fromCallSiteCFNode = from;
			toTargetFunctionCFRootNode = to;
		}
		
		private GraphElement getEdge(){
			if(fromCallSiteCFNode != null && toTargetFunctionCFRootNode != null) { 
				Q PCGEdges = Common.universe().edgesTaggedWithAny(PCGEdge.EventFlow_Edge).
						betweenStep(Common.toQ(fromCallSiteCFNode), Common.toQ(toTargetFunctionCFRootNode));
				if (PCGEdges.eval().edges().isEmpty()) {
					GraphElement newEdge = Graph.U.createEdge(fromCallSiteCFNode, toTargetFunctionCFRootNode);
					newEdge.tag(PCGEdge.EventFlow_Edge);
					return newEdge;
				} else {
					return PCGEdges.eval().edges().one();
				}
			} else {
				return null;
			}
		}
	}
}