package com.ensoftcorp.open.pcg.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.notification.NotificationHashMap;
import com.ensoftcorp.atlas.core.db.notification.NotificationMap;
import com.ensoftcorp.atlas.core.db.notification.NotificationSet;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.algorithms.DominanceAnalysis;
import com.ensoftcorp.open.commons.algorithms.DominanceAnalysis.Multimap;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;
import com.ensoftcorp.open.pcg.log.Log;

/**
 * A class that implements the event flow graph transformations to transform a given CFG into PCG
 * 
 * @author Ahmed Tamrawi, Ben Holland, Ganesh Ram Santhanam, Jon Mathews, Nikhil Ranade
 */
public class PCGFactory implements UniqueEntryExitGraph {
	
	// temporary variables for use in factory construction of a pcg
	private String pcgInstanceID;
	private String pcgParameters;
	private Node function;
	private AtlasSet<Node> events;
	private AtlasSet<Node> pcgNodes;
	private AtlasSet<Edge> pcgEdges;
	private Node masterEntry;
	private Node masterExit;
	
	/**
	 * The set of nodes associated with the PCG
	 * @return the set of nodes
	 */
	@Override
	public AtlasSet<Node> nodes() {
		return this.pcgNodes;
	}

	/**
	 * The set of edges associated with the PCG
	 * @return the set of edges
	 */
	@Override
	public AtlasSet<Edge> edges() {
		return this.pcgEdges;
	}
	
	/**
	 * Gets the predecessors of a given node
	 * @param node
	 * @return Predecessors of node
	 */
	@Override
	public AtlasSet<Node> getPredecessors(Node node){
		AtlasSet<Node> predecessors = new AtlasHashSet<Node>();
		for(Edge edge : this.edges()){
			if(edge.getNode(EdgeDirection.TO).equals(node)){
				Node parent = edge.getNode(EdgeDirection.FROM);
				predecessors.add(parent);
			}
		}
		return predecessors;
	}
	
	/**
	 * Gets the successors of a given node 
	 * @param node
	 * @return Successors of node
	 */
	@Override
	public AtlasSet<Node> getSuccessors(Node node){		
		AtlasSet<Node> successors = new AtlasHashSet<Node>();
		for(Edge edge : this.edges()){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				Node child = edge.getNode(EdgeDirection.TO);
				successors.add(child);
			}
		}
		return successors;
	}

	@Override
	public Node getEntryNode() {
		return this.masterEntry;
	}

	@Override
	public Node getExitNode() {
		return this.masterExit;
	}
	
	/** 
	 * 
	 * @param pcgInstanceID 
	 * 
	 * @param cfg a ControlFlowGraph (may include ExceptionalControlFlow_Edges)
	 * @param roots nodes to consider as control flow roots (entry points) in the graph
	 * @param events the set of nodes contained within the given graph
	 */
	/**
	 * Constructs a new PCG
	 * @param pcgInstanceID The suffix of the 
	 * @param function
	 * @param cfg
	 * @param roots
	 * @param exits
	 * @param events
	 */
	private PCGFactory(String pcgInstanceID, String pcgParameters, Node function, Graph cfg, AtlasSet<Node> roots, AtlasSet<Node> exits, AtlasSet<Node> events) {
		this.pcgInstanceID = pcgInstanceID;
		this.pcgParameters = pcgParameters;
		this.function = function;
		this.events = events;
		this.pcgNodes = new AtlasHashSet<Node>();
		this.nodes().addAll(cfg.nodes());
		this.pcgEdges = new AtlasHashSet<Edge>();
		this.edges().addAll(cfg.edges());
		this.masterEntry = setupMasterEntryNode(roots);
		this.masterExit = setupMasterExitNode(exits);
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
	 * @throws NoSuchAlgorithmException  
	 */
	public static PCG create(Q cfg, Q cfRoots, Q cfExits, Q events) {
		AtlasSet<Node> functions = CommonQueries.getContainingFunctions(cfg).eval().nodes();
		if(functions.isEmpty()){
			String message = "CFG is empty or is not contained within a function!";
			IllegalArgumentException e = new IllegalArgumentException(message);
			Log.error(message, e);
			throw e;
		} else if(functions.size() > 1){
			String message = "CFG should be restricted to a single function! Use IPCG factory for interprocedural PCGs.";
			IllegalArgumentException e = new IllegalArgumentException(message);
			Log.error(message, e);
			throw e;
		} else {
			Node function = functions.one();
			events = events.intersection(cfg);
			cfRoots = cfRoots.intersection(cfg);
			cfExits = cfExits.intersection(cfg);
			
			String pcgParameters = getPCGParametersJSONString(cfg.eval(), cfRoots.eval().nodes(), cfExits.eval().nodes(), events.eval().nodes());
			String pcgInstanceID = md5(pcgParameters);
			Q pcgInstance = Common.universe().nodes(PCG.EventFlow_Instance_Prefix + pcgInstanceID)
					.union(Common.universe().edges(PCG.EventFlow_Instance_Prefix + pcgInstanceID).retainEdges());
			if(!CommonQueries.isEmpty(pcgInstance)){
				Node masterEntry = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes().one();
				Node masterExit = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Exit).eval().nodes().one();
				PCG pcg = new PCG(pcgInstanceID, pcgInstance.eval(), function, masterEntry, masterExit);
				pcg.setUpdateLastAccessTime();
				return pcg;
			}
			
			// PCG does not exist or could not be found, compute the PCG now
			return new PCGFactory(pcgInstanceID, pcgParameters, function, cfg.eval(), cfRoots.eval().nodes(), cfExits.eval().nodes(), events.eval().nodes()).createPCG();
		}
	}
	
	@SuppressWarnings("unchecked")
	private static String getPCGParametersJSONString(Graph cfg, AtlasSet<Node> cfRoots, AtlasSet<Node> cfExits, AtlasSet<Node> events){
		// important note: do not modify this json list without special consideration
		// the result of this json object is used to compute the hash instance id of the PCG
		// only values that constitute pcg equivalences should be used to create this object
		events = Common.toQ(events).intersection(Common.toQ(cfg)).eval().nodes();
		cfRoots = Common.toQ(cfRoots).intersection(Common.toQ(cfg)).eval().nodes();
		cfExits = Common.toQ(cfExits).intersection(Common.toQ(cfg)).eval().nodes();
		
		JSONArray cfgNodeAddresses = new JSONArray();
		for(String address : getSortedAddressList(cfg.nodes())){
			cfgNodeAddresses.add(address);
		}
		
		JSONArray cfgEdgeAddresses = new JSONArray();
		for(String address : getSortedAddressList(cfg.edges())){
			cfgEdgeAddresses.add(address);
		}
		
		JSONArray rootAddresses = new JSONArray();
		for(String address : getSortedAddressList(cfRoots)){
			rootAddresses.add(address);
		}
		
		JSONArray exitAddresses = new JSONArray();
		for(String address : getSortedAddressList(cfExits)){
			exitAddresses.add(address);
		}
		
		JSONArray eventAddresses = new JSONArray();
		for(String address : getSortedAddressList(events)){
			eventAddresses.add(address);
		}
		
		JSONObject json = new JSONObject();
		json.put(PCG.JSON_CFG_NODES, cfgNodeAddresses);
		json.put(PCG.JSON_CFG_EDGES, cfgEdgeAddresses);
		json.put(PCG.JSON_ROOTS, rootAddresses);
		json.put(PCG.JSON_EXITS, exitAddresses);
		json.put(PCG.JSON_EVENTS, eventAddresses);
		
		return json.toJSONString();
	}
	
	/**
	 * Computes the MD5 hash of a string value
	 * @param value
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private static String md5(String value) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] array = md.digest(value.getBytes());
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < array.length; ++i) {
				sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
			}
			return sb.toString().toUpperCase();
		} catch (NoSuchAlgorithmException e) {
			Log.error("MD5 hashing is not supported!", e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Returns an alphabetically sorted list of graph element addresses
	 * @param graphElements
	 * @return
	 */
	private static List<String> getSortedAddressList(AtlasSet<? extends GraphElement> graphElements){
		ArrayList<String> addresses = new ArrayList<String>((int) graphElements.size());
		for(GraphElement graphElement : graphElements){
			addresses.add(graphElement.address().toAddressString());
		}
		Collections.sort(addresses);
		return addresses;
	}
	
	/**
	 * Creates the nodes and edges for setting up the master entry node
	 * @param roots nodes to consider as control flow roots (entry points) in the graph
	 */
	private Node setupMasterEntryNode(AtlasSet<Node> roots){
		// search if the function has a master entry node for any previously
		// created PCG
		// note we are reusing master entry nodes so the search should be from
		// the entire function cfg not just the specified roots
		Node masterEntryNode = Common.universe().edgesTaggedWithAll(PCGEdge.EventFlow_Edge)
				.predecessors(CommonQueries.cfg(function))
				.nodes(PCG.PCGNode.EventFlow_Master_Entry)
				.eval().nodes().one();
		
		// if master entry node has not been created by a previous pcg, then we
		// need to create one now
		if (masterEntryNode == null) {
			masterEntryNode = Graph.U.createNode();
			masterEntryNode.attr().put(XCSG.name, PCGNode.EventFlow_Master_Entry_Name);
			masterEntryNode.tag(PCGNode.EventFlow_Master_Entry);
		}
		
		// add the master entry node to the pcg
		this.nodes().add(masterEntryNode);
		
		// create pcg entry edges from the master entry to the pcg root nodes
		// and check if the entry edges exist before creating new ones
		for(Node root : roots){
			Edge entryEdge = this.getOrCreatePCGEdge(masterEntryNode, root, new NotificationHashMap<String, Object>(), null);
			this.edges().add(entryEdge);
		}
		return masterEntryNode;
	}
	
	/**
	 * Creates the nodes and edges for setting up the master exit node
	 * @param exits nodes to consider as control flow exits (exit points) in the graph
	 * @return
	 */
	private Node setupMasterExitNode(AtlasSet<Node> exits) {
		// search if the function has a master exit node for any previously
		// created PCG
		// note we are reusing master exit nodes so the search should be from
		// the entire function cfg not just the specified exits
		Node masterExitNode = Common.universe().edgesTaggedWithAll(PCGEdge.EventFlow_Edge)
				.successors(CommonQueries.cfg(function))
				.nodes(PCG.PCGNode.EventFlow_Master_Exit)
				.eval().nodes().one();
		
		// if master exit node has not been created by a previous pcg, then we
		// need to create one now
		if (masterExitNode == null) {
			masterExitNode = Graph.U.createNode();
			masterExitNode.attr().put(XCSG.name, PCGNode.EventFlow_Master_Exit_Name);
			masterExitNode.tag(PCGNode.EventFlow_Master_Exit);
		}
		
		// add the master exit node to the pcg
		this.nodes().add(masterExitNode);

		// create pcg exit edges from the pcg exits to the master exit node
		// and check if the exit edges exist before creating new ones
		for (Node exit : exits) {
			Edge exitEdge = this.getOrCreatePCGEdge(exit, masterExitNode, new NotificationHashMap<String, Object>(), null);
			this.edges().add(exitEdge);
		}
		return masterExitNode;
	}
	
	/**
	 * Given a CFG, construct PCG
	 * @return
	 */
	public PCG createPCG(){
		this.events = this.getImpliedEvents();
		for(Node node : this.events){
			node.tag(PCGNode.EventFlow_Node);
		}

		// retain a stack of node that are consumed to be removed from the graph after the loop
		AtlasSet<Node> nodesToRemove = new AtlasHashSet<Node>();
		for(Node node : this.nodes()){
			if(!this.events.contains(node)){
				this.consumeNode(node);
				nodesToRemove.add(node);
			}
		}
		
		// remove the consumed nodes in the previous loop
		for(Node node : nodesToRemove){
			this.nodes().remove(node);
		}
		
		// create a copy of all the nodes
		AtlasSet<Node> pcgNodeSet = new AtlasHashSet<Node>(this.nodes());

		// create a copy of all the edges that only refer to nodes which are tagged as pcg nodes
		AtlasSet<Edge> pcgEdgeSet = new AtlasHashSet<Edge>();
		for (Edge edge : this.edges()) {
			if (pcgNodeSet.contains(edge.getNode(EdgeDirection.FROM)) && pcgNodeSet.contains(edge.getNode(EdgeDirection.TO)) ) {
				pcgEdgeSet.add(edge);
			} else {
				Log.error("Internal error in PCG: edge not connected to pcg node: " + edge, new RuntimeException("Disconnected PCG Node"));
			}
		}
		
		// tag the pcg with the instance id
		for(Node pcgNode : pcgNodeSet){
			pcgNode.tag(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
		}
		for(Edge pcgEdge : pcgEdgeSet){
			pcgEdge.tag(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
		}
		
		// attribute the master entry node with the pcg instance parameters
		this.masterEntry.putAttr(PCG.EventFlow_Instance_Parameters_Prefix + pcgInstanceID, pcgParameters);
		this.masterEntry.putAttr(PCG.EventFlow_Instance_SupplementalData_Prefix + pcgInstanceID, getDefaultSupplementalData());
		
		// construct the pcg object
		return new PCG(pcgInstanceID, new UncheckedGraph(pcgNodeSet, pcgEdgeSet), function, getEntryNode(), getExitNode());	
	}
	
	/**
	 * Returns a JSON object string of default supplemental values for a PCG instance,
	 * this list may optionally be extended to store additional details for PCGs without
	 * affecting the core PCG implementation
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String getDefaultSupplementalData() {
		JSONObject json = new JSONObject();
		long time = System.currentTimeMillis();
		json.put(PCG.JSON_CREATED, time);
		json.put(PCG.JSON_LAST_ACCESSED, time);
		json.put(PCG.JSON_GIVEN_NAME, "");
		return json.toJSONString();
	}

	/**
	 * Consumes the given non-event node bypassing it through connecting its
	 * predecessors with successors while preserving edges contents especially
	 * for branches.
	 * 
	 * @param node: non-event node to be removed from the final PCG
	 */
	private void consumeNode(Node node){
		// this function will consume the given node by bypassing it through
		// connecting its predecessors with successors while preserving edges
		// contents especially for branches.
		
		// first: get the predecessors for the node
		AtlasSet<Edge> inEdges = this.getInEdgesToNode(node);
		HashMap<Node, Edge> predecessorEdgeMap = new HashMap<Node, Edge>(); 
		for(Edge inEdge : inEdges){
			Node predecessor = inEdge.getNode(EdgeDirection.FROM);
			predecessorEdgeMap.put(predecessor, inEdge);
		}
		// remove the case where the node has a self-loop. This will cause infinite recursion
		predecessorEdgeMap.keySet().remove(node);
		
		// second: get the successors for the node
		AtlasSet<Edge> outEdges = this.getOutEdgesFromNode(node);
		AtlasSet<Node> successors = new AtlasHashSet<Node>();
		for(Edge outEdge : outEdges){
			Node successor = outEdge.getNode(EdgeDirection.TO);
			successors.add(successor);
		}
		// remove the case where the node has a self-loop. This will cause infinite recursion
		successors.remove(node);
		
		for(Node predecessor : predecessorEdgeMap.keySet()){
			for(Node successor : successors){
				Edge oldEdge = predecessorEdgeMap.get(predecessor);
				NotificationMap<String, Object> attrs = new NotificationHashMap<String, Object>();
				attrs.putAll(oldEdge.attr());
				Edge newEdge = this.getOrCreatePCGEdge(predecessor, successor, attrs, oldEdge.tags());
				this.edges().add(newEdge);
			}
			
			// duplicate edges maybe be formed at the predecessor because of consuming the current node, so merge if needed
			this.removeDoubleEdges(predecessor);
		}
		
		// remove original inEdges for the node
		for(Edge inEdge : inEdges){
			this.edges().remove(inEdge);
		}
		
		// remove original outEdges for the node
		for(Edge outEdge : outEdges){
			this.edges().remove(outEdge);
		}
	}
	
	private void removeDoubleEdges(Node node){
		AtlasSet<Edge> outEdges = this.getOutEdgesFromNode(node);
		if(outEdges.size() < 2){
			return;
		}
		HashMap<Node, AtlasSet<Edge>> nodeEdgeMap = new HashMap<Node, AtlasSet<Edge>>();
		for(Edge outEdge : outEdges){
			Node successor = outEdge.getNode(EdgeDirection.TO);
			AtlasSet<Edge> edges = new AtlasHashSet<Edge>();
			if(nodeEdgeMap.containsKey(successor)){
				edges = nodeEdgeMap.get(successor);
			}
			edges.add(outEdge);
			nodeEdgeMap.put(successor, edges);
		}
		
		for(Node successor : nodeEdgeMap.keySet()){
			AtlasSet<Edge> edges = nodeEdgeMap.get(successor);
			if(edges.size() > 1){
				Edge oldEdge = edges.one();
				NotificationMap<String, Object> attrs = new NotificationHashMap<String, Object>();
				attrs.putAll(oldEdge.attr());
				attrs.remove(XCSG.conditionValue);
				Edge newEdge = this.getOrCreatePCGEdge(node, successor, attrs, oldEdge.tags());
				for(Edge edge : edges){
					this.edges().remove(edge);
				}
				this.edges().add(newEdge);
			}
		}
	}
	
	/**
	 * Performs dominance analysis on graph to compute the dominance frontier for every node in the graph.
	 * Then, compute the final set of nodes that will be retained in the final PCG
	 * @return The set of all nodes that need to be retained in the final PCG
	 */
	private AtlasSet<Node> getImpliedEvents() {
		DominanceAnalysis dominanceAnalysis = new DominanceAnalysis(this, true);
		Multimap<Node> dominanceFrontier = dominanceAnalysis.getDominanceFrontiers();

		AtlasSet<Node> impliedEvents = new AtlasHashSet<Node>(this.events);
		AtlasSet<Node> newEvents = null;
		long preSize = 0;
		do {
			preSize = impliedEvents.size();
			newEvents = new AtlasHashSet<Node>();
			for (Node element : impliedEvents) {
				newEvents.addAll(dominanceFrontier.get(element));
			}
			impliedEvents.addAll(newEvents);
		} while (preSize != impliedEvents.size());

		// add entry and exit nodes as event nodes as well
		impliedEvents.add(this.getEntryNode());
		impliedEvents.add(this.getExitNode());
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
	private Edge getOrCreatePCGEdge(Node from, Node to, NotificationMap<String, Object> attrs, NotificationSet<String> tags) {
		// first - Check if there exist CFG edge: tag it with EventFlow_Edge
		Q fromQ = Common.toQ(from);
		Q toQ = Common.toQ(to);
		AtlasSet<Edge> betweenEdges = new AtlasHashSet<Edge>();
		AtlasSet<Edge> cfgEdges = Common.universe().edges(XCSG.ControlFlow_Edge).betweenStep(fromQ, toQ).eval().edges();
		if (attrs.containsKey(XCSG.conditionValue)) {
			betweenEdges = cfgEdges.filter(XCSG.conditionValue, attrs.get(XCSG.conditionValue));
		} else {
			for (Edge edge : cfgEdges) {
				if (!edge.hasAttr(XCSG.conditionValue)) {
					betweenEdges.add(edge);
				}
			}
		}
		if (!betweenEdges.isEmpty()) {
			Edge edge = betweenEdges.one();
			tagPCGEdge(edge, false);
			return edge;
		}

		// second - Check if there exist PCG edge: use it
		AtlasSet<Edge> PCGEdges = Common.universe().edges(PCGEdge.EventFlow_Edge).betweenStep(fromQ, toQ).eval().edges();
		betweenEdges = new AtlasHashSet<Edge>();
		if (attrs.containsKey(XCSG.conditionValue)) {
			betweenEdges = PCGEdges.filter(XCSG.conditionValue, attrs.get(XCSG.conditionValue));
		} else {
			for (Edge edge : PCGEdges) {
				if (!edge.hasAttr(XCSG.conditionValue)) {
					betweenEdges.add(edge);
				}
			}
		}
		if (!betweenEdges.isEmpty()) {
			return betweenEdges.one();
		}

		// finally - Create a new edge
		Edge newEdge = Graph.U.createEdge(from, to);
		newEdge.putAllAttr(attrs);
		if (tags != null) {
			for (String tag : tags) {
				newEdge.tag(tag);
			}
		}
		tagPCGEdge(newEdge, true);
		return newEdge;
	}
	
	/**
	 * Tags the given edges with PCGEdge.EventFlow_Edge
	 * If newEdge is true, this function untags XCSG.ControlFlow_Edge and XCSG.ExceptionalControlFlow_Edge from the passed edge
	 * @param edge: edge to tag with PCGEdge.EventFlow_Edge
	 * @param newEdge: specifies whether the passed edge is newly created. If so: untags XCSG.ControlFlow_Edge and XCSG.ExceptionalControlFlow_Edge from edge
	 */
	private void tagPCGEdge(Edge edge, boolean newEdge){
		edge.tag(PCGEdge.EventFlow_Edge);
		edge.tag(XCSG.Edge);
		if(newEdge){
			edge.untag(XCSG.ControlFlow_Edge);
			edge.untag(XCSG.ExceptionalControlFlow_Edge);
		}
	}
	
	/**
	 * Gets incoming edges to node
	 * @param node
	 * @return The set of incoming edges to the given node
	 */
	private AtlasSet<Edge> getInEdgesToNode(Node node){
		AtlasSet<Edge> inEdges = new AtlasHashSet<Edge>();
		for(Edge edge : this.edges()){
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
	private AtlasSet<Edge> getOutEdgesFromNode(Node node){
		AtlasSet<Edge> outEdges = new AtlasHashSet<Edge>();
		for(Edge edge : this.edges()){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				outEdges.add(edge);
			}
		}
		return outEdges;
	}
}