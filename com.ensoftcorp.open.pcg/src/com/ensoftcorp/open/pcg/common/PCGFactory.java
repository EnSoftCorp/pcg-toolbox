package com.ensoftcorp.open.pcg.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
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
import com.ensoftcorp.open.commons.sandbox.DefaultFlushProvider;
import com.ensoftcorp.open.commons.sandbox.Sandbox;
import com.ensoftcorp.open.commons.sandbox.SandboxEdge;
import com.ensoftcorp.open.commons.sandbox.SandboxGraph;
import com.ensoftcorp.open.commons.sandbox.SandboxGraphElement;
import com.ensoftcorp.open.commons.sandbox.SandboxHashSet;
import com.ensoftcorp.open.commons.sandbox.SandboxNode;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.preferences.PCGPreferences;

/**
 * A class that implements the event flow graph transformations to transform a given CFG into PCG
 * 
 * @author Ahmed Tamrawi, Ben Holland, Ganesh Ram Santhanam, Jon Mathews, Nikhil Ranade
 */
public class PCGFactory {
	
	/**
	 * Construct the PCGs corresponding to the given events with the containing functions control flow graph
	 * @param function
	 * @param events
	 * @return PCG
	 */
	public static PCG create(Q events) {
		return create(events, false);
	}
	
	/**
	 * Construct the PCGs corresponding to the given events with the containing functions control flow graph
	 * Considers exceptional control flow paths if specified
	 * @param function
	 * @param events
	 * @return PCG
	 */
	public static PCG create(Q events, boolean exceptionalControlFlow) {
		Q functions = CommonQueries.getContainingFunctions(events);
		Q cfg;
		if(exceptionalControlFlow){
			cfg = CommonQueries.excfg(functions);
		} else {
			cfg = CommonQueries.cfg(functions);
		}
		events = events.intersection(cfg);
		return create(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), events);
	}
	
	/**
	 * Construct the PCG for the given CFG, selected CFG roots, and the events
	 * of interest. Note that roots, exits, and events must all be contained
	 * within the given cfg.
	 * 
	 * @param cfg
	 * @param function
	 * @param events
	 * @return PCG
	 */
	public static PCG create(Q cfg, Q cfRoots, Q cfExits, Q events) {
		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg.eval(), cfRoots.eval().nodes(), cfExits.eval().nodes(), CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
		return create(ucfg, events);
	}
	
	/**
	 * Constructs a PCG for the given unique entry/exit control flow graph and a
	 * set of events
	 * 
	 * @param ucfg
	 * @param events
	 * @return
	 */
	public static PCG create(UniqueEntryExitControlFlowGraph ucfg, Q events){
		events = events.intersection(Common.toQ(ucfg.getCFG()));
		PCG pcg = PCG.load(ucfg, events.eval().nodes());
		if(pcg != null){
			return pcg;
		} else {
			// PCG does not exist or could not be found, compute the PCG now
			return new PCGFactory(ucfg, events.eval().nodes()).createPCG();
		}
	}
	
	// temporary variables for use in factory construction of a pcg
	private Sandbox sandbox;
	private SandboxNode masterEntry;
	private SandboxNode masterExit;
	private SandboxHashSet<SandboxNode> events;
	
	private UniqueEntryExitControlFlowGraph atlasUCFG;
	private AtlasSet<Node> atlasEvents;
	
	/** Sandbox universe.
	 *  Initialized to CFG, transformed to the PCG
	 *  Nodes: ControlFlow_Edge, EventFlow_Edge 
	 *  Edges: ControlFlow_Node, EventFlow_Master_Entry, EventFlow_Master_Exit */
	private SandboxGraph pcg;
	
	private static class PCGFlushProvider extends DefaultFlushProvider {
		/**
		 * Flushes the changes made or creation of a sandbox graph element to
		 * the Atlas graph and updates the address map accordingly.
		 * 
		 * This implementation differs from the default implementation by
		 * attempting to re-use EventFlow edges that already exist between the
		 * two given nodes if the sandbox created a new edge between the two
		 * edges.
		 * 
		 * @param ge
		 * @return
		 */
		@Override
		public GraphElement flush(SandboxGraphElement ge, Map<String,SandboxGraphElement> addresses) {
			if(ge.isMirror()){
				if(ge instanceof SandboxNode){
					Node node = Graph.U.createNode();
					// add all the sandbox tags
					for(String tag : ge.tags()){
						node.tag(tag);
					}
					// add all new sandbox attributes
					for(String key : ge.attr().keySet()){
						node.putAttr(key, ge.attr().get(key));
					}
					addresses.remove(ge.getAddress());
					ge.flush(node.address().toAddressString());
					addresses.put(ge.getAddress(), ge);
					return node;
				} else if(ge instanceof SandboxEdge){
					SandboxEdge sandboxEdge = (SandboxEdge) ge;
					// assert: nodes will all have been flushed by the time we are flushing edges
					Node from = CommonQueries.getNodeByAddress(sandboxEdge.from().getAddress());
					Node to = CommonQueries.getNodeByAddress(sandboxEdge.to().getAddress());
					
					Edge edge;
					if(sandboxEdge.tags().contains(PCGEdge.EventFlow_Edge)){
						// only create event flow edges between nodes if one does not already exist
						Q pcgEdges = Common.universe().edges(XCSG.ControlFlow_Edge, PCGEdge.EventFlow_Edge);
						AtlasSet<Edge> betweenEdges = pcgEdges.betweenStep(Common.toQ(from), Common.toQ(to)).eval().edges();
						if(!betweenEdges.isEmpty()){
							edge = betweenEdges.one();
						} else {
							edge = Graph.U.createEdge(from, to);
						}
					} else {
						edge = Graph.U.createEdge(from, to);
					}
					
					// add all the sandbox tags
					for(String tag : ge.tags()){
						edge.tag(tag);
					}
					// add all new sandbox attributes
					for(String key : ge.attr().keySet()){
						edge.putAttr(key, ge.attr().get(key));
					}
					addresses.remove(ge.getAddress());
					ge.flush(edge.address().toAddressString());
					addresses.put(ge.getAddress(), ge);
					return edge;
				} else {
					throw new RuntimeException("Unknown sandbox graph element type.");
				}
			} else {
				GraphElement age = CommonQueries.getGraphElementByAddress(ge.getAddress());
				
				// purge all old tags
				Set<String> tagsToRemove = new HashSet<String>();
				for(String tag : age.tags()){
					tagsToRemove.add(tag);
				}
				for(String tag : tagsToRemove){
					age.tags().remove(tag);
				}
				
				// add all the sandbox tags
				for(String tag : ge.tags()){
					age.tag(tag);
				}
				
				// purge all old attributes
				Set<String> keysToRemove = new HashSet<String>();
				for(String key : age.attr().keys()){
					keysToRemove.add(key);
				}
				for(String key : keysToRemove){
					age.attr().remove(key);
				}
				
				// add all new sandbox attributes
				for(String key : ge.attr().keySet()){
					age.putAttr(key, ge.attr().get(key));
				}
				
				return age;
			}
		}
	}
	
	/**
	 * Constructs a PCGFactory
	 * @param ucfg
	 * @param events
	 */
	private PCGFactory(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events) {
		this.atlasUCFG = ucfg;
		this.atlasEvents = events;
		
		if(CommonsPreferences.isComputeControlFlowGraphDominanceTreesEnabled() 
				|| CommonsPreferences.isComputeExceptionalControlFlowGraphDominanceTreesEnabled()) {
			// use the pre-computed relationships
		} else {
			// calculate on demand
			DominanceAnalysis.computeDominanceFrontier(ucfg);
		}
		
		AtlasSet<Node> allEvents = addImpliedEvents(ucfg, events);
		
		// initialize the sandbox universe
		this.sandbox = new Sandbox();
		this.sandbox.setFlushProvider(new PCGFlushProvider());
		
		this.pcg = sandbox.universe();
		
		// populate sandbox universe
		// assert: allEvents are a subset of the ucfg
		this.sandbox.addGraph(ucfg.getGraph());
		
		// select the sandbox nodes, edges, and events to iterate over
		this.masterEntry = sandbox.node(ucfg.getEntryNode());
		this.masterExit = sandbox.node(ucfg.getExitNode());
		
		this.events = sandbox.nodes(allEvents);
	}
	
	/**
	 * Construct PCG
	 * @return
	 */
	private PCG createPCG(){
		
		// retain a set of consumed nodes that are to be removed from the graph after the loop
		SandboxHashSet<SandboxNode> nodesToRemove = sandbox.emptyNodeSet();
		for(SandboxNode node : pcg.nodes()) {
			if(!events.contains(node)){
				consumeNode(node);
				nodesToRemove.add(node);
			}
		}
		
		// remove the consumed nodes in the previous loop
		pcg.nodes().removeAll(nodesToRemove);
		
		// create a copy of all the edges that only refer to nodes which are tagged as pcg nodes
		SandboxHashSet<SandboxEdge> pcgEdgeSet = new SandboxHashSet<SandboxEdge>(sandbox.getInstanceID());
		for (SandboxEdge edge : pcg.edges()) {
			if (pcg.nodes().contains(edge.getNode(EdgeDirection.FROM)) 
					&& pcg.nodes().contains(edge.getNode(EdgeDirection.TO))) {
				pcgEdgeSet.add(edge);
			}
		}
		pcg.edges().clear();
		pcg.edges().addAll(pcgEdgeSet);
		
		
		// tag the entry and exit nodes
		masterEntry.tag(PCG.PCGNode.EventFlow_Master_Entry);
		masterExit.tag(PCG.PCGNode.EventFlow_Master_Exit);
		
		// tag each edge as an event flow edge
		// this gets an edges from the master entry to the roots
		// and from the exits to the master exit
		for(SandboxEdge edge : pcg.edges()){
			edge.tag(PCG.PCGEdge.EventFlow_Edge);
		}		

		// as a final sanity check - we could check that nodes should only be included 
		// if they are reachable on the path pcg.between(masterEntry,masterExit)
		// disabled because not really needed and sandbox between isn't efficient
//		pcg = pcg.between(masterEntry, masterExit);
		
		// flush the result and construct the PCG object
		Graph atlasPCG = sandbox.flush(pcg);
		PCG result = new PCG(atlasPCG, atlasUCFG, atlasEvents);
		
		// save the pcg instance parameters to the master entry node EventFlow_Instances attribute
		if(PCGPreferences.isSerializePCGInstancesEnabled()){
			PCG.save(result);
		}
		
		return result;
	}

	/**
	 * Consumes the given non-event node bypassing it through connecting its
	 * predecessors with successors. New edges are PCG edges and summarize
	 * conditionValues, but do not retain other tags or attributes from the
	 * elided subgraph.
	 * 
	 * @param node non-event node to be removed from the final PCG
	 */
	private void consumeNode(SandboxNode node) {
		// this function will consume the given node by bypassing it through
		// connecting its predecessors with successors while preserving edge's
		// conditional values
		
		// first: get the predecessors for the node
		SandboxHashSet<SandboxEdge> inEdges = pcg.edges(node, NodeDirection.IN);
		Set<SandboxEdge> predecessorEdges = new HashSet<>(); 
		for(SandboxEdge inEdge : inEdges) {
			SandboxNode predecessor = inEdge.from();
			if (node.equals(predecessor)) {
				// skip the case where the node has a self-loop. This will cause infinite recursion
				continue;
			}
			predecessorEdges.add(inEdge);
		}
		
		// second: get the successors for the node
		SandboxHashSet<SandboxEdge> outEdges = pcg.edges(node, NodeDirection.OUT);
		Set<SandboxNode> successors = new HashSet<>(); 
		for(SandboxEdge outEdge : outEdges) {
			SandboxNode successor = outEdge.to();
			if (node.equals(successor)) {
				// skip the case where the node has a self-loop. This will cause infinite recursion
				continue;
			}
			successors.add(successor);
		}
		
		// add PCG edges
		for(SandboxEdge inEdge : predecessorEdges) {
			connectToSuccessors(inEdge, successors);
		}
		
		// remove original inEdges for the node
		pcg.edges().removeAll(inEdges);
		
		// remove original outEdges for the node
		pcg.edges().removeAll(outEdges);
	}

	/**
	 * Connect inEdge.from() to all successors, consolidating duplicate edges as needed.
	 * 
	 * For example, when both true and false edges reach the same successor, they are combined
	 * into a single edge.
	 * 
	 * @param inEdge
	 * @param successors
	 */
	private void connectToSuccessors(SandboxEdge inEdge, Set<SandboxNode> successors) {
		
		// FIXME do not create anything until we know what the final state should be - how would removeDouble figure out which one is redundant anyway?
		
		SandboxNode predecessor = inEdge.from();
		for(SandboxNode successor : successors) {
			this.getOrCreatePCGEdge(predecessor, successor, 
					inEdge.attr().get(XCSG.conditionValue));
		}
		
		// duplicate edges maybe be formed at the predecessor because of
		// consuming the current node, so merge if needed
		this.removeDuplicateEdges(predecessor);
	}
	
	/**
	 * remove duplicate successor edges
	 * @param node
	 */
	private void removeDuplicateEdges(SandboxNode node){
		SandboxHashSet<SandboxEdge> outEdges = pcg.edges(node, NodeDirection.OUT);
		if(outEdges.size() < 2){
			return;
		}
		
		HashMap<SandboxNode, SandboxHashSet<SandboxEdge>> nodeEdgeMap = new HashMap<>();
		for(SandboxEdge outEdge : outEdges){
			SandboxNode successor = outEdge.getNode(EdgeDirection.TO);
			SandboxHashSet<SandboxEdge> edges = sandbox.emptyEdgeSet();
			if(nodeEdgeMap.containsKey(successor)){
				edges = nodeEdgeMap.get(successor);
			}
			edges.add(outEdge);
			nodeEdgeMap.put(successor, edges);
		}
		
		for(SandboxNode successor : nodeEdgeMap.keySet()){
			SandboxHashSet<SandboxEdge> successorEdges = nodeEdgeMap.get(successor);
			if(successorEdges.size() > 1){
				SandboxEdge oldEdge = successorEdges.one();
				this.getOrCreatePCGEdge(node, successor, 
						oldEdge.attr().get(XCSG.conditionValue));
				
				pcg.edges().removeAll(successorEdges);
			}
		}
	}
	
	/**
	 * Using dominance frontier, compute the set of implied event nodes.
	 * @param ucfg
	 * @return The set of event nodes that need to be retained in the final PCG, 
	 * including implicit, explicit and start/exit nodes.
	 */
	private AtlasSet<Node> addImpliedEvents(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> explicitEvents) {
		// get the dominance frontier within the function
		Q dominanceFrontierEdges = Common.universe().edges(DominanceAnalysis.DOMINANCE_FRONTIER_EDGE);
		Q ucfgNodes = Common.toQ(ucfg.getGraph()).retainNodes();
		Q dominanceFrontier = Common.resolve(null, ucfgNodes.induce(dominanceFrontierEdges));
		
		Q qExplicitEvents = Common.toQ(Common.toGraph(explicitEvents));
		AtlasSet<Node> impliedEvents = new AtlasHashSet<>();
		AtlasSet<Node> qImpliedEvents = dominanceFrontier.forward(qExplicitEvents).eval().nodes();
		impliedEvents.addAll(qImpliedEvents);
		
		// add entry and exit nodes as event nodes as well
		impliedEvents.add(ucfg.getEntryNode());
		impliedEvents.add(ucfg.getExitNode());
		
		return impliedEvents;
	}
	
	/**
	 * Finds or creates a edge in the PCG between the specified nodes with the conditionValue.
	 * 
	 * @param from predecessor
	 * @param to successor
	 * @param conditionValue the conditionValue, or null on unconditional edges
	 * @return the edge
	 */
	private SandboxEdge getOrCreatePCGEdge(SandboxNode from, SandboxNode to, Object conditionValue) {
		
		SandboxHashSet<SandboxEdge> edges = pcg.edges(from, NodeDirection.OUT);
		
		// find match
		for (SandboxEdge edge : edges) {
			if (!to.equals(edge.to())) {
				continue;
			}
			boolean hasAttr = edge.hasAttr(XCSG.conditionValue);
			if (conditionValue == null) {
				// looking for an edge WITHOUT the attribute
				if (!hasAttr) {
					return edge;
				}
			} else {
				// looking for an edge with the same value
				if (hasAttr) {
					Object attr = edge.getAttr(XCSG.conditionValue);
					if (conditionValue.equals(attr)) {
						return edge;
					}
				}
			}
		}
		
		// assert: no match
		
		// create a new edge
		SandboxEdge pcgEdge = sandbox.createEdge(from, to);
		pcgEdge.putAttr(XCSG.conditionValue, conditionValue);
		pcgEdge.tag(XCSG.Edge);
		pcgEdge.tag(PCGEdge.EventFlow_Edge);
		
		pcg.edges().add(pcgEdge);

		return pcgEdge;
	}
	
}