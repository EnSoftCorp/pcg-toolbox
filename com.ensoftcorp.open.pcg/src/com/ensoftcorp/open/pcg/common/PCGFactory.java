package com.ensoftcorp.open.pcg.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
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
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitControlFlowGraph;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;
import com.ensoftcorp.open.pcg.log.Log;

/**
 * A class that implements the event flow graph transformations to transform a given CFG into PCG
 * 
 * @author Ahmed Tamrawi, Ben Holland, Ganesh Ram Santhanam, Jon Mathews, Nikhil Ranade
 */
public class PCGFactory {
	
	// temporary variables for use in factory construction of a pcg
	private UniqueEntryExitControlFlowGraph ucfg;
	private AtlasSet<Node> roots;
	private AtlasSet<Node> exits;
	private AtlasSet<Node> events;
	private AtlasSet<Node> nodes;
	private AtlasSet<Edge> edges;
	
	private String pcgInstanceID;
	private String pcgParameters;
	
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
	private PCGFactory(String pcgInstanceID, String pcgParameters, Graph cfg, AtlasSet<Node> roots, AtlasSet<Node> exits, AtlasSet<Node> events) {
		this.pcgInstanceID = pcgInstanceID;
		this.pcgParameters = pcgParameters;
		
		this.ucfg = new UniqueEntryExitControlFlowGraph(cfg, roots, exits);
		this.events = new AtlasHashSet<Node>(events);
		this.nodes = new AtlasHashSet<Node>(ucfg.getGraph().nodes());
		this.edges = new AtlasHashSet<Edge>(ucfg.getGraph().edges());
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
			
			Log.info("Nodes: " + cfg.eval().nodes().size() + ", Edges: " + cfg.eval().edges().size());
			
			String pcgParameters = getPCGParametersJSONString(cfg.eval(), cfRoots.eval().nodes(), cfExits.eval().nodes(), events.eval().nodes());
			Log.info("Creating PCG: " + pcgParameters);
			
			String pcgInstanceID = md5(pcgParameters);
			Q pcgInstance = Common.universe().nodes(PCG.EventFlow_Instance_Prefix + pcgInstanceID)
					.induce(Common.universe().edgesTaggedWithAll(PCG.EventFlow_Instance_Prefix + pcgInstanceID, PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID).retainEdges());
			if(!CommonQueries.isEmpty(pcgInstance)){
				Node masterEntry = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes().one();
				Node masterExit = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Exit).eval().nodes().one();
				PCG pcg = new PCG(pcgInstanceID, pcgInstance.eval(), function, masterEntry, masterExit);
				pcg.setUpdateLastAccessTime();
				return pcg;
			}
			
			// PCG does not exist or could not be found, compute the PCG now
			return new PCGFactory(pcgInstanceID, pcgParameters, cfg.eval(), cfRoots.eval().nodes(), cfExits.eval().nodes(), events.eval().nodes()).createPCG();
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
	 * Given a CFG, construct PCG
	 * @return
	 */
	public PCG createPCG(){
		AtlasSet<Node> impliedEvents = getImpliedEvents();

		// retain a stack of node that are consumed to be removed from the graph after the loop
		AtlasSet<Node> nodesToRemove = new AtlasHashSet<Node>();
		for(Node node : nodes){
			if(!impliedEvents.contains(node)){
				consumeNode(node);
				nodesToRemove.add(node);
			}
		}
		
		// remove the consumed nodes in the previous loop
		for(Node node : nodesToRemove){
			nodes.remove(node);
		}
		
		// create a copy of all the edges that only refer to nodes which are tagged as pcg nodes
		AtlasSet<Edge> edgesToRemove = new AtlasHashSet<Edge>();
		for (Edge edge : edges) {
			if (!nodes.contains(edge.getNode(EdgeDirection.FROM)) && nodes.contains(edge.getNode(EdgeDirection.TO)) ) {
				edgesToRemove.add(edge);
			}
		}
		
		// remove the disconnected edges in the previous loop
		for(Edge edge : edgesToRemove){
			this.edges.remove(edge);
		}
		
		Graph pcg = Common.resolve(new NullProgressMonitor(), Common.toQ(new UncheckedGraph(nodes, edges)).retainEdges().eval());
		
		// tag the pcg with the instance id
		for(Node pcgNode : pcg.nodes()){
			pcgNode.tag(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
		}
		for(Edge pcgEdge : pcg.edges()){
			pcgEdge.tag(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
		}
		
		// attribute the master entry node with the pcg instance parameters and default supplemental data
		ucfg.getEntryNode().putAttr(PCG.EventFlow_Instance_Parameters_Prefix + pcgInstanceID, pcgParameters);
		ucfg.getEntryNode().putAttr(PCG.EventFlow_Instance_SupplementalData_Prefix + pcgInstanceID, getDefaultSupplementalData());
		
		// construct the pcg object
		return new PCG(pcgInstanceID, pcg, ucfg.getFunction(), ucfg.getEntryNode(), ucfg.getExitNode());
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
		AtlasSet<Edge> inEdges = getInEdgesToNode(node);
		HashMap<Node, Edge> predecessorEdgeMap = new HashMap<Node, Edge>(); 
		for(Edge inEdge : inEdges){
			Node predecessor = inEdge.getNode(EdgeDirection.FROM);
			predecessorEdgeMap.put(predecessor, inEdge);
		}
		// remove the case where the node has a self-loop. This will cause infinite recursion
		predecessorEdgeMap.keySet().remove(node);
		
		// second: get the successors for the node
		AtlasSet<Edge> outEdges = getOutEdgesFromNode(node);
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
				Edge newEdge = this.getOrCreatePCGEdge(predecessor, successor, oldEdge.attr(), oldEdge.tags());
				edges.add(newEdge);
			}
			
			// duplicate edges maybe be formed at the predecessor because of
			// consuming the current node, so merge if needed
			this.removeDoubleEdges(predecessor);
		}
		
		// remove original inEdges for the node
		for(Edge inEdge : inEdges){
			edges.remove(inEdge);
		}
		
		// remove original outEdges for the node
		for(Edge outEdge : outEdges){
			edges.remove(outEdge);
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
			AtlasSet<Edge> successorEdges = nodeEdgeMap.get(successor);
			if(successorEdges.size() > 1){
				Edge oldEdge = successorEdges.one();
				NotificationMap<String, Object> attrs = new NotificationHashMap<String, Object>();
				attrs.putAll(oldEdge.attr());
				attrs.remove(XCSG.conditionValue);
				Edge newEdge = this.getOrCreatePCGEdge(node, successor, attrs, oldEdge.tags());
				for(Edge successorEdge : successorEdges){
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
	private AtlasSet<Node> getImpliedEvents() {
		DominanceAnalysis dominanceAnalysis = new DominanceAnalysis(ucfg, true);
		Multimap<Node> dominanceFrontier = dominanceAnalysis.getDominanceFrontiers();

		// start with the set of explict events to determine the implied events
		AtlasHashSet<Node> impliedEvents = new AtlasHashSet<Node>(events);
		long preSize = 0;
		do {
			preSize = impliedEvents.size();
			AtlasSet<Node> newEvents = new AtlasHashSet<Node>();
			for (Node element : impliedEvents) {
				newEvents.addAll(dominanceFrontier.get(element));
			}
			impliedEvents.addAll(newEvents);
		} while (preSize != impliedEvents.size());

		// add entry and exit nodes as event nodes as well
		impliedEvents.add(ucfg.getEntryNode());
		impliedEvents.add(ucfg.getExitNode());
		
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
		// first - Check if there is an existing CFG edge, if there is tag it as an event flow edge
		Q fromQ = Common.toQ(from);
		Q toQ = Common.toQ(to);
		AtlasSet<Edge> betweenEdges = new AtlasHashSet<Edge>();
		AtlasSet<Edge> cfgEdges = Common.toQ(ucfg.getCFG()).betweenStep(fromQ, toQ).eval().edges();
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
			edge.tag(PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID);
			return edge;
		}

		// second - check if there exists an EventFlow edge (for this instance), if there is use it
		// the edge to ignore removes previous edges so this is looks like a first run computation
		AtlasSet<Edge> pcgEdges = Common.universe().edges(PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID).betweenStep(fromQ, toQ).eval().edges();
		betweenEdges = new AtlasHashSet<Edge>();
		if (attrs.containsKey(XCSG.conditionValue)) {
			betweenEdges = pcgEdges.filter(XCSG.conditionValue, attrs.get(XCSG.conditionValue));
		} else {
			for (Edge edge : pcgEdges) {
				if (!edge.hasAttr(XCSG.conditionValue)) {
					betweenEdges.add(edge);
				}
			}
		}
		if (!betweenEdges.isEmpty()) {
			return betweenEdges.one();
		}

		// finally - create a new edge and use it
		Edge newEdge = Graph.U.createEdge(from, to);
		newEdge.putAllAttr(attrs);
		if (tags != null) {
			for (String tag : tags) {
				if(!tag.startsWith(PCGEdge.EventFlow_Edge_Instance_Prefix)){
					newEdge.tag(tag);
				}
			}
		}
		newEdge.tag(XCSG.Edge);
		newEdge.tag(PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID);
		newEdge.untag(XCSG.ControlFlow_Edge);
		newEdge.untag(XCSG.ExceptionalControlFlow_Edge);
		
		Log.info("Created New Edge: " + newEdge.address().toAddressString() + ", Tags: " + newEdge.tags().toString());
		
		return newEdge;
	}
	
	/**
	 * Gets incoming edges to node
	 * @param node
	 * @return The set of incoming edges to the given node
	 */
	private AtlasSet<Edge> getInEdgesToNode(Node node){
		AtlasSet<Edge> inEdges = new AtlasHashSet<Edge>();
		for(Edge edge : edges){
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
		for(Edge edge : edges){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				outEdges.add(edge);
			}
		}
		return outEdges;
	}
}