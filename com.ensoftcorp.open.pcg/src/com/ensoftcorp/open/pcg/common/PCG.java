package com.ensoftcorp.open.pcg.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitControlFlowGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.xcsg.XCSG_Extension;
import com.ensoftcorp.open.pcg.log.Log;

/**
 * A wrapper class for deserializing and accessing the properties of 
 * Project Control Graph computations.
 * 
 * @author Ben Holland
 */
public class PCG {

	/**
	 * A attribute used to serialize the PCG nodes and edges that identify a
	 * unique PCG instance serialized in the Atlas graph.
	 */
	@XCSG_Extension
	public static final String PCGInstances = "PCGInstances";
	
	/**
	 * Defines tags and attributes for PCG nodes
	 */
	public static interface PCGNode {
		/**
		 * Tag applied to the newly created master entry node
		 */
		@XCSG_Extension
		public static final String PCGMasterEntry = "PCGMasterEntry";
		
		/**
		 * Tag applied to the newly create master exit node
		 */
		@XCSG_Extension
		public static final String PCGMasterExit = "PCGMasterExit";
	}
	
	/**
	 * Defines tags and attributes for PCG edges
	 */
	public static interface PCGEdge {
		/**
		 * Tag applied to CFG edges that are retained in the final PCG
		 */
		@XCSG_Extension
		public static final String PCGEdge = "PCGEdge";
		
		/**
		 * Tag applied to PCG edges that are back edges (similar to XCSG.ControlFlowBackEdge for PCGs)
		 */
		@XCSG_Extension
		public static final String PCGBackEdge = "PCGBackEdge";
		
		/**
		 * Tag applied to PCG Back eges that are reentrant
		 */
		@XCSG_Extension
		public static final String PCGReentryEdge = "PCGReentryEdge";
	}
	
	private Graph pcg;
	private Graph cfg;
	private Node masterEntry;
	private Node masterExit;
	private AtlasSet<Node> roots;
	private AtlasSet<Node> exits;
	private AtlasSet<Node> events;
	private String instanceID;
	private long creationTime;
	private long lastAccessTime;
	private String givenName;
	
	/**
	 * PCG instances are equivalent if they have the same instance id (case-insensitive)
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((instanceID == null) ? 0 : instanceID.hashCode());
		return result;
	}

	/**
	 * PCG instances are equivalent if they have the same instance id (case-insensitive)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PCG other = (PCG) obj;
		if (instanceID == null) {
			if (other.instanceID != null)
				return false;
		} else if (!instanceID.equalsIgnoreCase(other.instanceID))
			return false;
		return true;
	}

	protected PCG(Graph pcg, UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events){
		this.pcg = pcg;
		this.cfg = ucfg.getCFG();
		this.masterEntry = ucfg.getEntryNode();
		this.roots = ucfg.getRoots();
		this.masterExit = ucfg.getExitNode();
		this.exits = ucfg.getExits();
		this.events = events;
		this.instanceID = getPCGInstanceID(ucfg, events);
		long time = System.currentTimeMillis();
		this.creationTime = time;
		this.lastAccessTime = time;
		this.givenName = "";
	}
	
	private PCG(Graph pcg, Graph cfg, Node masterEntry, AtlasSet<Node> roots, Node masterExit, AtlasSet<Node> exits, AtlasSet<Node> events, String instanceID, long creationTime, long lastAccessTime, String givenName){
		this.pcg = pcg;
		this.cfg = cfg;
		this.masterEntry = masterEntry;
		this.roots = roots;
		this.masterExit = masterExit;
		this.exits = exits;
		this.events = events;
		this.instanceID = instanceID;
		this.creationTime = creationTime;
		this.lastAccessTime = lastAccessTime;
		this.givenName = givenName;
	}
	
	/**
	 * Gets the time (unix time) that the this PCG was last accessed
	 * @return
	 */
	public long getLastAccessTime(){
		return lastAccessTime;
	}
	
	/**
	 * Updates the last access time to the current time
	 */
	public void updateLastAccessTime(){
		this.lastAccessTime = System.currentTimeMillis();
		delete(this);
		save(this);
	}
	
	/**
	 * Gets the time (unix time) that the this PCG was created
	 * @return
	 */
	public long getCreationTime(){
		return creationTime;
	}
	
	/**
	 * Updates this PCG's given name
	 * @param givenName
	 */
	public void setGivenName(String givenName){
		this.givenName = givenName;
		delete(this);
		save(this);
	}
	
	/**
	 * Returns the name assigned to this PCG
	 * @return
	 */
	public String getGivenName(){
		return givenName;
	}
	
	/**
	 * Returns the function that contains this PCG
	 * @return
	 */
	public Node getFunction(){
		// assertion: the factory class has already asserted that the cfg has to
		// be contained within a single function and is non-empty
		return CommonQueries.getContainingFunctions(Common.toQ(cfg)).eval().nodes().one();
	}
	
	/**
	 * Loads a serialized PCG instance from the Atlas graph
	 * or null if the PCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static PCG load(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events){
		return load(ucfg.getEntryNode(), getPCGInstanceID(ucfg, events));
	}
	
	/**
	 * Loads a serialized PCG instance from the Atlas graph
	 * or null if the PCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static PCG load(Node masterEntry, String instanceID){
		for(Object instance : PCG.getInstances(masterEntry)){
			JSONObject json = (JSONObject) instance;
			PCG pcg = decodePCGInstance(json);
			if(instanceID.equalsIgnoreCase(pcg.getInstanceID())){
				return pcg;
			}
		}
		return null;
	}
	
	/**
	 * Loads a serialized PCG instance from the Atlas graph
	 * or null if the PCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static Set<PCG> load(Node masterEntry){
		Set<PCG> pcgs = new HashSet<PCG>();
		for(Object instance : PCG.getInstances(masterEntry)){
			JSONObject json = (JSONObject) instance;
			pcgs.add(decodePCGInstance(json));
		}
		return pcgs;
	}
	
	/**
	 * Loads all PCGs stored in the Atlas graph
	 * @return
	 */
	public static Set<PCG> loadAll() {
		Set<PCG> pcgs = new HashSet<PCG>();
		for(Node masterEntry : Query.universe().nodes(PCG.PCGNode.PCGMasterEntry).eval().nodes()){
			for(Object instance : PCG.getInstances(masterEntry)){
				JSONObject json = (JSONObject) instance;
				pcgs.add(decodePCGInstance(json));
			}
		}
		return pcgs;
	}
	
	@SuppressWarnings("unchecked")
	protected static void save(PCG instance){
		JSONArray instances = getInstances(instance.getMasterEntry());
		instances.add(getPCGInstanceJSON(instance));
		instance.getMasterEntry().putAttr(PCG.PCGInstances, instances.toJSONString());
	}
	
	/**
	 * Deletes the PCG instance from the Atlas graph
	 * @param instanceID
	 */
	@SuppressWarnings("unchecked")
	public static void delete(PCG pcg){
		JSONArray instances = getInstances(pcg.getMasterEntry());
		JSONArray updatedInstances = new JSONArray();
		for(Object instance : instances){
			JSONObject json = (JSONObject) instance;
			if(pcg.getInstanceID().equalsIgnoreCase(decodePCGInstance(json).getInstanceID())){
				continue;
			} else {
				updatedInstances.add(json);
			}
		}
		pcg.getMasterEntry().putAttr(PCGInstances, updatedInstances.toJSONString());
	}
	
	/**
	 * Purges all records of PCGs from the Atlas graph
	 */
	public static void deleteAll(){
		for(Node masterEntry : new AtlasHashSet<Node>(Query.universe().nodes(PCG.PCGNode.PCGMasterEntry).eval().nodes())){
			masterEntry.attr().remove(PCGInstances);
		}
	}

	private static PCG decodePCGInstance(JSONObject pcgInstance) {		
		// decode the pcg nodes
		AtlasSet<Node> pcgNodes = new AtlasHashSet<Node>();
		try {
			JSONArray pcgNodeAddresses = (JSONArray) pcgInstance.get(JSON_PCG_NODES);
			for(Object address : pcgNodeAddresses){
				Node pcgNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				pcgNodes.add(pcgNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_PCG_NODES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the pcg edges
		AtlasSet<Edge> pcgEdges = new AtlasHashSet<Edge>();
		try {
			JSONArray pcgEdgeAddresses = (JSONArray) pcgInstance.get(JSON_PCG_EDGES);
			for(Object address : pcgEdgeAddresses){
				Edge pcgEdge = (Edge) CommonQueries.getGraphElementByAddress(address.toString());
				pcgEdges.add(pcgEdge);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_PCG_EDGES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		Graph pcg = new UncheckedGraph(pcgNodes, pcgEdges);
		
		// decode the cfg nodes
		AtlasSet<Node> cfgNodes = new AtlasHashSet<Node>();
		try {
			JSONArray cfgNodeAddresses = (JSONArray) pcgInstance.get(JSON_CFG_NODES);
			for(Object address : cfgNodeAddresses){
				Node cfgNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				cfgNodes.add(cfgNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_CFG_NODES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		// decode the cfg edges
		AtlasSet<Edge> cfgEdges = new AtlasHashSet<Edge>();
		try {
			JSONArray cfgEdgeAddresses = (JSONArray) pcgInstance.get(JSON_CFG_EDGES);
			for(Object address : cfgEdgeAddresses){
				Edge cfgEdge = (Edge) CommonQueries.getGraphElementByAddress(address.toString());
				cfgEdges.add(cfgEdge);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_CFG_EDGES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		Graph cfg = new UncheckedGraph(cfgNodes, cfgEdges);
		
		// decode the master entry
		Node masterEntry;
		try {
			masterEntry = CommonQueries.getNodeByAddress(pcgInstance.get(PCG.JSON_MASTER_ENTRY).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_MASTER_ENTRY + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		// decode the roots nodes
		AtlasSet<Node> roots = new AtlasHashSet<Node>();
		try {
			JSONArray rootsNodeAddresses = (JSONArray) pcgInstance.get(PCG.JSON_ROOTS);
			for(Object address : rootsNodeAddresses){
				Node rootsNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				roots.add(rootsNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_ROOTS + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		// decode the master exit
		Node masterExit;
		try {
			masterExit = CommonQueries.getNodeByAddress(pcgInstance.get(PCG.JSON_MASTER_EXIT).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_MASTER_EXIT + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		// decode the exits nodes
		AtlasSet<Node> exits = new AtlasHashSet<Node>();
		try {
			JSONArray exitsNodeAddresses = (JSONArray) pcgInstance.get(PCG.JSON_EXITS);
			for(Object address : exitsNodeAddresses){
				Node exitsNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				exits.add(exitsNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_EXITS + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		// decode the events nodes
		AtlasSet<Node> events = new AtlasHashSet<Node>();
		try {
			JSONArray eventsNodeAddresses = (JSONArray) pcgInstance.get(PCG.JSON_EVENTS);
			for(Object address : eventsNodeAddresses){
				Node eventsNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				events.add(eventsNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_EVENTS + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		String instanceID;
		try {
			instanceID = pcgInstance.get(PCG.JSON_PCG_INSTANCE_ID).toString();
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_PCG_INSTANCE_ID + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		long creationTime = -1L;
		try {
			creationTime = Long.parseLong(pcgInstance.get(PCG.JSON_CREATION_TIME).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_CREATION_TIME + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		long lastAccessTime = -1L;
		try {
			lastAccessTime = Long.parseLong(pcgInstance.get(PCG.JSON_LAST_ACCESS_TIME).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_LAST_ACCESS_TIME + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		String givenName;
		try {
			givenName = pcgInstance.get(PCG.JSON_GIVEN_NAME).toString();
		} catch (Throwable t){
			String message = "Could not decode serialized PCG " + JSON_GIVEN_NAME + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}
		
		return new PCG(pcg, cfg, masterEntry, roots, masterExit, exits, events, instanceID, creationTime, lastAccessTime, givenName);
	}

	/**
	 * Returns the instance ID of this PCG which can be used to load the PCG
	 * later
	 * 
	 * @return
	 */
	public String getInstanceID(){
		return instanceID;
	}
	
	/**
	 * Returns the project control graph
	 * @return
	 */
	public Q getPCG(){
		return Common.toQ(pcg);
	}
	
	/**
	 * Returns the original control flow graph
	 * @return
	 */
	public Q getCFG(){
		return Common.toQ(cfg);
	}
	
	/**
	 * Returns the master entry of the PCG and CFG
	 * @return
	 */
	public Node getMasterEntry(){
		return masterEntry;
	}
	
	/**
	 * Returns the roots of the control flow graph specified to create the PCG
	 * 
	 * @return
	 */
	public Q getRoots(){
		return Common.toQ(roots);
	}
	
	/**
	 * Returns the exits of the control flow graph specified to create the PCG
	 * 
	 * @return
	 */
	public Q getExits(){
		return Common.toQ(exits);
	}
	
	/**
	 * Returns the master exit of the PCG and CFG
	 * @return
	 */
	public Node getMasterExit(){
		return masterExit;
	}
	
	/**
	 * Returns the set of events used to contruct the PCG
	 * @return
	 */
	public Q getEvents(){
		return Common.toQ(events);
	}
	
	// BEGIN PCG SERIALIZATION LOGIC
	
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
	 * Returns a hash of the sorted node/edge addresses used to construct the
	 * PCG, which can be used to identify a unique PCG instance
	 * 
	 * @param pcg
	 * @return
	 */
	public static String getPCGInstanceID(Graph cfg, AtlasSet<Node> roots, AtlasSet<Node> exits, AtlasSet<Node> events){
		StringBuilder instance = new StringBuilder();
		instance.append(getSortedAddressList(cfg.nodes()).toString());
		instance.append(getSortedAddressList(cfg.edges()).toString());
		instance.append(getSortedAddressList(roots).toString());
		instance.append(getSortedAddressList(exits).toString());
		instance.append(getSortedAddressList(events).toString());
		return md5(instance.toString());
	}
	
	/**
	 * Returns a hash of the sorted node/edge addresses used to construct the
	 * PCG, which can be used to identify a unique PCG instance.
	 * 
	 * Equivalent to getPCGInstanceID(ucfg.getCFG(), ucfg.getRoots(), ucfg.getExits(), events)
	 * 
	 * @param pcg
	 * @return
	 */
	public static String getPCGInstanceID(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events){
		return getPCGInstanceID(ucfg.getCFG(), ucfg.getRoots(), ucfg.getExits(), events);
	}
	
	// constants for serializing pcg parameters
	private static final String JSON_PCG_INSTANCE_ID = "instance";
	private static final String JSON_PCG_NODES = "pcg-nodes";
	private static final String JSON_PCG_EDGES = "pcg-edges";
	private static final String JSON_CFG_NODES = "cfg-nodes";
	private static final String JSON_CFG_EDGES = "cfg-edges";
	private static final String JSON_MASTER_ENTRY = "master-entry";
	private static final String JSON_ROOTS = "roots";
	private static final String JSON_MASTER_EXIT = "master-exit";
	private static final String JSON_EXITS = "exits";
	private static final String JSON_EVENTS = "events";
	private static final String JSON_CREATION_TIME = "creation";
	private static final String JSON_LAST_ACCESS_TIME = "last-access";
	private static final String JSON_GIVEN_NAME = "name";
	
	@SuppressWarnings("unchecked")
	private static JSONObject getPCGInstanceJSON(PCG instance){
		JSONArray pcgNodeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.pcg.nodes())){
			pcgNodeAddresses.add(address);
		}
		
		JSONArray pcgEdgeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.pcg.edges())){
			pcgEdgeAddresses.add(address);
		}
		
		JSONArray cfgNodeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.cfg.nodes())){
			cfgNodeAddresses.add(address);
		}
		
		JSONArray cfgEdgeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.cfg.edges())){
			cfgEdgeAddresses.add(address);
		}
		
		JSONArray rootAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.roots)){
			rootAddresses.add(address);
		}
		
		JSONArray exitAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.exits)){
			exitAddresses.add(address);
		}
		
		JSONArray eventAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.events)){
			eventAddresses.add(address);
		}
		
		JSONObject json = new JSONObject();
		json.put(PCG.JSON_PCG_NODES, pcgNodeAddresses);
		json.put(PCG.JSON_PCG_EDGES, pcgEdgeAddresses);
		json.put(PCG.JSON_CFG_NODES, cfgNodeAddresses);
		json.put(PCG.JSON_CFG_EDGES, cfgEdgeAddresses);
		json.put(PCG.JSON_MASTER_ENTRY, instance.getMasterEntry().address().toAddressString());
		json.put(PCG.JSON_ROOTS, rootAddresses);
		json.put(PCG.JSON_MASTER_EXIT, instance.getMasterExit().address().toAddressString());
		json.put(PCG.JSON_EXITS, exitAddresses);
		json.put(PCG.JSON_EVENTS, eventAddresses);
		json.put(PCG.JSON_PCG_INSTANCE_ID, instance.instanceID);
		json.put(PCG.JSON_CREATION_TIME, instance.creationTime);
		json.put(PCG.JSON_LAST_ACCESS_TIME, instance.lastAccessTime);
		json.put(PCG.JSON_GIVEN_NAME, instance.givenName);
		
		return json;
	}
	
	// helper method to return the PCG json object for each instance stored on the master entry node
	private static JSONArray getInstances(Node masterEntry){
		if(masterEntry.hasAttr(PCG.PCGInstances)){
			String json = masterEntry.getAttr(PCG.PCGInstances).toString();
			JSONParser parser = new JSONParser();
			try {
				return (JSONArray) parser.parse(json);
			} catch (ParseException e) {
				Log.error("Could not load PCG instances", e);
				return new JSONArray();
			}
		} else {
			return new JSONArray();
		}
	}
	
}
