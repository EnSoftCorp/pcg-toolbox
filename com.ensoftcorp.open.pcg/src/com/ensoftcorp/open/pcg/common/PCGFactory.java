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
import com.ensoftcorp.open.commons.algorithms.LoopIdentification;
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
	 * Constructs the PCG corresponding to the given events within the containing control flow graph and returns
	 * only the nodes in the resulting PCG. 
	 * 
	 * This result is much cheaper to compute than PCGFactory.create alternatives which include PCG edges.
	 * 
	 * @param events
	 * @return
	 * @return
	 */
	public static AtlasSet<Node> createNodesOnly(Q events){
		Q functions = CommonQueries.getContainingFunctions(events);
		Q cfg = CommonQueries.cfg(functions);
		return createNodesOnly(events, cfg);
	}
	
	/**
	 * Construct the PCGs corresponding to the given events and control flow graph and returns
	 * only the nodes in the resulting PCG. 
	 * 
	 * This result is much cheaper to compute than PCGFactory.create alternatives which include PCG edges.
	 * @param function
	 * @param events
	 * @return
	 */
	public static AtlasSet<Node> createNodesOnly(Q events, Q cfg) {
		events = events.intersection(cfg).nodes(XCSG.ControlFlow_Node);
		return createNodesOnly(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), events); 
	}

	/**
	 * Construct the PCG for the given CFG, selected CFG roots, and the events
	 * of interest. Note that roots, exits, and events must all be contained
	 * within the given cfg and returns only the nodes in the resulting PCG. 
	 * 
	 * This result is much cheaper to compute than PCGFactory.create alternatives which include PCG edges.
	 * 
	 * @param cfg
	 * @param function
	 * @param events
	 * @return 
	 */
	public static AtlasSet<Node> createNodesOnly(Q cfg, Q cfRoots, Q cfExits, Q events) {
		if(CommonQueries.isEmpty(cfg)){
			throw new RuntimeException("Control flow graph is empty! Is the containing function a library function?");
		}
		// see PCGFactory.create for some design choice related to relaxing root/exit requirements
		boolean relaxNonEmptyRootsRequirement = true;
		boolean relaxNonEmptyExitsRequirement = true;
		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg.eval(), cfRoots.eval().nodes(), relaxNonEmptyRootsRequirement, cfExits.eval().nodes(), relaxNonEmptyExitsRequirement, CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
		return createNodesOnly(ucfg, events);
	}

	/**
	 * Constructs a PCG for the given unique entry/exit control flow graph and a
	 * set of events and returns only the nodes in the resulting PCG. 
	 * 
	 * This result is much cheaper to compute than PCGFactory.create alternatives which include PCG edges.
	 * 
	 * @param ucfg
	 * @param events
	 * @return
	 */
	public static AtlasSet<Node> createNodesOnly(UniqueEntryExitControlFlowGraph ucfg, Q events){
		events = events.intersection(Common.toQ(ucfg.getCFG()));
		AtlasSet<Node> conditions = Common.toQ(ucfg.getCFG()).nodes(XCSG.ControlFlowCondition).eval().nodes();
		AtlasSet<Node> result = new AtlasHashSet<Node>();
		for(Node event : events.eval().nodes()){
			result.add(event);
			for(Node condition : conditions){
				if(CommonQueries.isGoverningBranch(condition, event)){
					result.add(condition);
				}
			}
		}
		result.add(ucfg.getEntryNode());
		result.add(ucfg.getExitNode());
		return result;
	}

	/**
	 * Construct the PCGs corresponding to the given events with the containing functions control flow graph
	 * 
	 * @param function
	 * @param events
	 * @return PCG
	 */
	public static PCG create(Q events) {
		return create(events, false);
	}
	
	/**
	 * Construct the PCGs corresponding to the given events with the containing functions control flow graph
	 * 
	 * @param events
	 * @param labelBackEdges
	 * @return
	 */
	public static PCG create(Q events, boolean labelBackEdges) {
		Q functions = CommonQueries.getContainingFunctions(events);
		Q cfg = CommonQueries.cfg(functions);
		return create(cfg, events, labelBackEdges);
	}
	
	/**
	 * Construct the PCGs corresponding to the given events and control flow graph
	 * 
	 * @param function
	 * @param events
	 * @return PCG
	 */
	public static PCG create(Q cfg, Q events) {
		return create(cfg, events, false);
	}

	/**
	 * Construct the PCGs corresponding to the given events and control flow graph
	 * 
	 * @param cfg
	 * @param events
	 * @param labelBackEdges
	 * @return
	 */
	public static PCG create(Q cfg, Q events, boolean labelBackEdges) {
		events = events.intersection(cfg).nodes(XCSG.ControlFlow_Node);
		return create(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), events, labelBackEdges);
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
		return create(cfg, cfRoots, cfExits, events, false);
	}

	/**
	 * Construct the PCG for the given CFG, selected CFG roots, and the events
	 * of interest. Note that roots, exits, and events must all be contained
	 * within the given cfg.
	 * 
	 * @param cfg
	 * @param cfRoots
	 * @param cfExits
	 * @param events
	 * @return
	 */
	public static PCG create(Q cfg, Q cfRoots, Q cfExits, Q events, boolean labelBackEdges) {
		if(CommonQueries.isEmpty(cfg)){
			throw new RuntimeException("Control flow graph is empty! Is the containing function a library function?");
		}
		
		// APPROACH 2 - A PCG is a compaction of a CFG. A CFG could be refined 
		// by removing some infeasible edges. A PCG still has symbolic master  
		// entry and exit nodes but there is no guarantee there is a path from 
		// entry to exit. The collapsed CFG edges in the PCG are a reflection
		// of the accuracy of the refined CFG.
		
		// for APPROACH 2 we just do nothing...we is the currently implemented
		// approach, alternatively (APPROACH 1) we can restore the removed CFG
		// edges by detecting the cases where SOOT refined the control flow
		// that affected the master entry / exit nodes (which is because of loops)
		boolean relaxNonEmptyRootsRequirement = true;
		boolean relaxNonEmptyExitsRequirement = true;
		
		// APPROACH 1 - PCGs do not discuss feasibility questions and SOOT's
		// Jimple prematurely answered that question violating our assumption
		// that a path from master entry / exit exits (but may or may not be
		// feasible).
//		boolean relaxNonEmptyRootsRequirement = false;
//		boolean relaxNonEmptyExitsRequirement = false;
//
//		// a fun corner case is that the control flow root could be sucked up 
//		// in an SCC due to the root being a loop header. In this case the
//		// outermost loop (according to loop children) of the SCC is the control 
//		// flow root.
//		if(CommonQueries.isEmpty(cfRoots)){
//			// we should be able to trust the Atlas tags here...
//			cfRoots = cfg.nodes(XCSG.controlFlowRoot);
//			if(CommonQueries.isEmpty(cfRoots)){
//				throw new IllegalArgumentException("Control flow root must not be empty");
//			}
//			
//			// alternatively
////			AtlasSet<Node> outerLoops = Common.universe().edges(XCSG.LoopChild).reverse(cfg.nodes(XCSG.Loop)).roots().eval().nodes();
////			ArrayList<Node> sortedOuterLoops = new ArrayList<Node>();
////			for(Node outerLoop : outerLoops){
////				sortedOuterLoops.add(outerLoop);
////			}
////			Collections.sort(sortedOuterLoops, new NodeSourceCorrespondenceSorter());
////			if(sortedOuterLoops.isEmpty()){
////				throw new IllegalArgumentException("Control flow root must not be empty");
////			} else {
////				cfRoots = Common.toQ(sortedOuterLoops.get(0));
////			}
//		}
//		
//		// another lovely rare corner case here, a void method can have a loop
//		// with no termination conditions that forms a strongly connected
//		// component, so root -> ... SCC, since the SCC will not have any
//		// leaves could be empty. This is due to Jimple's dead code 
//		// elimination packs that remove the unreached return statement. 
//		// In this case the loop header(s) is implicitly the exit even
//		// though the loop is non-terminating. 
//		AtlasSet<Node> nonTerminatingLoops = new AtlasHashSet<Node>();
//		for(Node loop : cfg.nodes(XCSG.Loop).eval().nodes()){
//			if(CommonQueries.isEmpty(cfg.forward(Common.toQ(loop).difference(cfg.between(Common.toQ(loop), Common.toQ(loop)))))){
//				nonTerminatingLoops.add(loop);
//			}
//		}
//		cfExits = cfExits.union(Common.toQ(nonTerminatingLoops));
//		if(CommonQueries.isEmpty(cfExits)){
//			throw new RuntimeException("Control flow graph has no exits.");
//		}
		
		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg.eval(), cfRoots.eval().nodes(), relaxNonEmptyRootsRequirement, cfExits.eval().nodes(), relaxNonEmptyExitsRequirement, CommonsPreferences.isMasterEntryExitContainmentRelationshipsEnabled());
		return create(ucfg, events, labelBackEdges);
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
		return create(ucfg, events, false);
	}
	
	/**
	 * Constructs a PCG for the given unique entry/exit control flow graph and a
	 * set of events.
	 * 
	 * @param ucfg
	 * @param events
	 * @param labelBackEdges
	 * @return
	 */
	public static PCG create(UniqueEntryExitControlFlowGraph ucfg, Q events, boolean labelBackEdges){
		events = events.intersection(Common.toQ(ucfg.getCFG()));
//		PCG pcg = null; //PCG.load(ucfg, events.eval().nodes());
//		if(pcg != null){
//			return pcg;
//		} else {
			// PCG does not exist or could not be found, compute the PCG now
			return new PCGFactory(ucfg, events.eval().nodes()).createPCG(labelBackEdges);
//		}
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
	 *  Nodes: ControlFlow_Edge, PCGEdge 
	 *  Edges: ControlFlow_Node, PCGMasterEntry, PCGMasterExit */
	private SandboxGraph pcg;
	
	private static class PCGFlushProvider extends DefaultFlushProvider {
		/**
		 * Flushes the changes made or creation of a sandbox graph element to
		 * the Atlas graph and updates the address map accordingly.
		 * 
		 * This implementation differs from the default implementation by
		 * attempting to re-use PCG edges that already exist between the
		 * two given nodes if the sandbox created a new edge between the two
		 * edges.
		 * 
		 * @param ge
		 * @return
		 */
		@Override
		public GraphElement flush(SandboxGraphElement ge, Map<String,SandboxGraphElement> addresses) {
			if(!ge.isMirror()){
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
					
					Edge edge = null;
					if(sandboxEdge.tags().contains(PCGEdge.PCGEdge)){
						// only create event flow edges between nodes if one does not already exist
						edge = findPCGEdge(sandboxEdge, from, to);
						if (edge == null) {
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
					throw new RuntimeException("Unknown sandbox graph element type."); //$NON-NLS-1$
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

		/** find a compatible PCG Edge with respect to adjacent nodes and XCSG.conditionValue */
		private Edge findPCGEdge(SandboxEdge sandboxEdge, Node from, Node to) {
			Q pcgEdges = Common.universe().edges(XCSG.ControlFlow_Edge, PCGEdge.PCGEdge);
			AtlasSet<Edge> betweenEdges = pcgEdges.betweenStep(Common.toQ(from), Common.toQ(to)).eval().edges();
			boolean hasAttr = sandboxEdge.hasAttr(XCSG.conditionValue);
			Object cv = sandboxEdge.getAttr(XCSG.conditionValue);
			for (Edge be : betweenEdges) {
				boolean hasAttr2 = be.hasAttr(XCSG.conditionValue);
				Object cv2 = be.getAttr(XCSG.conditionValue);
				if (hasAttr==hasAttr2) {
					if (cv == null && cv==cv2) {
						return be;
					} else if (cv.equals(cv2)) {
						return be;
					}
				}
			}
			return null;
		}
	}
	
	/**
	 * Constructs a PCGFactory
	 * @param ucfg
	 * @param events
	 * @param postConditions 
	 */
	private PCGFactory(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events) {
		// storing references to create result object later
		this.atlasUCFG = ucfg;
		this.atlasEvents = events;
		
		// initialize the sandbox universe
		this.sandbox = new Sandbox();
		this.sandbox.setFlushProvider(new PCGFlushProvider());

		// populate sandbox universe
		// assert: allEvents are a subset of the ucfg
		this.sandbox.addGraph(ucfg.getGraph());
		SandboxGraph sucfg = sandbox.graph(ucfg.getGraph()); 
		this.masterEntry = sandbox.node(ucfg.getEntryNode());
		this.masterExit = sandbox.node(ucfg.getExitNode());
		
		// always calculate on demand and in a sandbox because pcg could be
		// calculated on a subset of the CFG
		SandboxGraph domFrontier = DominanceAnalysis.computeSandboxedPostDominanceFrontier(sandbox, ucfg);
		this.events = getImpliedEvents(sandbox, domFrontier, masterEntry, masterExit, sandbox.nodes(events));
		
		// the pcg starts as the whole cfg with master entry/exit
		this.pcg = sucfg;
	}
	
	/**
	 * Construct PCG
	 * @return
	 */
	private PCG createPCG(boolean labelBackEdges){
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
		masterEntry.tag(PCG.PCGNode.PCGMasterEntry);
		masterExit.tag(PCG.PCGNode.PCGMasterExit);
		
		// tag each edge as an event flow edge
		// this gets an edges from the master entry to the roots
		// and from the exits to the master exit
		for(SandboxEdge edge : pcg.edges()){
			edge.tag(PCG.PCGEdge.PCGEdge);
		}
		
		// flush the result and construct the PCG object
		Graph atlasPCG = sandbox.flush(pcg);
		PCG result = new PCG(atlasPCG, atlasUCFG, atlasEvents);
		
		if(labelBackEdges){
			Node masterEntry = Common.toQ(atlasPCG).nodes(PCG.PCGNode.PCGMasterEntry).eval().nodes().one();
			labelBackEdges(atlasPCG, masterEntry);
		}
		
		// save the pcg instance parameters to the master entry node PCG_Instances attribute
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
		SandboxNode predecessor = inEdge.from();
		for (SandboxNode successor : successors) {
			this.getOrCreatePCGEdge(predecessor, successor, inEdge.getAttr(XCSG.conditionValue));
		}
		// merge (boolean) edges
		this.mergeEdges(predecessor);
	}
	
	/**
	 * Merge conditional edges.
	 * 
	 * For 'if' and 'loop' conditions, the boolean successor edges
	 * are merged when both 'true' and 'false' are present.
	 * @param node
	 */
	private void mergeEdges(SandboxNode node){
		SandboxHashSet<SandboxEdge> outEdges = pcg.edges(node, NodeDirection.OUT);
		if (outEdges.size() < 2){
			return;
		}
		
		// group out edges by successor
		HashMap<SandboxNode, SandboxHashSet<SandboxEdge>> nodeEdgeMap = new HashMap<>();
		for (SandboxEdge outEdge : outEdges) {
			SandboxNode successor = outEdge.getNode(EdgeDirection.TO);
			SandboxHashSet<SandboxEdge> edges = sandbox.emptyEdgeSet();
			if (nodeEdgeMap.containsKey(successor)) {
				edges = nodeEdgeMap.get(successor);
			}
			edges.add(outEdge);
			nodeEdgeMap.put(successor, edges);
		}
		
		for (SandboxNode successor : nodeEdgeMap.keySet()) {
			SandboxHashSet<SandboxEdge> successorEdges = nodeEdgeMap.get(successor);
			// successors with in degree > 1 
			if (successorEdges.size() > 1){
				if (node.taggedWith(XCSG.ControlFlowIfCondition) || node.taggedWith(XCSG.ControlFlowLoopCondition)) {
					/* NOTE: because nodes are consumed in no particular order, it is possible to
					 * encounter a merge of an unconditional edge with true or false edge, indicating
					 * that the paths are partially merged already.
					 * 
					 * This should imply that the successor is not an event, and all paths to it will be merged
					 * eventually.
					 * 
					 * This also means that the merged edge should not be removed along with the others,
					 * in the case that two unconditional edges are being merged.  This can happen as a result of 
					 * merging deeply nested branches (depth 3 is sufficient). 
					 */
					
					// assert: duplicate values of XCSG.conditionValue should be impossible because of getOrCreate
					assertConditionValues(successorEdges);
					SandboxEdge mergedEdge = this.getOrCreatePCGEdge(node, successor, null);
					
					// remove the edges which have been replaced (but not the one representing the merged paths) 
					successorEdges.remove(mergedEdge);
					pcg.edges().removeAll(successorEdges);
				} else if (node.taggedWith(XCSG.ControlFlowSwitchCondition)) {
					// assert: duplicate values of XCSG.conditionValue should be impossible because of getOrCreate
					assertConditionValues(successorEdges);
					// unlike the boolean edges, do not merge
				} else {
					// unexpected case...
					// one case that could end up here is (case 45934)
					// the previous node.taggedWith(XCSG.ControlFlowSwitchCondition) is not strong
					// enough to catch statements between switch conditions and case statements
					//    switch (expr) {
					//    //  <trouble>
					//    // i has automatic storage, but the initialization is dead, as is the call to f()
					//    int i = 4;
					//    f(i);
					//        //  </trouble>
					//    case 0:
					//        i = 17;
					//        /* no break */
					//    default:
					//        printf("%d\n", i);
					//    }
					throw new RuntimeException("Unhandled case for merging duplicate edges at node: " + node); //$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Sanity check conditionValues 
	 * @param successorEdges
	 * @return
	 * @throws IllegalStateException if conditionValue is not present, null, or if each value is not unique per edge
	 */
	private Set<Object> assertConditionValues(SandboxHashSet<SandboxEdge> successorEdges) {
		Set<Object> conditionValues = new HashSet<>();
		for (SandboxEdge successorEdge : successorEdges) {
			Object cv = successorEdge.getAttr(XCSG.conditionValue);
			if (cv == null) {
				new IllegalStateException("Expected XCSG.conditionValue on edge: " + successorEdge); //$NON-NLS-1$
			}
			boolean added = conditionValues.add(cv);
			if (!added) {
				// collision in values is not expected
				new IllegalStateException("Expected XCSG.conditionValue to be unique across edges, value=" + cv); //$NON-NLS-1$
			}
		}
		return conditionValues;
	}
	
	/**
	 * Using dominance frontier, compute the set of implied event nodes.
	 * @param domFrontier 
	 * @param ucfg
	 * @return The set of event nodes that need to be retained in the final PCG, 
	 * including implicit, explicit and start/exit nodes.
	 */
	private SandboxHashSet<SandboxNode> getImpliedEvents(Sandbox sandbox, SandboxGraph domFrontier, SandboxNode ucfgEntry, SandboxNode ucfgExit, SandboxHashSet<SandboxNode> explicitEvents) {
		// get the dominance frontier within the function
		SandboxHashSet<SandboxNode> impliedEvents = new SandboxHashSet<SandboxNode>(sandbox);
		impliedEvents.addAll(explicitEvents);
		impliedEvents.addAll(domFrontier.forward(explicitEvents).nodes());
		
		// add entry and exit nodes as event nodes as well
		impliedEvents.add(ucfgEntry);
		impliedEvents.add(ucfgExit);
		
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
		pcgEdge.tag(XCSG.Edge);
		pcgEdge.tag(PCGEdge.PCGEdge);
		if (conditionValue != null){
			pcgEdge.putAttr(XCSG.conditionValue, conditionValue);
		}
		
		pcg.edges().add(pcgEdge);
		return pcgEdge;
	}
	
	private void labelBackEdges(Graph atlasPCG, Node masterEntry) {
		LoopIdentification loops = new LoopIdentification(atlasPCG, masterEntry);
		for (Edge reentryEdge : loops.getReentryEdges()) {
			reentryEdge.tag(PCG.PCGEdge.PCGReentryEdge);
		}
		for (Edge loopbackEdge : loops.getLoopbacks()) {
			loopbackEdge.tag(PCG.PCGEdge.PCGBackEdge);
		}
	}
	
}
