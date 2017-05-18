package com.ensoftcorp.open.pcg.common;

import java.util.HashSet;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.xcsg.XCSG_Extension;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;

public class PCG {

	// constants for serializing pcg parameters
	protected static final String JSON_CFG_NODES = "cfg-nodes";
	protected static final String JSON_CFG_EDGES = "cfg-edges";
	protected static final String JSON_ROOTS = "roots";
	protected static final String JSON_EXITS = "exits";
	protected static final String JSON_EVENTS = "events";
	protected static final String JSON_CREATED = "created";
	protected static final String JSON_LAST_ACCESSED = "last-accessed";
	protected static final String JSON_GIVEN_NAME = "given-name";

	/**
	 * A tag prefix used to tag the PCG nodes and edges that identify a
	 * unique PCG instance serialized in the Atlas graph. The tag suffix
	 * is an MD5 hash of the value of the EventFlow_Instance_Parameters
	 * attribute.
	 */
	@XCSG_Extension
	public static final String EventFlow_Instance_Prefix = "EventFlow_Instance_";
	
	/**
	 * An attribute applied to the EventFlow_Master_Entry node that maps to
	 * the JSON serialized PCG instance parameters used to create the pcg
	 */
	@XCSG_Extension
	public static final String EventFlow_Instance_Parameters_Prefix = "EventFlow_Instance_Parameters_";

	/**
	 * An attribute applied to the EventFlow_Master_Entry node that maps to
	 * the JSON serialized PCG instance supplemental parameters stored for the PCG
	 */
	@XCSG_Extension
	public static final String EventFlow_Instance_SupplementalData_Prefix = "EventFlow_Instance_SupplementalData_";
	
	/**
	 * Defines tags and attributes for PCG nodes
	 */
	public static interface PCGNode {
		/**
		 * Tag applied to the newly created master entry node
		 */
		@XCSG_Extension
		public static final String EventFlow_Master_Entry = "EventFlow_Master_Entry";
		
		/**
		 * Tag applied to the newly create master exit node
		 */
		@XCSG_Extension
		public static final String EventFlow_Master_Exit = "EventFlow_Master_Exit";
		
		/**
		 * The name attribute applied to the EventFlow_Master_Entry of the PCG
		 */
		@XCSG_Extension
		public static final String EventFlow_Master_Entry_Name = "\u22A4";
		
		/**
		 * The name attribute applied to the EventFlow_Master_Exit of the PCG
		 */
		@XCSG_Extension
		public static final String EventFlow_Master_Exit_Name = "\u22A5";
	}
	
	/**
	 * Defines tags and attributes for PCG edges
	 */
	public static interface PCGEdge {
		/**
		 * Tag applied to CFG edges that are retained in the final PCG
		 */
		@XCSG_Extension
		public static final String EventFlow_Edge_Instance_Prefix = "EventFlow_Edge_Instance_";
		
		public static final String EventFlow_Edge = "EventFlow_Edge";
	}
	
	/**
	 * Returns a set of all PCG instances
	 * @return
	 */
	public static Set<PCG> getInstances(){
		AtlasSet<Node> pcgMasterEntries = Common.universe().nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes();
		Set<PCG> pcgs = new HashSet<PCG>();
		for(Node pcgMasterEntry : pcgMasterEntries){
			for(String attributeKey : pcgMasterEntry.attr().keys()){
				if(attributeKey.startsWith(PCG.EventFlow_Instance_Parameters_Prefix)){
					String instanceID = attributeKey.substring(PCG.EventFlow_Instance_Parameters_Prefix.length());
					pcgs.add(getInstance(instanceID));
				}
			}
		}
		return pcgs;
	}
	
	/**
	 * Returns the PCG instance corresponding to the given PCG instance id
	 * @param pcgInstanceID
	 * @return
	 */
	public static PCG getInstance(String pcgInstanceID) {
		Q pcgInstance = Common.universe().nodes(PCG.EventFlow_Instance_Prefix + pcgInstanceID)
				.union(Common.universe().edges(PCG.EventFlow_Instance_Prefix + pcgInstanceID).retainEdges());
		if(!CommonQueries.isEmpty(pcgInstance)){
			Node function = CommonQueries.getContainingFunctions(pcgInstance.nodes(XCSG.ControlFlow_Node)).eval().nodes().one();
			Node masterEntry = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes().one();
			Node masterExit = pcgInstance.nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes().one();
			return new PCG(pcgInstanceID, pcgInstance.eval(), function, masterEntry, masterExit);
		} else {
			throw new IllegalArgumentException("PCG instance " + pcgInstanceID + " does not exist.");
		}
	}
	
	/**
	 * Deletes all PCG instances 
	 * @param pcgInstanceID
	 */
	public static void deleteInstances(){
		AtlasSet<Node> pcgMasterEntries = Common.universe().nodes(PCG.PCGNode.EventFlow_Master_Entry).eval().nodes();
		Set<String> instances = new HashSet<String>();
		for(Node pcgMasterEntry : pcgMasterEntries){
			for(String attributeKey : pcgMasterEntry.attr().keys()){
				if(attributeKey.startsWith(PCG.EventFlow_Instance_Parameters_Prefix)){
					String instanceID = attributeKey.substring(PCG.EventFlow_Instance_Parameters_Prefix.length());
					instances.add(instanceID);
				}
			}
		}
		for(String instance : instances){
			deleteInstance(instance);
		}
	}
	
	/**
	 * Deletes a PCG instance corresponding to the given PCG instance id 
	 * @param pcgInstanceID
	 */
	public static void deleteInstance(String pcgInstanceID){
		Q pcgInstance = Common.universe().nodes(PCG.EventFlow_Instance_Prefix + pcgInstanceID)
				.induce(Common.universe().edgesTaggedWithAll(PCG.EventFlow_Instance_Prefix + pcgInstanceID, PCGEdge.EventFlow_Edge_Instance_Prefix + pcgInstanceID).retainEdges());
		if(!CommonQueries.isEmpty(pcgInstance)){
			Graph pcg = pcgInstance.eval();
			AtlasSet<Edge> edges = new AtlasHashSet<Edge>(pcg.edges());
			AtlasSet<Edge> edgesToRemove = new AtlasHashSet<Edge>();
			AtlasSet<Node> nodes = new AtlasHashSet<Node>(pcg.nodes());
			AtlasSet<Node> nodesToRemove = new AtlasHashSet<Node>();
			for(Edge edge : edges){
				edge.tags().remove(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
				if(!edge.taggedWith(XCSG.ControlFlow_Edge)){
					boolean hasAnotherInstance = false;
					for(String tag : edge.tags()){
						if(tag.startsWith(PCG.EventFlow_Instance_Prefix)){
							hasAnotherInstance = true;
							break;
						}
					}
					if(!hasAnotherInstance){
						edgesToRemove.add(edge);
					}
				}
			}
			for(Edge edge : edgesToRemove){
				Graph.U.delete(edge);
			}
			for(Node node : nodes){
				node.tags().remove(PCG.EventFlow_Instance_Prefix + pcgInstanceID);
				node.tags().remove(PCG.EventFlow_Instance_Parameters_Prefix + pcgInstanceID);
				node.tags().remove(PCG.EventFlow_Instance_SupplementalData_Prefix + pcgInstanceID);
				if(node.taggedWith(PCG.PCGNode.EventFlow_Master_Entry) || node.taggedWith(PCG.PCGNode.EventFlow_Master_Exit)){
					boolean hasAnotherInstance = false;
					for(String tag : node.tags()){
						if(tag.startsWith(PCG.EventFlow_Instance_Prefix)){
							hasAnotherInstance = true;
							break;
						}
					}
					if(!hasAnotherInstance){
						nodesToRemove.add(node);
					}
				}
			}
			for(Node node : nodesToRemove){
				Graph.U.delete(node);
			}
		}
	}

	private Graph pcg;
	private Node function;
	private Node masterEntry;
	private Node masterExit;
	private String id;
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PCG other = (PCG) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	protected PCG(String id, Graph pcg, Node function, Node masterEntry, Node masterExit) {
		this.id = id;
		this.pcg = pcg;
		this.function = function;
		this.masterEntry = masterEntry;
		this.masterExit = masterExit;
	}
	
	/**
	 * Returns the PCG's instance ID. An instance ID is a unique string
	 * value corresponding to the EventFlow_Instance attribute, which is
	 * assigned to all of the PCG's nodes and edges. This value can be used
	 * to recover a serialized PCG from the Atlas graph.
	 * 
	 * @return
	 */
	public String getInstanceID(){
		return id;
	}
	
	/**
	 * Returns the complete PCG (including master entry and exit nodes) as a Q
	 * @return
	 */
	public Q getPCG() {
		return Common.toQ(pcg);
	}

	/**
	 * Returns the original control flow graph used to create this pcg
	 * @return
	 */
	public Q getCFG(){
		// decode the cfg nodes
		AtlasSet<Node> cfgNodes = new AtlasHashSet<Node>();
		try {
			JSONArray cfgNodeAddresses = (JSONArray) getParameters().get(JSON_CFG_NODES);
			for(Object address : cfgNodeAddresses){
				Node cfgNode = (Node) getGraphElementByAddress(address.toString());
				cfgNodes.add(cfgNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_CFG_NODES + " parameters.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		// decode the cfg edges
		AtlasSet<Edge> cfgEdges = new AtlasHashSet<Edge>();
		try {
			JSONArray cfgEdgeAddresses = (JSONArray) getParameters().get(JSON_CFG_EDGES);
			for(Object address : cfgEdgeAddresses){
				Edge cfgEdge = (Edge) getGraphElementByAddress(address.toString());
				cfgEdges.add(cfgEdge);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_CFG_EDGES + " parameters.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		return Common.toQ(new UncheckedGraph(cfgNodes, cfgEdges));
	}
	
	/**
	 * Returns the selected control flow events used to create this pcg
	 * @return
	 */
	public Q getEvents(){
		AtlasSet<Node> events = new AtlasHashSet<Node>();
		try {
			JSONArray eventAddresses = (JSONArray) getParameters().get(JSON_EVENTS);
			for(Object address : eventAddresses){
				Node event = (Node) getGraphElementByAddress(address.toString());
				events.add(event);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_EVENTS + " parameters.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		return Common.toQ(events);
	}
	
	/**
	 * Returns the selected control flow roots used to create this pcg
	 * @return
	 */
	public Q getRoots(){
		AtlasSet<Node> roots = new AtlasHashSet<Node>();
		try {
			JSONArray rootAddresses = (JSONArray) getParameters().get(JSON_ROOTS);
			for(Object address : rootAddresses){
				Node root = (Node) getGraphElementByAddress(address.toString());
				roots.add(root);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_ROOTS + " parameters.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		return Common.toQ(roots);
	}
	
	/**
	 * Returns the selected control flow exits used to create this pcg
	 * @return
	 */
	public Q getExits(){
		AtlasSet<Node> exits = new AtlasHashSet<Node>();
		try {
			JSONArray exitAddresses = (JSONArray) getParameters().get(JSON_EXITS);
			for(Object address : exitAddresses){
				Node exit = (Node) getGraphElementByAddress(address.toString());
				exits.add(exit);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_EXITS + " parameters.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		return Common.toQ(exits);
	}
	
	/**
	 * Returns the user given name of this pcg or an empty string if one was not given
	 * @return
	 */
	public String getGivenName(){
		try {
			return (String) getSupplementalData().get(JSON_GIVEN_NAME);
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_GIVEN_NAME + " supplemental data.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
	}
	
	/**
	 * Returns the time this pcg was created at
	 * @return
	 */
	public long getCreationTime(){
		try {
			return (long) getSupplementalData().get(JSON_CREATED);
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_CREATED + " supplemental data.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
	}
	
	/**
	 * Returns the time this pcg was created at
	 * @return
	 */
	public long getLastAccessTime(){
		try {
			return (long) getSupplementalData().get(JSON_LAST_ACCESSED);
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_LAST_ACCESSED + " supplemental data.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
	}
	
	/**
	 * Updates the last access time to now
	 */
	@SuppressWarnings("unchecked")
	public void setUpdateLastAccessTime(){
		JSONObject json = new JSONObject();
		json.put(PCG.JSON_CREATED, getCreationTime());
		json.put(PCG.JSON_LAST_ACCESSED, System.currentTimeMillis());
		json.put(PCG.JSON_GIVEN_NAME, getGivenName());
		getMasterEntry().putAttr(EventFlow_Instance_SupplementalData_Prefix + id, json.toJSONString());
	}
	
	/**
	 * Updates the last access time to now
	 */
	@SuppressWarnings("unchecked")
	public void setGivenName(String name){
		JSONObject json = new JSONObject();
		json.put(PCG.JSON_CREATED, getCreationTime());
		json.put(PCG.JSON_LAST_ACCESSED, getLastAccessTime());
		json.put(PCG.JSON_GIVEN_NAME, name);
		getMasterEntry().putAttr(EventFlow_Instance_SupplementalData_Prefix + id, json.toJSONString());
	}
	
	/**
	 * Returns the PCG's master entry node
	 * @return
	 */
	public Node getMasterEntry() {
		return masterEntry;
	}

	/**
	 * Returns the PCG's master exit node
	 * @return
	 */
	public Node getMasterExit() {
		return masterExit;
	}
	
	/**
	 * Returns the function containing the pcg events
	 * @return
	 */
	public Node getFunction(){
		return function;
	}
	
	/**
	 * Helper method to select the Atlas graph element given a serialized graph element address
	 * @param address
	 * @return
	 */
	private GraphElement getGraphElementByAddress(String address){
		int hexAddress = Integer.parseInt(address, 16);
		GraphElement ge = Graph.U.getAt(hexAddress);
		return ge;
	}
	
	/**
	 * Helper method to get the serialized PCG parameters for this pcg instance
	 * @return
	 */
	private JSONObject getSupplementalData(){
		try {
			JSONParser parser = new JSONParser();
			String jsonString = getMasterEntry().getAttr(EventFlow_Instance_SupplementalData_Prefix + id).toString();
			JSONObject json = (JSONObject) parser.parse(jsonString);
			return json;
		} catch (Exception e){
			throw new RuntimeException("Could not decode serialized PCG supplemental data.", e);
		}
	}
	
	/**
	 * Helper method to get the serialized PCG parameters for this pcg instance
	 * @return
	 */
	private JSONObject getParameters(){
		try {
			JSONParser parser = new JSONParser();
			String jsonString = getMasterEntry().getAttr(EventFlow_Instance_Parameters_Prefix + id).toString();
			JSONObject json = (JSONObject) parser.parse(jsonString);
			return json;
		} catch (Exception e){
			throw new RuntimeException("Could not decode serialized PCG parameters.", e);
		}
	}
	
}
