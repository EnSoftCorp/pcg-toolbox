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
import com.ensoftcorp.open.commons.analysis.StandardQueries;
import com.ensoftcorp.open.pcg.common.IPCG;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;

public class IPCGFactory {
	
	/**
	 * Constructs the Inter-procedural PCG by including following events: 
	 * Callsites of the following functions - union of the LEAVES of pairwise intersections of the reverse call graphs of (m1, m2) for each m1 and m2 in M
	 * LCCA stands for Lowest Common Call Ancestor, i.e., the LEAVES of the intersection of reverse call chains of each pair (m1, m2) of functions.  
	 * 
	 * @param functions set of functions M
	 * @param events
	 * @return
	 */
	public static Q getIPCGIncludingPairwiseLCCAs(Q functions, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		
		// Find the union of the leaves of the pairwise intersection of reverse call graphs from the functions
		for (GraphElement function1: functions.eval().nodes()){
			Q reverseCallForFunction1 = Common.toQ(function1).reverseOn(callEdges);
			for (GraphElement function2: functions.eval().nodes()){
				if(!function1.equals(function2)) {
					Q reverseCallForFunction2 = Common.toQ(function2).reverseOn(callEdges);
					
					// Intersection of the reverse call graphs of the pair of functions
					Q intersectionOfReverseCallGraphs = reverseCallForFunction1.intersection(reverseCallForFunction2);
					
					// Add the leaves from the pairwise intersection to the set of functions considered for creating IPCG
					functions = functions.union(intersectionOfReverseCallGraphs.leaves());
					// TODO : Leaves may have cycles. In that case, add all the functions in the leaf SCC to functions  
				}
			}
		}
		Q iPCG = IPCG.getIPCG(functions, events);
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
	 * Constructs the Inter-procedural PCG using two given sets of functions M1, M2, and the following events:
	 * Callsites to functions in the union of the pairwise intersection of the reverse call graphs of (m1, m2) for each m1 and m2 in F1 and F2 respectively
	 * The computation is restricted to the forward call graph from entryFunctions.
	 * CCA stands for Common Call Ancestor, i.e., the intersection of reverse call chains of each pair (m1, m2) of functions.  
	 * 
	 * @param entryFunctions 
	 * @param functions1 first set of functions M1
	 * @param functions2 second set of functions M2
	 * @param events
	 * @return
	 */
	public static Q getIPCGIncludingPairwiseCCAs(Q entryFunctions, Q functions1, Q functions2, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		Q functions = functions1.union(functions2);
		
		// Find the union of the pairwise intersection of reverse call graphs from the functions
		for (GraphElement function1: functions1.eval().nodes()){
			Q reverseCallForFunction1 = Common.toQ(function1).reverseOn(callEdges);
			for (GraphElement function2: functions2.eval().nodes()){
				if(!function1.equals(function2)) {
					Q reverseCallForFunction2 = Common.toQ(function2).reverseOn(callEdges);
					
					// Intersection of the reverse call graphs from the 2 sets of functions
					Q intersectionOfReverseCallGraphs = reverseCallForFunction1.union(reverseCallForFunction2);

					if(entryFunctions != null && !CommonQueries.isEmpty(entryFunctions)) {
						// Entry point is specified, so restrict the intersection of reverse call graphs 
						// further to those reachable from the entry point 
						functions = functions.union(entryFunctions.forwardOn(callEdges).intersection(intersectionOfReverseCallGraphs));
					} else {
						// Entry point not specified, so just take the intersection of reverse call graphs 
						functions = functions.union(intersectionOfReverseCallGraphs);
					}
				}
			}
		}
		Q iPCG = IPCG.getIPCG(functions, events);
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
	 * Constructs the Inter-procedural PCG using two given sets of functions M1, M2, and the following events:
	 * Callsites to functions in the union of the reverse call graphs of (m1, m2) for each m1 and m2 in F1 and F2 respectively
	 * The computation is restricted to the forward call graph from entryFunctions.
	 * "Interactions" as opposed to "Common Call Ancestors" means that the union of reverse call graphs is taken, rather than their intersection.  
	 * 
	 * @param entryFunctions
	 * @param functions1
	 * @param functions2
	 * @param events
	 * @return
	 */
	public static Q getIPCGFromInteractions(Q entryFunctions, Q functions1, Q functions2, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		Q rev1 = functions1.reverseOn(callEdges);
		Q rev2 = functions2.reverseOn(callEdges);
		
		Q revCalls = rev1.union(rev2);
		Q functions = Common.empty();
		if(entryFunctions != null && !CommonQueries.isEmpty(entryFunctions)) {
			// Entry point is specified, so restrict the intersection of reverse call graphs 
			// further to those reachable from the entry point 
			if(CommonQueries.isEmpty(revCalls)) {
				functions = entryFunctions.forwardOn(callEdges);
			} else {
				functions = revCalls.intersection(entryFunctions.forwardOn(callEdges));
			}
		}
		Q iPCG = IPCG.getIPCG(functions.nodesTaggedWithAny(XCSG.Function), events);
		
		return iPCG;
	}
	
	public static Q getIPCGFromEvents(Q explicitFunctions, Q events){
		
		Q containingFunctions = StandardQueries.getContainingFunctions(events);
		Q functions = explicitFunctions.union(containingFunctions);
		
		Q iPCG = IPCG.getIPCG(functions.nodesTaggedWithAny(XCSG.Function), events);
		
		return iPCG;
	}
	
	/**
	 * Constructs the Inter-procedural CFG using CFGs of two given sets of functions M1, M2, and the functions containing the following events:
	 * Callsites to functions in the union of the reverse call graphs of (m1, m2) for each m1 and m2 in F1 and F2 respectively
	 * The computation is restricted to the forward call graph from entryFunctions.
	 * "Interactions" as opposed to "Common Call Ancestors" means that the union of reverse call graphs is taken, rather than their intersection.  
	 * 
	 * @param entryFunctions
	 * @param functions1
	 * @param functions2
	 * @param events
	 * @return
	 */
	public static Q getICFGFromInteractions(Q entryFunctions, Q functions1, Q functions2, Q events){
		Q callEdges = Common.edges(XCSG.Call);
		Q rev1 = functions1.reverseOn(callEdges);
		Q rev2 = functions2.reverseOn(callEdges);
		
		Q revCalls = rev1.union(rev2);
		
		Q functions = Common.empty();
		if(entryFunctions != null && !CommonQueries.isEmpty(entryFunctions)) {
			// Entry point is specified, so restrict the reverse calls only 
			// to those reachable from the entry point	 
			functions = revCalls.intersection(entryFunctions.forwardOn(callEdges));
		}
		
		Q icfg = IPCG.getICFG(functions.nodesTaggedWithAny(XCSG.Function));
		
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
