package com.ensoftcorp.open.pcg.common;

import java.util.LinkedList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CFG;
import com.ensoftcorp.open.commons.analysis.StandardQueries;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;
import com.ensoftcorp.open.pcg.factory.PCGFactory;

public class IPCG {
	/**
	 * Construct and inter-procedural PCG for the given methods and the events of interest
	 * @param methods
	 * @param events
	 * @return
	 */
	public static Q getIPCG(Q methods, Q events){
		List<IPCGEdge> allIPCGEdges = new LinkedList<IPCGEdge>();
		//For each method in methods, get all callSites in the method. For each call site
		//find the target methods of that callSite
		for (GraphElement method: methods.eval().nodes()){
			Q callSites = Common.toQ(method).contained().nodesTaggedWithAny(XCSG.CallSite);
			
			for (GraphElement callSite: callSites.eval().nodes()){
				Q callSiteCFNode = Common.toQ(callSite).parent();
				//Find all callSite, targetMethodCfRoot pairs
				List<IPCGEdge> iPCGEdges = findMethodsForCallSites(callSiteCFNode, methods);
				if (!iPCGEdges.isEmpty()){
					//Add iPCGEdges to allIPCGEdges list
					allIPCGEdges.addAll(iPCGEdges);
					//Mark call site as an event
					events = events.union(callSiteCFNode);
					
					//Mark the targetMethodCfRoot as an event as well
					for (IPCGEdge iPCGEdge: iPCGEdges){
						
						if(iPCGEdge.toTargetMethodCFRootNode != null && !iPCGEdge.toTargetMethodCFRootNode.taggedWith(XCSG.Method)) {
							// Generally, for methods defined in the app, the control flow root node of the target method should be included as an event, 
							// so that flow through that method can be shown in the PCG. 
							// The second condition is needed because sometimes toTargetMethodCFRootNode can be a method outside the app, 
							// e.g., java.io.BufferedReader.readLine() which is in the JDK. In this case, toTargetMethodCFRootNode is not null,
							// but at the same time we don't have the control flow root node of that method in the index.
							events = events.union(Common.toQ(iPCGEdge.toTargetMethodCFRootNode));
						}
					}
				}
			}
		}
		
		//For each method, 
		//Get it's PCG with respect to events
		Q iPCG = Common.empty();
		for (GraphElement method: methods.eval().nodes()){
			Q PCG = PCGFactory.PCGforMethod(Common.toQ(method), events);
			iPCG = iPCG.union(PCG);
		}
		
		//Add an interprocedural PCG edge from CallSite event to the target methods's 
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
	
	
	public static Q getICFG(Q methods){
		Q events = Common.empty();
		List<IPCGEdge> allIPCGEdges = new LinkedList<IPCGEdge>();
		//For each method in methods, get all callSites in the method. For each call site
		//find the target methods of that callSite
		for (GraphElement method: methods.eval().nodes()){
			Q callSites = Common.toQ(method).contained().nodesTaggedWithAny(XCSG.CallSite);
			
			for (GraphElement callSite: callSites.eval().nodes()){
				Q callSiteCFNode = Common.toQ(callSite).parent();
				//Find all callSite, targetMethodCfRoot pairs
				List<IPCGEdge> iPCGEdges = findMethodsForCallSites(callSiteCFNode, methods);
				if (!iPCGEdges.isEmpty()){
					//Add iPCGEdges to allIPCGEdges list
					allIPCGEdges.addAll(iPCGEdges);
					//Mark call site as an event
					events = events.union(callSiteCFNode);
					
					//Mark the targetMethodCfRoot as an event as well
					for (IPCGEdge iPCGEdge: iPCGEdges){
						
						if(iPCGEdge.toTargetMethodCFRootNode != null && !iPCGEdge.toTargetMethodCFRootNode.taggedWith(XCSG.Method)) {
							events = events.union(Common.toQ(iPCGEdge.toTargetMethodCFRootNode));
							
							// add the method 'm' containing this event 'e' (control flow root of 'm') to the set of methods when computing the PCG
							// this is not strictly necessary. if 'm' is not added to the PCG at this point, then the iPCG will only contain
							// the control flow node containing the callsite to this method in m's caller, but not contain 'e' because 'm' was not included  
							// of course, if the input parameter 'methods' contained 'm', then it is redundant to add 'm' here.
							Q methodContainingControlFlowRootEvent = Common.toQ(StandardQueries.getContainingFunction(iPCGEdge.toTargetMethodCFRootNode));
							if(methodContainingControlFlowRootEvent != null && !CommonQueries.isEmpty(methodContainingControlFlowRootEvent)) {
								methods = methods.union(methodContainingControlFlowRootEvent);
							}
						}
					}
				}
			}
		}
		
		//For each method, 
		//Get it's CFG with respect to events
		Q iCfg = Common.empty();
		for (GraphElement method: methods.eval().nodes()){
			Q cfg = CFG.cfg(Common.toQ(method));
			iCfg = iCfg.union(cfg);
		}
		
		//Add an interprocedural CFG edge from CallSite event to the target methods's 
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
	 * Given a callSite controlFlowNode, find all targetMethods that are reachable
	 * from a forward call graph from that callSite node.
	 * @param callSite
	 * @param methods
	 * @returns a list of {callSite, controlFlowRoot} pair, where the callSite is the callSite passed to this method
	 *          and the controlFlowRoot is the root for each target method found in "methods" which can be reached
	 *          from the callSite
	 */
	private static List<IPCGEdge> findMethodsForCallSites(Q callSiteCFNode, Q methods) {
		List<IPCGEdge> PCGEdgeList = new LinkedList<IPCGEdge>();
		
		
		Q forwardCall = Common.universe().edgesTaggedWithAny(Attr.Edge.CALL, XCSG.Call).forward(callSiteCFNode);
		
		for (Node method : methods.eval().nodes()){
			Q m = Common.toQ(method);
			if (!m.intersection(forwardCall).eval().nodes().isEmpty()){
				Node cfRoot = m.contained().nodesTaggedWithAny(XCSG.controlFlowRoot).eval().nodes().getFirst();
				
				if(cfRoot != null) {
					PCGEdgeList.add(new IPCGEdge(callSiteCFNode.eval().nodes().getFirst(), cfRoot));
				} else {
					// For methods outside the indexed app, e.g., JDK methods, we don't have the control flow root node, 
					// so just add the method node itself.   
					PCGEdgeList.add(new IPCGEdge(callSiteCFNode.eval().nodes().getFirst(), method));
				}
			}
		}
		return PCGEdgeList;
	}
	
	
	//IPCG is a contactenation of multiple PCG's each with its own entry and exit nodes. 
	//This methods, removes all those entry and exit nodes and creates a single entry and exit
	//node for the IPCG.
	private static Q cleanUpMasterEntryExitNodes(Q PCG){
		
		AtlasHashSet<Node> nodesToDelete = new AtlasHashSet<Node>();
		AtlasHashSet<Edge> edgesToDelete = new AtlasHashSet<Edge>();
		
		Q PCGEdges = PCG.edgesTaggedWithAll(PCGEdge.EventFlow_Edge);
		
		
		//Cleanup MasterEntryNodes
		
		Q masterEntryNodes = PCG.nodesTaggedWithAny(PCGNode.EventFlow_Master_Entry);
		Q masterExitNodes = PCG.nodesTaggedWithAny(PCGNode.EventFlow_Master_Exit);
	
		//Get successors (filter out directly connencted exit nodes)
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
		Node toTargetMethodCFRootNode;
		
		public IPCGEdge(Node from, Node to){
			fromCallSiteCFNode = from;
			toTargetMethodCFRootNode = to;
		}
		
		private GraphElement getEdge(){
			if(fromCallSiteCFNode != null && toTargetMethodCFRootNode != null) { 
				Q PCGEdges = Common.universe().edgesTaggedWithAny(PCGEdge.EventFlow_Edge).
						betweenStep(Common.toQ(fromCallSiteCFNode), Common.toQ(toTargetMethodCFRootNode));
				if (PCGEdges.eval().edges().isEmpty()) {
					GraphElement newEdge = Graph.U.createEdge(fromCallSiteCFNode, toTargetMethodCFRootNode);
					newEdge.tag(PCGEdge.EventFlow_Edge);
					return newEdge;
				} else {
					return PCGEdges.eval().edges().getFirst();
				}
			} else {
				return null;
			}
		}
	}
}