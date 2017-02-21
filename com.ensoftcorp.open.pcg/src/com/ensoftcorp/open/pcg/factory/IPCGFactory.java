package com.ensoftcorp.open.pcg.factory;

import java.awt.Color;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.pcg.common.IPCG;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;

public class IPCGFactory {
	
	/**
	 * Constructs the Inter-procedural PCG by including following events: 
	 * Callsites of the following methods - union of the LEAVES of pairwise intersections of the reverse call graphs of (m1, m2) for each m1 and m2 in M
	 * LCCA stands for Lowest Common Call Ancestor, i.e., the LEAVES of the intersection of reverse call chains of each pair (m1, m2) of methods.  
	 * 
	 * @param methods set of methods M
	 * @param events
	 * @return
	 */
	public static Q getIPCGIncludingPairwiseLCCAs(Q methods, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		
		// Find the union of the leaves of the pairwise intersection of reverse call graphs from the methods
		for (GraphElement method1: methods.eval().nodes()){
			Q reverseCallForMethod1 = Common.toQ(method1).reverseOn(callEdges);
			for (GraphElement method2: methods.eval().nodes()){
				if(!method1.equals(method2)) {
					Q reverseCallForMethod2 = Common.toQ(method2).reverseOn(callEdges);
					
					// Intersection of the reverse call graphs of the pair of methods
					Q intersectionOfReverseCallGraphs = reverseCallForMethod1.intersection(reverseCallForMethod2);
					
					// Add the leaves from the pairwise intersection to the set of methods considered for creating IPCG
					methods = methods.union(intersectionOfReverseCallGraphs.leaves());
					// TODO : Leaves may have cycles. In that case, add all the methods in the leaf SCC to methods  
				}
			}
		}
		Q iPCG = IPCG.getIPCG(methods, events);
		Markup m = new Markup();
		Q iPCGEdgesToExit = Common.empty();
		for(GraphElement ge : iPCG.eval().edges()) {
			GraphElement to = ge.getNode(EdgeDirection.TO);
			if(to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesToExit = iPCGEdgesToExit.union(Common.toQ(ge));
			}
		}
		m.setEdge(iPCGEdgesToExit, MarkupProperty.EDGE_COLOR, Color.GRAY);
		return iPCG;
	}
	
	/**
	 * Constructs the Inter-procedural PCG using two given sets of methods M1, M2, and the following events:
	 * Callsites to methods in the union of the pairwise intersection of the reverse call graphs of (m1, m2) for each m1 and m2 in M1 and M2 respectively
	 * The computation is restricted to the forward call graph from entryMethods.
	 * CCA stands for Common Call Ancestor, i.e., the intersection of reverse call chains of each pair (m1, m2) of methods.  
	 * 
	 * @param entryMethods 
	 * @param methods1 first set of methods M1
	 * @param methods2 second set of methos M2
	 * @param events
	 * @return
	 */
	public static Q getIPCGIncludingPairwiseCCAs(Q entryMethods, Q methods1, Q methods2, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		Q methods = methods1.union(methods2);
		
		// Find the union of the pairwise intersection of reverse call graphs from the methods
		for (GraphElement method1: methods1.eval().nodes()){
			Q reverseCallForMethod1 = Common.toQ(method1).reverseOn(callEdges);
			for (GraphElement method2: methods2.eval().nodes()){
				if(!method1.equals(method2)) {
					Q reverseCallForMethod2 = Common.toQ(method2).reverseOn(callEdges);
					
					// Intersection of the reverse call graphs from the 2 sets of methods
					Q intersectionOfReverseCallGraphs = reverseCallForMethod1.union(reverseCallForMethod2);

					if(entryMethods != null && !CommonQueries.isEmpty(entryMethods)) {
						// Entry point is specified, so restrict the intersection of reverse call graphs 
						// further to those reachable from the entry point 
						methods = methods.union(entryMethods.forwardOn(callEdges).intersection(intersectionOfReverseCallGraphs));
					} else {
						// Entry point not specified, so just take the intersection of reverse call graphs 
						methods = methods.union(intersectionOfReverseCallGraphs);
					}
				}
			}
		}
		Q iPCG = IPCG.getIPCG(methods, events);
		Markup m = new Markup();
		Q iPCGEdgesToExit = Common.empty();
		for(GraphElement ge : iPCG.eval().edges()) {
			GraphElement to = ge.getNode(EdgeDirection.TO);
			if(to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesToExit = iPCGEdgesToExit.union(Common.toQ(ge));
			}
		}
		m.setEdge(iPCGEdgesToExit, MarkupProperty.EDGE_COLOR, Color.GRAY);
		return iPCG;
	}
	
	/**
	 * Constructs the Inter-procedural PCG using two given sets of methods M1, M2, and the following events:
	 * Callsites to methods in the union of the reverse call graphs of (m1, m2) for each m1 and m2 in M1 and M2 respectively
	 * The computation is restricted to the forward call graph from entryMethods.
	 * "Interactions" as opposed to "Common Call Ancestors" means that the union of reverse call graphs is taken, rather than their intersection.  
	 * 
	 * @param entryMethods
	 * @param methods1
	 * @param methods2
	 * @param events
	 * @return
	 */
	public static Q getIPCGFromInteractions(Q entryMethods, Q methods1, Q methods2, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		Q rev1 = methods1.reverseOn(callEdges);
		Q rev2 = methods2.reverseOn(callEdges);
		
		Q revCalls = rev1.union(rev2);
		Q methods = Common.empty();
		if(entryMethods != null && !CommonQueries.isEmpty(entryMethods)) {
			// Entry point is specified, so restrict the intersection of reverse call graphs 
			// further to those reachable from the entry point 
			if(CommonQueries.isEmpty(revCalls)) {
				methods = entryMethods.forwardOn(callEdges);
			} 
			methods = revCalls.intersection(entryMethods.forwardOn(callEdges));
		}
		Q iPCG = IPCG.getIPCG(methods.nodesTaggedWithAny(XCSG.Method), events);
		
		Markup m = new Markup();
		Q iPCGEdgesToExit = Common.empty();
		for(GraphElement ge : iPCG.eval().edges()) {
			GraphElement to = ge.getNode(EdgeDirection.TO);
			if(to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesToExit = iPCGEdgesToExit.union(Common.toQ(ge));
			}
		}
		m.setEdge(iPCGEdgesToExit, MarkupProperty.EDGE_COLOR, Color.GRAY);
		return iPCG;
	}
	
	/**
	 * Constructs the Inter-procedural CFG using CFGs of two given sets of methods M1, M2, and the methods containing the following events:
	 * Callsites to methods in the union of the reverse call graphs of (m1, m2) for each m1 and m2 in M1 and M2 respectively
	 * The computation is restricted to the forward call graph from entryMethods.
	 * "Interactions" as opposed to "Common Call Ancestors" means that the union of reverse call graphs is taken, rather than their intersection.  
	 * 
	 * @param entryMethods
	 * @param methods1
	 * @param methods2
	 * @param events
	 * @return
	 */
	public static Q getICFGFromInteractions(Q entryMethods, Q methods1, Q methods2, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		Q rev1 = methods1.reverseOn(callEdges);
		Q rev2 = methods2.reverseOn(callEdges);
		
		Q revCalls = rev1.union(rev2);
		
		Q methods = Common.empty();
		if(entryMethods != null && !CommonQueries.isEmpty(entryMethods)) {
			// Entry point is specified, so restrict the reverse calls only 
			// to those reachable from the entry point	 
			methods = revCalls.intersection(entryMethods.forwardOn(callEdges));
		}
		
		Q icfg = IPCG.getICFG(methods.nodesTaggedWithAny(XCSG.Method));
		
		Markup m = new Markup();
		Q iPCGEdgesToExit = Common.empty();
		for(GraphElement ge : icfg.eval().edges()) {
			GraphElement to = ge.getNode(EdgeDirection.TO);
			if(to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesToExit = iPCGEdgesToExit.union(Common.toQ(ge));
			}
		}
		m.setEdge(iPCGEdgesToExit, MarkupProperty.EDGE_COLOR, Color.GRAY);
		return icfg;
	}
}
