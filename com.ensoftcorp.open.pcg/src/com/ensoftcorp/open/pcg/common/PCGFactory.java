package com.ensoftcorp.open.pcg.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.notification.NotificationHashMap;
import com.ensoftcorp.atlas.core.db.notification.NotificationMap;
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
import com.ensoftcorp.open.commons.sandbox.SandboxEdge;
import com.ensoftcorp.open.commons.sandbox.SandboxGraph;
import com.ensoftcorp.open.commons.sandbox.SandboxHashSet;
import com.ensoftcorp.open.commons.sandbox.SandboxNode;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.log.Log;

/**
 * A class that implements the event flow graph transformations to transform a given CFG into PCG
 * 
 * @author Ahmed Tamrawi, Ben Holland, Ganesh Ram Santhanam, Jon Mathews, Nikhil Ranade
 */
public class PCGFactory {
	
	// temporary variables for use in factory construction of a pcg
	private Sandbox sandbox;
	private SandboxGraph ucfg;
	private SandboxGraph dominanceFrontier;
	private SandboxNode masterEntry;
	private SandboxNode masterExit;
	private SandboxHashSet<SandboxNode> events;
	private SandboxHashSet<SandboxNode> nodes;
	private SandboxHashSet<SandboxEdge> edges;
	private final String pcgInstanceID = "TODO";
	
	/**
	 * Constructs a PCG
	 * @param ucfg
	 * @param events
	 */
	private PCGFactory(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events) {
		Graph dominanceFrontier;
		if(CommonsPreferences.isComputeControlFlowGraphDominanceTreesEnabled() || CommonsPreferences.isComputeExceptionalControlFlowGraphDominanceTreesEnabled()){
			// use the pre-compute relationships if they are available
			dominanceFrontier = DominanceAnalysis.getDominanceFrontiers().retainEdges().eval();
		} else {
			dominanceFrontier = Common.toQ(DominanceAnalysis.computeDominanceFrontier(ucfg)).retainEdges().eval();
		}
		
		// initialize the sandbox universe
		this.sandbox = new Sandbox();
		this.sandbox.addGraph(dominanceFrontier);
		this.sandbox.addGraph(ucfg.getGraph());
		
		// save the sandbox nodes, edges, and events to iterate over
		this.ucfg = sandbox.graph(ucfg.getGraph());
		this.masterEntry = (SandboxNode) sandbox.getAt(ucfg.getEntryNode().address().toAddressString());
		this.masterExit = (SandboxNode) sandbox.getAt(ucfg.getExitNode().address().toAddressString());
		this.dominanceFrontier = sandbox.graph(dominanceFrontier);
		this.nodes = sandbox.nodes(ucfg.getGraph().nodes());
		this.edges = sandbox.edges(ucfg.getGraph().edges());
		this.events = sandbox.nodes(events);
	}

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
		UniqueEntryExitControlFlowGraph ucfg = new UniqueEntryExitControlFlowGraph(cfg.eval(), cfRoots.eval().nodes(), cfExits.eval().nodes());
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
		
//		String pcgInstanceID = md5(pcgParameters);
//		Q pcgInstance = Common.universe().nodes(PCG.EventFlow_Instance_Prefix + pcgInstanceID)
//				.induce(Common.universe().edgesTaggedWithAll(PCG.EventFlow_Instance_Prefix + pcgInstanceID, PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID).retainEdges());
//		if(!CommonQueries.isEmpty(pcgInstance)){
//			Node masterEntry = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes().one();
//			Node masterExit = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Exit).eval().nodes().one();
//			PCG pcg = new PCG(pcgInstanceID, pcgInstance.eval(), function, masterEntry, masterExit);
//			pcg.setUpdateLastAccessTime();
//			return pcg;
//		}
		
		// PCG does not exist or could not be found, compute the PCG now
		return new PCGFactory(ucfg, events.eval().nodes()).createPCG();
	}
	
//	@SuppressWarnings("unchecked")
//	private static String getPCGParametersJSONString(Graph cfg, AtlasSet<Node> cfRoots, AtlasSet<Node> cfExits, AtlasSet<Node> events){
//		// important note: do not modify this json list without special consideration
//		// the result of this json object is used to compute the hash instance id of the PCG
//		// only values that constitute pcg equivalences should be used to create this object
//		events = Common.toQ(events).intersection(Common.toQ(cfg)).eval().nodes();
//		cfRoots = Common.toQ(cfRoots).intersection(Common.toQ(cfg)).eval().nodes();
//		cfExits = Common.toQ(cfExits).intersection(Common.toQ(cfg)).eval().nodes();
//		
//		JSONArray cfgNodeAddresses = new JSONArray();
//		for(String address : getSortedAddressList(cfg.nodes())){
//			cfgNodeAddresses.add(address);
//		}
//		
//		JSONArray cfgEdgeAddresses = new JSONArray();
//		for(String address : getSortedAddressList(cfg.edges())){
//			cfgEdgeAddresses.add(address);
//		}
//		
//		JSONArray rootAddresses = new JSONArray();
//		for(String address : getSortedAddressList(cfRoots)){
//			rootAddresses.add(address);
//		}
//		
//		JSONArray exitAddresses = new JSONArray();
//		for(String address : getSortedAddressList(cfExits)){
//			exitAddresses.add(address);
//		}
//		
//		JSONArray eventAddresses = new JSONArray();
//		for(String address : getSortedAddressList(events)){
//			eventAddresses.add(address);
//		}
//		
//		JSONObject json = new JSONObject();
//		json.put(PCG.JSON_CFG_NODES, cfgNodeAddresses);
//		json.put(PCG.JSON_CFG_EDGES, cfgEdgeAddresses);
//		json.put(PCG.JSON_ROOTS, rootAddresses);
//		json.put(PCG.JSON_EXITS, exitAddresses);
//		json.put(PCG.JSON_EVENTS, eventAddresses);
//		
//		return json.toJSONString();
//	}
//	
//	/**
//	 * Computes the MD5 hash of a string value
//	 * @param value
//	 * @return
//	 * @throws NoSuchAlgorithmException
//	 */
//	private static String md5(String value) {
//		try {
//			MessageDigest md = MessageDigest.getInstance("MD5");
//			byte[] array = md.digest(value.getBytes());
//			StringBuffer sb = new StringBuffer();
//			for (int i = 0; i < array.length; ++i) {
//				sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
//			}
//			return sb.toString().toUpperCase();
//		} catch (NoSuchAlgorithmException e) {
//			Log.error("MD5 hashing is not supported!", e);
//			throw new RuntimeException(e);
//		}
//	}
//	
//	/**
//	 * Returns an alphabetically sorted list of graph element addresses
//	 * @param graphElements
//	 * @return
//	 */
//	private static List<String> getSortedAddressList(AtlasSet<? extends GraphElement> graphElements){
//		ArrayList<String> addresses = new ArrayList<String>((int) graphElements.size());
//		for(GraphElement graphElement : graphElements){
//			addresses.add(graphElement.address().toAddressString());
//		}
//		Collections.sort(addresses);
//		return addresses;
//	}
	
	/**
	 * Given a CFG, construct PCG
	 * @return
	 */
	public PCG createPCG(){
		SandboxHashSet<SandboxNode> impliedEvents = getImpliedEvents();
		
		// retain a stack of node that are consumed to be removed from the graph after the loop
		SandboxHashSet<SandboxNode> nodesToRemove = sandbox.emptyNodeSet();
		for(SandboxNode node : nodes){
			if(!impliedEvents.contains(node)){
				consumeNode(node);
				nodesToRemove.add(node);
			}
		}
		
		// remove the consumed nodes in the previous loop
		for(SandboxNode node : nodesToRemove){
			nodes.remove(node);
		}
		
		// create a copy of all the edges that only refer to nodes which are tagged as pcg nodes
		HashSet<SandboxEdge> edgesToRemove = new HashSet<SandboxEdge>();
		for (SandboxEdge edge : edges) {
			if (!nodes.contains(edge.getNode(EdgeDirection.FROM)) && nodes.contains(edge.getNode(EdgeDirection.TO)) ) {
				edgesToRemove.add(edge);
			}
		}
		
		// remove the disconnected edges in the previous loop
		for(SandboxEdge edge : edgesToRemove){
			this.edges.remove(edge);
		}
		
//		Graph pcg = Common.resolve(new NullProgressMonitor(), Common.toQ(new UncheckedGraph(nodes, edges)).retainEdges().eval());
		
//		// tag the pcg with the instance id
//		for(Node pcgNode : pcg.nodes()){
//			pcgNode.tag(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
//		}
//		for(Edge pcgEdge : pcg.edges()){
//			pcgEdge.tag(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
//		}
//		
//		// attribute the master entry node with the pcg instance parameters and default supplemental data
//		ucfg.getEntryNode().putAttr(PCG.EventFlow_Instance_Parameters_Prefix + pcgInstanceID, pcgParameters);
//		ucfg.getEntryNode().putAttr(PCG.EventFlow_Instance_SupplementalData_Prefix + pcgInstanceID, getDefaultSupplementalData());
		
		// construct the pcg object
		return new PCG(null);
	}
	
//	/**
//	 * Returns a JSON object string of default supplemental values for a PCG instance,
//	 * this list may optionally be extended to store additional details for PCGs without
//	 * affecting the core PCG implementation
//	 * @return
//	 */
//	@SuppressWarnings("unchecked")
//	private String getDefaultSupplementalData() {
//		JSONObject json = new JSONObject();
//		long time = System.currentTimeMillis();
//		json.put(PCG.JSON_CREATED, time);
//		json.put(PCG.JSON_LAST_ACCESSED, time);
//		json.put(PCG.JSON_GIVEN_NAME, "");
//		return json.toJSONString();
//	}

	/**
	 * Consumes the given non-event node bypassing it through connecting its
	 * predecessors with successors while preserving edges contents especially
	 * for branches.
	 * 
	 * @param node: non-event node to be removed from the final PCG
	 */
	private void consumeNode(SandboxNode node){
		// this function will consume the given node by bypassing it through
		// connecting its predecessors with successors while preserving edge's
		// conditional values
		
		// first: get the predecessors for the node
		SandboxHashSet<SandboxEdge> inEdges = getInEdgesToNode(node);
		HashMap<SandboxNode, SandboxEdge> predecessorEdgeMap = new HashMap<SandboxNode, SandboxEdge>(); 
		for(SandboxEdge inEdge : inEdges){
			SandboxNode predecessor = inEdge.getNode(EdgeDirection.FROM);
			predecessorEdgeMap.put(predecessor, inEdge);
		}
		// remove the case where the node has a self-loop. This will cause infinite recursion
		predecessorEdgeMap.keySet().remove(node);
		
		// second: get the successors for the node
		SandboxHashSet<SandboxEdge> outEdges = getOutEdgesFromNode(node);
		SandboxHashSet<SandboxNode> successors = sandbox.emptyNodeSet();
		for(SandboxEdge outEdge : outEdges){
			SandboxNode successor = outEdge.getNode(EdgeDirection.TO);
			successors.add(successor);
		}
		// remove the case where the node has a self-loop. This will cause infinite recursion
		successors.remove(node);
		
		for(SandboxNode predecessor : predecessorEdgeMap.keySet()){
			for(SandboxNode successor : successors){
				SandboxEdge oldEdge = predecessorEdgeMap.get(predecessor);
				SandboxEdge pcgEdge = this.getOrCreatePCGEdge(predecessor, successor, oldEdge.attr().containsKey(XCSG.conditionValue), oldEdge.attr().get(XCSG.conditionValue));
				edges.add(pcgEdge);
			}
			
			// duplicate edges maybe be formed at the predecessor because of
			// consuming the current node, so merge if needed
			this.removeDoubleEdges(predecessor);
		}
		
		// remove original inEdges for the node
		for(SandboxEdge inEdge : inEdges){
			edges.remove(inEdge);
		}
		
		// remove original outEdges for the node
		for(SandboxEdge outEdge : outEdges){
			edges.remove(outEdge);
		}
	}
	
	private void removeDoubleEdges(SandboxNode node){
		SandboxHashSet<SandboxEdge> outEdges = this.getOutEdgesFromNode(node);
		if(outEdges.size() < 2){
			return;
		}
		HashMap<SandboxNode, SandboxHashSet<SandboxEdge>> nodeEdgeMap = new HashMap<SandboxNode, SandboxHashSet<SandboxEdge>>();
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
				HashMap<String, Object> attrs = new HashMap<String, Object>();
				attrs.putAll(oldEdge.attr());
				attrs.remove(XCSG.conditionValue);
				SandboxEdge newEdge = this.getOrCreatePCGEdge(node, successor, oldEdge.attr().containsKey(XCSG.conditionValue), oldEdge.attr().get(XCSG.conditionValue));
				for(SandboxEdge successorEdge : successorEdges){
					edges.remove(successorEdge);
				}
				edges.add(newEdge);
			}
		}
	}
	
	/**
	 * Performs dominance analysis on graph to compute the dominance frontier for every node in the graph.
	 * Then, compute the final set of nodes that will be retained in the final sPCG
	 * @return The set of implied nodes that need to be retained in the final PCG
	 */
	private SandboxHashSet<SandboxNode> getImpliedEvents() {
		// start with the set of explict events to determine the implied events
		SandboxHashSet<SandboxNode> impliedEvents = sandbox.emptyNodeSet();
		impliedEvents.addAll(events);
		long preSize = 0;
		do {
			preSize = impliedEvents.size();
			SandboxHashSet<SandboxNode> newEvents = sandbox.emptyNodeSet();
			for (SandboxNode element : impliedEvents) {
				newEvents.addAll(dominanceFrontier.successors(element));
			}
			impliedEvents.addAll(newEvents);
		} while (preSize != impliedEvents.size());

		// add entry and exit nodes as event nodes as well
		impliedEvents.add(this.masterEntry);
		impliedEvents.add(this.masterExit);
		
		return impliedEvents;
	}
	
	/**
	 * Check if the intended PCG edge does exist as a CFG edges or already created PCG edge, otherwise, it creates the edge and applies the given attributes and tags
	 * @param from: the node where the edge originates from
	 * @param to: the node where the edge goes into
	 * @param attrs: the set of <String, Object> values that needs to be added to the newly created edge
	 * @param tags: the set of <String> tags that needs to be applied to the newly created edge
	 * @return the existent PCG edge or newly created PCG edge
	 */
	private SandboxEdge getOrCreatePCGEdge(SandboxNode from, SandboxNode to, boolean filterConditions, Object conditionValue) {
		// first - Check if there is an existing CFG edge, if there is tag it as an event flow edge
		SandboxHashSet<SandboxEdge> betweenEdges = sandbox.emptyEdgeSet();
		SandboxHashSet<SandboxEdge> cfgEdges = ucfg.betweenStep(from, to).edges();
		if (filterConditions) {
			betweenEdges = cfgEdges.filter(XCSG.conditionValue, conditionValue);
		} else {
			for (SandboxEdge edge : cfgEdges) {
				if (!edge.hasAttr(XCSG.conditionValue)) {
					betweenEdges.add(edge);
				}
			}
		}
		if (!betweenEdges.isEmpty()) {
			SandboxEdge edge = betweenEdges.one();
			edge.tag(PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID);
			return edge;
		}

		// second - check if there exists an EventFlow edge (for this instance), if there is use it
		// the edge to ignore removes previous edges so this is looks like a first run computation
		SandboxHashSet<SandboxEdge> pcgEdges = sandbox.toGraph(sandbox.U.edges(PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID)).betweenStep(from, to).edges();
		betweenEdges = sandbox.emptyEdgeSet();
		if (filterConditions) {
			betweenEdges = pcgEdges.filter(XCSG.conditionValue, conditionValue);
		} else {
			for (SandboxEdge edge : pcgEdges) {
				if (!edge.hasAttr(XCSG.conditionValue)) {
					betweenEdges.add(edge);
				}
			}
		}
		if (!betweenEdges.isEmpty()) {
			return betweenEdges.one();
		}

		// finally - create a new edge and use it
		SandboxEdge pcgEdge = sandbox.createEdge(from, to);
		pcgEdge.putAttr(XCSG.conditionValue, conditionValue);
		pcgEdge.tag(XCSG.Edge);
		pcgEdge.tag(PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID);
		
		Log.info("Created New Edge: " + pcgEdge.getAddress() + ", Tags: " + pcgEdge.tags().toString());
		
		return pcgEdge;
	}
	
	/**
	 * Gets incoming edges to node
	 * @param node
	 * @return The set of incoming edges to the given node
	 */
	private SandboxHashSet<SandboxEdge> getInEdgesToNode(SandboxNode node){
		SandboxHashSet<SandboxEdge> inEdges = sandbox.emptyEdgeSet();
		for(SandboxEdge edge : edges){
			if(edge.getNode(EdgeDirection.TO).equals(node)){
				inEdges.add(edge);
			}
		}
		return inEdges;
	}
	
	/**
	 * Gets out-coming edges from node
	 * @param node
	 * @return The set of out-coming edges from the given node
	 */
	private SandboxHashSet<SandboxEdge> getOutEdgesFromNode(SandboxNode node){
		SandboxHashSet<SandboxEdge> outEdges = sandbox.emptyEdgeSet();
		for(SandboxEdge edge : edges){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				outEdges.add(edge);
			}
		}
		return outEdges;
	}
}