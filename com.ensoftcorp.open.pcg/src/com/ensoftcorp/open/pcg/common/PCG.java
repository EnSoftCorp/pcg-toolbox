package com.ensoftcorp.open.pcg.common;

import java.util.HashMap;

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
import com.ensoftcorp.open.pcg.log.Log;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitGraph;

/**
 * A class that implements the event flow graph transformations to transform a given CFG into PCG
 * 
 * @author Ahmed Tamrawi
 */
public class PCG implements UniqueEntryExitGraph {
	
	public static interface PCGNode {
		/**
		 * Tag applied to the newly created master entry node
		 */
		public static final String EventFlow_Master_Entry = "EventFlow_Master_Entry";
		
		/**
		 * Tag applied to the newly create master exit node
		 */
		public static final String EventFlow_Master_Exit = "EventFlow_Master_Exit";
		
		/**
		 * Tag applied to nodes that are retained in the final PCG
		 */
		public static final String EventFlow_Node = "EventFlow_Node";
		
		/**
		 * The name attribute applied to the EventFlow_Master_Entry of the PCG
		 */
		public static final String EventFlow_Master_Entry_Name = "\u22A4";
		
		/**
		 * The name attribute applied to the EventFlow_Master_Exit of the PCG
		 */
		public static final String EventFlow_Master_Exit_Name = "\u22A5";
	}
	
	public static interface PCGEdge{
		/**
		 * Tag applied to CFG edges that are retained in the final PCG
		 */
		public static final String EventFlow_Edge = "EventFlow_Edge";
	}
	
	/**
	 * Given: An Atlas set of nodes on the provided graph to retain in the final PCG
	 */
	private AtlasSet<Node> events;
	
	/**
	 * The set of nodes in the current graph
	 */
	private AtlasSet<Node> graph_nodes;
	
	/**
	 * The set of edges in the current graph
	 */
	private AtlasSet<Edge> graph_edges;
	
	/** 
	 * @param graph a ControlFlowGraph (may include ExceptionalControlFlow_Edges)
	 * @param events the set of nodes contained within the given graph
	 */
	public PCG(Graph graph, AtlasSet<Node> events) {
		this.events = events;
		this.graph_nodes = new AtlasHashSet<Node>();
		this.nodes().addAll(graph.nodes());
		this.graph_edges = new AtlasHashSet<Edge>();
		this.edges().addAll(graph.edges());
		this.setupMasterEntryNode();
		this.setupMasterExitNode();
	}
	
	/** 
	 * @param graph a ControlFlowGraph (may include ExceptionalControlFlow_Edges)
	 * @param roots nodes to consider as control flow roots (entry points) in the graph
	 * @param events the set of nodes contained within the given graph
	 */
	public PCG(Graph graph, AtlasSet<Node> roots, AtlasSet<Node> events) {
		this.events = events;
		this.graph_nodes = new AtlasHashSet<Node>();
		this.nodes().addAll(graph.nodes());
		this.graph_edges = new AtlasHashSet<Edge>();
		this.edges().addAll(graph.edges());
		this.setupMasterEntryNode(roots);
		this.setupMasterExitNode();
	}
	
	/**
	 * Creates the nodes and edges for setting up the master entry node
	 */
	private GraphElement setupMasterEntryNode(){
		AtlasSet<Node> roots = new AtlasHashSet<Node>();
		roots.addAll(this.nodes().taggedWithAll(XCSG.controlFlowRoot)); // TODO: check if this is the correct way to make an AtlasSet<Node> out of the result of taggedWithAll()
		return setupMasterEntryNode(roots);
	}
	
	/**
	 * Creates the nodes and edges for setting up the master entry node
	 * @param roots nodes to consider as control flow roots (entry points) in the graph
	 */
	private GraphElement setupMasterEntryNode(AtlasSet<Node> roots){
		AtlasSet<Node> predecessors = Common.universe().edgesTaggedWithAll(PCGEdge.EventFlow_Edge).predecessors(Common.toQ(roots)).eval().nodes();
		//Retain only the nodes tagged with PCGNode.EventFlow_Master_Entry, there are maybe other non entry nodes 
		AtlasSet<Node> masterEntryNodes = predecessors.taggedWithAll(PCGNode.EventFlow_Master_Entry);
		GraphElement masterEntryNode = null;
		
		// Check if entry node already exists before creating a new one
		if(masterEntryNodes.isEmpty()){
			//Log.info("Creating PCGNode.EventFlow_Master_Entry.");
			masterEntryNode = Graph.U.createNode();
			masterEntryNode.tag(PCGNode.EventFlow_Master_Entry);
			masterEntryNode.attr().put(XCSG.name, PCGNode.EventFlow_Master_Entry_Name);
		}else{
			if(masterEntryNodes.size() > 1){
				Log.warning("Internal error in PCG: multiple EventFlow_Master_Entry nodes exist before: " + masterEntryNodes.getFirst());
			}
			masterEntryNode = masterEntryNodes.getFirst();
		}
		this.nodes().add(masterEntryNode);
		
		// Check if the entry edges do exist before creating new ones
		for(GraphElement root : roots){
			GraphElement newEntryEdge = this.getPCGEdge(masterEntryNode, root, new NotificationHashMap<String, Object>(), null);
			/*
			GraphElement newEntryEdge = this.getExistingPCGEdge(masterEntryNode, root);
			if(newEntryEdge == null){
				newEntryEdge = Graph.U.createEdge(masterEntryNode, root);
				tagPCGEdge(newEntryEdge, true);
			}
			*/
			this.edges().add(newEntryEdge);
		}
		return masterEntryNode;
	}
	
	/**
	 * Creates the nodes and edges for setting up the master exit node
	 */
	private GraphElement setupMasterExitNode(){
		AtlasSet<Node> exits = this.nodes().taggedWithAll(XCSG.controlFlowExitPoint);
		AtlasSet<Node> successors = Common.universe().edgesTaggedWithAll(PCGEdge.EventFlow_Edge).successors(Common.toQ(exits)).eval().nodes();
		//Retain only the nodes tagged with PCGNode.EventFlow_Master_Exit, there are maybe other non exit nodes 
		AtlasSet<Node> masterExitNodes = successors.taggedWithAll(PCGNode.EventFlow_Master_Exit);
		GraphElement masterExitNode = null;
		
		// Check if exit node already exists before creating a new one
		if(masterExitNodes.isEmpty()){
			//Log.info("Creating PCGNode.EventFlow_Master_Exit.");
			masterExitNode = Graph.U.createNode();
			masterExitNode.attr().put(XCSG.name, PCGNode.EventFlow_Master_Exit_Name);
			masterExitNode.tag(PCGNode.EventFlow_Master_Exit);
		}else{
			if(masterExitNodes.size() > 1){
				Log.warning("Internal error in PCG: multiple EventFlow_Master_Exit nodes exist before: " + masterExitNodes.getFirst());
			}
			masterExitNode = masterExitNodes.getFirst();			
		}
		this.nodes().add(masterExitNode);
		
		// Check if the exit edges do exist before creating new ones
		for(GraphElement exit : exits){
			GraphElement newExitEdge = this.getPCGEdge(exit, masterExitNode, new NotificationHashMap<String, Object>(), null); 
			/*
			GraphElement newExitEdge = this.getExistingPCGEdge(exit, masterExitNode);
			if(newExitEdge == null){
				newExitEdge = Graph.U.createEdge(exit, masterExitNode);
				//newExitEdge.tag(PCGEdge.EventFlow_Exit_Edge);
				tagPCGEdge(newExitEdge, true);
			}
			*/
			this.edges().add(newExitEdge);
		}
		return masterExitNode;
	}
	
	/**
	 * Check if an PCG
	 * @param from
	 * @param to
	 * @return
	 */
	/*
	private GraphElement getExistingPCGEdge(GraphElement from, GraphElement to){
		Q PCGEdges = Common.universe().edgesTaggedWithAll(PCGEdge.EventFlow_Edge);
		AtlasSet<GraphElement> betweenEdges = PCGEdges.betweenStep(Common.toQ(from), Common.toQ(to)).eval().edges();
		if(betweenEdges.isEmpty()){
			return null;
		}
		return betweenEdges.getFirst();
	}
	*/
	
	/**
	 * Given a CFG, construct PCG
	 * @return
	 */
	public Q createPCG(){
		this.events = this.getImpliedEvents();
		for(GraphElement node : this.events){
			node.tag(PCGNode.EventFlow_Node);
		}

		// Retain a stack of node that are consumed to be removed from the graph after the loop
		AtlasSet<GraphElement> toRemoveNodes = new AtlasHashSet<GraphElement>();
		for(GraphElement node : this.nodes()){
			if(!this.events.contains(node)){
				this.consumeNode(node);
				toRemoveNodes.add(node);
			}
		}
		
		// Remove the consumed nodes in the previous loop
		for(GraphElement node : toRemoveNodes){
			this.nodes().remove(node);
		}
		
		AtlasSet<GraphElement> ns = new AtlasHashSet<GraphElement>();
		ns.addAll(this.nodes());
		
		
		// assert: edges only refer to nodes which are tagged PCG_NODE 
		AtlasSet<GraphElement> es = new AtlasHashSet<GraphElement>();
		for (GraphElement edge : this.edges()) {
			if (ns.contains(edge.getNode(EdgeDirection.FROM))
				&& ns.contains(edge.getNode(EdgeDirection.TO)) ) {
				es.add(edge);
			} else {
				// error
				Log.warning("Internal error in PCG: edge not connected to PCG_NODE: " + edge);
			}
		}
		
		Q PCG = Common.toQ(new UncheckedGraph(ns, es));

		return PCG;	
	}
	
	/*
	private void retainNode(GraphElement node){
		AtlasSet<GraphElement> inEdges = this.getInEdgesToNode(node);
		for(GraphElement inEdge : inEdges){
			GraphElement predecessor = inEdge.getNode(EdgeDirection.FROM);
			if(predecessor.taggedWith(PCGNode.EventFlow_Node)){
				tagPCGEdge(inEdge, false);
				this.edges().add(inEdge);
			}
		}
		
		AtlasSet<GraphElement> outEdges = this.getOutEdgesFromNode(node);
		for(GraphElement outEdge : outEdges){
			GraphElement successor = outEdge.getNode(EdgeDirection.TO);
			if(successor.taggedWith(PCGNode.EventFlow_Node)){
				tagPCGEdge(outEdge, false);
				this.edges().add(outEdge);
			}
		}
	}
	*/
	
	/**
	 * Subsumes the given non-event node bypassing it through connecting its predecessors with successors
	 * while preserving edges contents especially for branches
	 * @param node: non-event node to be removed from the final PCG
	 */
	private void consumeNode(GraphElement node){
		// This function will consume the given node by bypassing it through connecting its predecessors with successors
		// while preserving edges contents especially for branches.
		
		// First: get the predecessors for the node
		AtlasSet<GraphElement> inEdges = this.getInEdgesToNode(node);
		HashMap<GraphElement, GraphElement> predecessorEdgeMap = new HashMap<GraphElement, GraphElement>(); 
		for(GraphElement inEdge : inEdges){
			GraphElement predecessor = inEdge.getNode(EdgeDirection.FROM);
			predecessorEdgeMap.put(predecessor, inEdge);
		}
		// Remove the case where the node has a self-loop. This will cause infinite recursion
		predecessorEdgeMap.keySet().remove(node);
		
		// Second: get the successors for the node
		AtlasSet<GraphElement> outEdges = this.getOutEdgesFromNode(node);
		AtlasSet<GraphElement> successors = new AtlasHashSet<GraphElement>();
		for(GraphElement outEdge : outEdges){
			GraphElement successor = outEdge.getNode(EdgeDirection.TO);
			successors.add(successor);
		}
		// Remove the case where the node has a self-loop. This will cause infinite recursion
		successors.remove(node);
		
		for(GraphElement predecessor : predecessorEdgeMap.keySet()){
			for(GraphElement successor : successors){
				GraphElement oldEdge = predecessorEdgeMap.get(predecessor);
				/*
				GraphElement newEdge = Graph.U.createEdge(predecessor, successor);
				newEdge.putAllAttr(oldEdge.attr());
				for(String tag : oldEdge.tags()){
					newEdge.tag(tag);
				}
				tagPCGEdge(newEdge, true);
				*/
				NotificationMap<String, Object> attrs = new NotificationHashMap<String, Object>();
				attrs.putAll(oldEdge.attr());
				GraphElement newEdge = this.getPCGEdge(predecessor, successor, attrs, oldEdge.tags());
				this.edges().add(newEdge);
			}
			
			// Duplicate edges maybe be formed at the predecessor because of consuming the current node, so merge if needed
			this.removeDoubleEdges(predecessor);
		}
		
		// Remove original inEdges for the node
		for(GraphElement inEdge : inEdges){
			this.edges().remove(inEdge);
		}
		
		// Remove original outEdges for the node
		for(GraphElement outEdge : outEdges){
			this.edges().remove(outEdge);
		}
	}
	
	private void removeDoubleEdges(GraphElement node){
		AtlasSet<GraphElement> outEdges = this.getOutEdgesFromNode(node);
		if(outEdges.size() < 2){
			return;
		}
		HashMap<GraphElement, AtlasSet<GraphElement>> map = new HashMap<GraphElement, AtlasSet<GraphElement>>();
		for(GraphElement outEdge : outEdges){
			GraphElement successor = outEdge.getNode(EdgeDirection.TO);
			AtlasSet<GraphElement> edges = new AtlasHashSet<GraphElement>();
			if(map.containsKey(successor)){
				edges = map.get(successor);
			}
			edges.add(outEdge);
			map.put(successor, edges);
		}
		
		for(GraphElement successor : map.keySet()){
			AtlasSet<GraphElement> edges = map.get(successor);
			if(edges.size() > 1){
				GraphElement oldEdge = edges.getFirst();
				/*
				GraphElement newEdge = Graph.U.createEdge(node, successor);
				newEdge.putAllAttr(oldEdge.attr());
				for(String tag : oldEdge.tags()){
					newEdge.tag(tag);
				}
				tagPCGEdge(newEdge, true);
				newEdge.removeAttr(XCSG.conditionValue);
				*/
				NotificationMap<String, Object> attrs = new NotificationHashMap<String, Object>();
				attrs.putAll(oldEdge.attr());
				attrs.remove(XCSG.conditionValue);
				GraphElement newEdge = this.getPCGEdge(node, successor, attrs, oldEdge.tags());
				for(GraphElement e : edges){
					this.edges().remove(e);
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
	private AtlasSet<Node> getImpliedEvents(){
		DominanceAnalysis dominanceAnalysis = new DominanceAnalysis(this, true);
		Multimap<Node> dominanceFrontier = dominanceAnalysis.getDominanceFrontiers();
		
		AtlasSet<Node> elements = new AtlasHashSet<Node>(this.events);
		AtlasSet<Node> newElements = null;
		long preSize = 0;
		do{
			preSize = elements.size();
			newElements = new AtlasHashSet<Node>();
			for(GraphElement element : elements){
				newElements.addAll(dominanceFrontier.get(element));
			}
			elements.addAll(newElements);
		}while(preSize != elements.size());
		
		// Add entry and exit nodes as event nodes as well
		elements.add(this.getEntryNode());
		elements.add(this.getExitNode());	
		return elements;
	}
	
	/**
	 * Check if the intended PCG edge does exist as a CFG edges or already created PCG edge, otherwise, it creates the edge and applies the given attributes and tags
	 * @param from: the node where the edge originates from
	 * @param to: the node where the edge goes into
	 * @param attrs: the set of <String, Object> values that needs to be added to the newly created edge
	 * @param tags: the set of <String> tags that needs to be applied to the newly created edge
	 * @return the existent PCG edge or newly created PCG edge
	 */
	private GraphElement getPCGEdge(GraphElement from, GraphElement to, NotificationMap<String, Object> attrs, NotificationSet<String> tags){
		// 1- Check if there exist CFG edge: tag it with EventFlow_Edge
		Q fromQ = Common.toQ(from);
		Q toQ = Common.toQ(to);
		AtlasSet<Edge> betweenEdges = new AtlasHashSet<Edge>();
		AtlasSet<Edge> cfgEdges = Common.universe().edgesTaggedWithAll(XCSG.ControlFlow_Edge).betweenStep(fromQ, toQ).eval().edges();
		if(attrs.containsKey(XCSG.conditionValue)){
			betweenEdges = cfgEdges.filter(XCSG.conditionValue, attrs.get(XCSG.conditionValue)); 
		}else{
			for(GraphElement edge : cfgEdges){
				if(!edge.hasAttr(XCSG.conditionValue)){
					betweenEdges.add(edge);
				}
			}
		}
		if(!betweenEdges.isEmpty()){
			GraphElement edge = betweenEdges.getFirst();
			tagPCGEdge(edge, false);
			return edge;
		}
		
		// 2- Check if there exist PCG edge: use it
		AtlasSet<Edge> PCGEdges = Common.universe().edgesTaggedWithAll(PCGEdge.EventFlow_Edge).betweenStep(fromQ, toQ).eval().edges();
		betweenEdges = new AtlasHashSet<Edge>();
		if(attrs.containsKey(XCSG.conditionValue)){
			betweenEdges = PCGEdges.filter(XCSG.conditionValue, attrs.get(XCSG.conditionValue)); 
		}else{
			for(GraphElement edge : PCGEdges){
				if(!edge.hasAttr(XCSG.conditionValue)){
					betweenEdges.add(edge);
				}
			}
		}
		if(!betweenEdges.isEmpty()){
			return betweenEdges.getFirst();
		}
		
		// 3- Create a new edge
		//Log.info("Creating a new PCG edge.");
		GraphElement newEdge = Graph.U.createEdge(from, to);
		newEdge.putAllAttr(attrs);
		if(tags != null){
			for(String tag : tags){
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
	private void tagPCGEdge(GraphElement edge, boolean newEdge){
		edge.tag(PCGEdge.EventFlow_Edge);
		edge.tag(XCSG.Edge);
		if(newEdge){
			edge.untag(XCSG.ControlFlow_Edge);
			edge.untag(XCSG.ExceptionalControlFlow_Edge);
		}
	}
	
	/**
	 * The set of nodes associated with the PCG
	 * @return the set of nodes
	 */
	@Override
	public AtlasSet<Node> nodes() {
		return this.graph_nodes;
	}

	/**
	 * The set of edges associated with the PCG
	 * @return the set of edges
	 */
	@Override
	public AtlasSet<Edge> edges() {
		return this.graph_edges;
	}
	
	/**
	 * Gets the predecessors of a given node
	 * @param node
	 * @return Predecessors of node
	 */
	@Override
	public AtlasSet<Node> getPredecessors(Node node){
		AtlasSet<Node> predecessors = new AtlasHashSet<Node>();
		for(GraphElement edge : this.edges()){
			if(edge.getNode(EdgeDirection.TO).equals(node)){
				GraphElement parent = edge.getNode(EdgeDirection.FROM);
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
		for(GraphElement edge : this.edges()){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				GraphElement child = edge.getNode(EdgeDirection.TO);
				successors.add(child);
			}
		}
		return successors;
	}
	
	/**
	 * Gets incoming edges to node
	 * @param node
	 * @return The set of incoming edges to the given node
	 */
	public AtlasSet<GraphElement> getInEdgesToNode(GraphElement node){
		AtlasSet<GraphElement> inEdges = new AtlasHashSet<GraphElement>();
		for(GraphElement edge : this.edges()){
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
	public AtlasSet<GraphElement> getOutEdgesFromNode(GraphElement node){
		AtlasSet<GraphElement> outEdges = new AtlasHashSet<GraphElement>();
		for(GraphElement edge : this.edges()){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				outEdges.add(edge);
			}
		}
		return outEdges;
	}

	@Override
	public Node getEntryNode() {
		return this.nodes().taggedWithAll(PCGNode.EventFlow_Master_Entry).getFirst();
	}

	@Override
	public Node getExitNode() {
		return this.nodes().taggedWithAll(PCGNode.EventFlow_Master_Exit).getFirst();
	}
}