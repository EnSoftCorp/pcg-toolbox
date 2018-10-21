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
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitInterproceduralControlFlowGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.xcsg.XCSG_Extension;
import com.ensoftcorp.open.pcg.log.Log;

public class ICFGPCG {

	/**
	 * A attribute used to serialize the ICFGPCG nodes and edges that identify a
	 * unique ICFGPCG instance serialized in the Atlas graph.
	 */
	@XCSG_Extension
	public static final String ICFGPCGInstances = "ICFGPCGInstances";

	/**
	 * Defines tags and attributes for ICFGPCG nodes
	 */
	public static interface ICFGPCGNode {
		/**
		 * Tag applied to the newly created master entry node
		 */
		@XCSG_Extension
		public static final String ICFGPCGMasterEntry = "ICFGPCGMasterEntry";

		/**
		 * Tag applied to the newly create master exit node
		 */
		@XCSG_Extension
		public static final String ICFGPCGMasterExit = "ICFGPCGMasterExit";
	}

	/**
	 * Defines tags and attributes for ICFGPCG edges
	 */
	public static interface ICFGPCGEdge {
		/**
		 * Tag applied to CFG edges that are retained in the final ICFGPCG
		 */
		@XCSG_Extension
		public static final String ICFGPCGEdge = "InterproceduralPCGEdge";

		/**
		 * Tag applied to ICFGPCG edges that are back edges (similar to XCSG.ControlFlowBackEdge for PCGs)
		 */
		@XCSG_Extension
		public static final String ICFGPCGBackEdge = "InterproceduralPCGBackEdge";

		/**
		 * Tag applied to ICFGPCG Back eges that are reentrant
		 */
		@XCSG_Extension
		public static final String ICFGPCGReentryEdge = "InterproceduralPCGReentryEdge";
	}

	private Graph icfgpcg;
	private Graph icfg;
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
	 * ICFGPCG instances are equivalent if they have the same instance id (case-insensitive)
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((instanceID == null) ? 0 : instanceID.hashCode());
		return result;
	}

	/**
	 * ICFGPCG instances are equivalent if they have the same instance id (case-insensitive)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ICFGPCG other = (ICFGPCG) obj;
		if (instanceID == null) {
			if (other.instanceID != null)
				return false;
		} else if (!instanceID.equalsIgnoreCase(other.instanceID))
			return false;
		return true;
	}

	protected ICFGPCG(Graph icfgpcg, UniqueEntryExitInterproceduralControlFlowGraph uicfg, AtlasSet<Node> events){
		this.icfgpcg = icfgpcg;
		this.icfg = uicfg.getICFG();
		this.masterEntry = uicfg.getEntryNode();
		this.roots = uicfg.getRoots();
		this.masterExit = uicfg.getExitNode();
		this.exits = uicfg.getExits();
		this.events = events;
		this.instanceID = getICFGPCGInstanceID(uicfg, events);
		long time = System.currentTimeMillis();
		this.creationTime = time;
		this.lastAccessTime = time;
		this.givenName = "";
	}

	private ICFGPCG(Graph icfgpcg, Graph icfg, Node masterEntry, AtlasSet<Node> roots, Node masterExit, AtlasSet<Node> exits, AtlasSet<Node> events, String instanceID, long creationTime, long lastAccessTime, String givenName){
		this.icfgpcg = icfgpcg;
		this.icfg = icfg;
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
	 * Gets the time (unix time) that the this ICFGPCG was last accessed
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
	 * Gets the time (unix time) that the this ICFGPCG was created
	 * @return
	 */
	public long getCreationTime(){
		return creationTime;
	}

	/**
	 * Updates this ICFGPCG's given name
	 * @param givenName
	 */
	public void setGivenName(String givenName){
		this.givenName = givenName;
		delete(this);
		save(this);
	}

	/**
	 * Returns the name assigned to this ICFGPCG
	 * @return
	 */
	public String getGivenName(){
		return givenName;
	}

	/**
	 * Returns the functions that are captured in this ICFGPCG
	 * @return
	 */
	public Node getCapturedFunctions(){
		// TODO: Is this even required?
		return null;
	}

	/**
	 * Returns the entry point function of this ICFGPCG
	 * @return
	 */
	public Node getEntryPointFunction(){
		// TODO: Is this even required?
		return null;
	}

	/**
	 * Loads a serialized ICFGPCG instance from the Atlas graph
	 * or null if the ICFGPCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static ICFGPCG load(UniqueEntryExitInterproceduralControlFlowGraph ucfg, AtlasSet<Node> events){
		return load(ucfg.getEntryNode(), getICFGPCGInstanceID(ucfg, events));
	}

	/**
	 * Loads a serialized ICFGPCG instance from the Atlas graph
	 * or null if the ICFGPCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static ICFGPCG load(Node masterEntry, String instanceID){
		for(Object instance : ICFGPCG.getInstances(masterEntry)){
			JSONObject json = (JSONObject) instance;
			ICFGPCG icfgpcg = decodeICFGPCGInstance(json);
			if(instanceID.equalsIgnoreCase(icfgpcg.getInstanceID())){
				return icfgpcg;
			}
		}
		return null;
	}

	/**
	 * Loads a serialized ICFGPCG instance from the Atlas graph
	 * or null if the ICFGPCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static Set<ICFGPCG> load(Node masterEntry){
		Set<ICFGPCG> icfgpcgs = new HashSet<ICFGPCG>();
		for(Object instance : ICFGPCG.getInstances(masterEntry)){
			JSONObject json = (JSONObject) instance;
			icfgpcgs.add(decodeICFGPCGInstance(json));
		}
		return icfgpcgs;
	}

	/**
	 * Loads all PCGs stored in the Atlas graph
	 * @return
	 */
	public static Set<ICFGPCG> loadAll() {
		Set<ICFGPCG> icfgpcgs = new HashSet<ICFGPCG>();
		for(Node masterEntry : Common.universe().nodes(ICFGPCG.ICFGPCGNode.ICFGPCGMasterEntry).eval().nodes()){
			for(Object instance : ICFGPCG.getInstances(masterEntry)){
				JSONObject json = (JSONObject) instance;
				icfgpcgs.add(decodeICFGPCGInstance(json));
			}
		}
		return icfgpcgs;
	}

	@SuppressWarnings("unchecked")
	protected static void save(ICFGPCG instance){
		JSONArray instances = getInstances(instance.getMasterEntry());
		instances.add(getICFGPCGInstanceJSON(instance));
		instance.getMasterEntry().putAttr(ICFGPCG.ICFGPCGInstances, instances.toJSONString());
	}

	/**
	 * Deletes the ICFGPCG instance from the Atlas graph
	 * @param instanceID
	 */
	@SuppressWarnings("unchecked")
	public static void delete(ICFGPCG icfgpcg){
		JSONArray instances = getInstances(icfgpcg.getMasterEntry());
		JSONArray updatedInstances = new JSONArray();
		for(Object instance : instances){
			JSONObject json = (JSONObject) instance;
			if(icfgpcg.getInstanceID().equalsIgnoreCase(decodeICFGPCGInstance(json).getInstanceID())){
				continue;
			} else {
				updatedInstances.add(json);
			}
		}
		icfgpcg.getMasterEntry().putAttr(ICFGPCGInstances, updatedInstances.toJSONString());
	}

	/**
	 * Purges all records of PCGs from the Atlas graph
	 */
	public static void deleteAll(){
		for(Node masterEntry : new AtlasHashSet<Node>(Common.universe().nodes(ICFGPCG.ICFGPCGNode.ICFGPCGMasterEntry).eval().nodes())){
			masterEntry.attr().remove(ICFGPCGInstances);
		}
	}

	private static ICFGPCG decodeICFGPCGInstance(JSONObject icfgpcgInstance) {		
		// decode the icfgpcg nodes
		AtlasSet<Node> icfgpcgNodes = new AtlasHashSet<Node>();
		try {
			JSONArray icfgpcgNodeAddresses = (JSONArray) icfgpcgInstance.get(JSON_ICFGPCG_NODES);
			for(Object address : icfgpcgNodeAddresses){
				Node pcgNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				icfgpcgNodes.add(pcgNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCG " + JSON_ICFGPCG_NODES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the icfgpcg edges
		AtlasSet<Edge> pcgEdges = new AtlasHashSet<Edge>();
		try {
			JSONArray pcgEdgeAddresses = (JSONArray) icfgpcgInstance.get(JSON_ICFGPCG_EDGES);
			for(Object address : pcgEdgeAddresses){
				Edge pcgEdge = (Edge) CommonQueries.getGraphElementByAddress(address.toString());
				pcgEdges.add(pcgEdge);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCG " + JSON_ICFGPCG_EDGES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		Graph icfgpcg = new UncheckedGraph(icfgpcgNodes, pcgEdges);

		// decode the icfg nodes
		AtlasSet<Node> icfgNodes = new AtlasHashSet<Node>();
		try {
			JSONArray icfgNodeAddresses = (JSONArray) icfgpcgInstance.get(JSON_ICFG_NODES);
			for(Object address : icfgNodeAddresses){
				Node icfgNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				icfgNodes.add(icfgNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_ICFG_NODES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the icfg edges
		AtlasSet<Edge> icfgEdges = new AtlasHashSet<Edge>();
		try {
			JSONArray cfgEdgeAddresses = (JSONArray) icfgpcgInstance.get(JSON_ICFG_EDGES);
			for(Object address : cfgEdgeAddresses){
				Edge icfgEdge = (Edge) CommonQueries.getGraphElementByAddress(address.toString());
				icfgEdges.add(icfgEdge);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_ICFG_EDGES + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		Graph icfg = new UncheckedGraph(icfgNodes, icfgEdges);

		// decode the master entry
		Node masterEntry;
		try {
			masterEntry = CommonQueries.getNodeByAddress(icfgpcgInstance.get(ICFGPCG.JSON_MASTER_ENTRY).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_MASTER_ENTRY + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the roots nodes
		AtlasSet<Node> roots = new AtlasHashSet<Node>();
		try {
			JSONArray rootsNodeAddresses = (JSONArray) icfgpcgInstance.get(ICFGPCG.JSON_ROOTS);
			for(Object address : rootsNodeAddresses){
				Node rootsNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				roots.add(rootsNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_ROOTS + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the master exit
		Node masterExit;
		try {
			masterExit = CommonQueries.getNodeByAddress(icfgpcgInstance.get(ICFGPCG.JSON_MASTER_EXIT).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_MASTER_EXIT + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the exits nodes
		AtlasSet<Node> exits = new AtlasHashSet<Node>();
		try {
			JSONArray exitsNodeAddresses = (JSONArray) icfgpcgInstance.get(ICFGPCG.JSON_EXITS);
			for(Object address : exitsNodeAddresses){
				Node exitsNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				exits.add(exitsNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_EXITS + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		// decode the events nodes
		AtlasSet<Node> events = new AtlasHashSet<Node>();
		try {
			JSONArray eventsNodeAddresses = (JSONArray) icfgpcgInstance.get(ICFGPCG.JSON_EVENTS);
			for(Object address : eventsNodeAddresses){
				Node eventsNode = (Node) CommonQueries.getGraphElementByAddress(address.toString());
				events.add(eventsNode);
			}
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCFG " + JSON_EVENTS + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		String instanceID;
		try {
			instanceID = icfgpcgInstance.get(ICFGPCG.JSON_ICFGPCG_INSTANCE_ID).toString();
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCG " + JSON_ICFGPCG_INSTANCE_ID + " values.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		long creationTime = -1L;
		try {
			creationTime = Long.parseLong(icfgpcgInstance.get(ICFGPCG.JSON_CREATION_TIME).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCG " + JSON_CREATION_TIME + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		long lastAccessTime = -1L;
		try {
			lastAccessTime = Long.parseLong(icfgpcgInstance.get(ICFGPCG.JSON_LAST_ACCESS_TIME).toString());
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCG " + JSON_LAST_ACCESS_TIME + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		String givenName;
		try {
			givenName = icfgpcgInstance.get(ICFGPCG.JSON_GIVEN_NAME).toString();
		} catch (Throwable t){
			String message = "Could not decode serialized ICFGPCG " + JSON_GIVEN_NAME + " value.";
			RuntimeException e = new RuntimeException(message, t);
			throw e;
		}

		return new ICFGPCG(icfgpcg, icfg, masterEntry, roots, masterExit, exits, events, instanceID, creationTime, lastAccessTime, givenName);
	}

	/**
	 * Returns the instance ID of this ICFGPCG which can be used to load the ICFGPCG
	 * later
	 * 
	 * @return
	 */
	public String getInstanceID(){
		return instanceID;
	}

	/**
	 * Returns the interprocedural projected control graph
	 * @return
	 */
	public Q getICFGPCG(){
//		DisplayUtil.displayGraph(Common.toQ(icfgpcg).eval());
		return Common.toQ(icfgpcg);
	}

	/**
	 * Returns the original interprocedural control flow graph
	 * @return
	 */
	public Q getICFG(){
		return Common.toQ(icfg);
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

	// Begin ICFGPCG serialization logic

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
	 * ICFGPCG, which can be used to identify a unique PCG instance
	 * 
	 * @param icfgpcg
	 * @return
	 */
	public static String getICFGPCGInstanceID(Graph icfg, AtlasSet<Node> roots, AtlasSet<Node> exits, AtlasSet<Node> events){
		StringBuilder instance = new StringBuilder();
		instance.append(getSortedAddressList(icfg.nodes()).toString());
		instance.append(getSortedAddressList(icfg.edges()).toString());
		instance.append(getSortedAddressList(roots).toString());
		instance.append(getSortedAddressList(exits).toString());
		instance.append(getSortedAddressList(events).toString());
		return md5(instance.toString());
	}

	/**
	 * Returns a hash of the sorted node/edge addresses used to construct the
	 * PCG, which can be used to identify a unique PCG instance.
	 * 
	 * Equivalent to getICFGPCGInstanceID(uicfg.getICFG(), uicfg.getRoots(), uicfg.getExits(), events)
	 * 
	 * @param pcg
	 * @return
	 */
	public static String getICFGPCGInstanceID(UniqueEntryExitInterproceduralControlFlowGraph uicfg, AtlasSet<Node> events){
		return getICFGPCGInstanceID(uicfg.getICFG(), uicfg.getRoots(), uicfg.getExits(), events);
	}

	// constants for serializing pcg parameters
	private static final String JSON_ICFGPCG_INSTANCE_ID = "instance";
	private static final String JSON_ICFGPCG_NODES = "icfgpcg-nodes";
	private static final String JSON_ICFGPCG_EDGES = "icfgpcg-edges";
	private static final String JSON_ICFG_NODES = "icfg-nodes";
	private static final String JSON_ICFG_EDGES = "icfg-edges";
	private static final String JSON_MASTER_ENTRY = "master-entry";
	private static final String JSON_ROOTS = "roots";
	private static final String JSON_MASTER_EXIT = "master-exit";
	private static final String JSON_EXITS = "exits";
	private static final String JSON_EVENTS = "events";
	private static final String JSON_CREATION_TIME = "creation";
	private static final String JSON_LAST_ACCESS_TIME = "last-access";
	private static final String JSON_GIVEN_NAME = "name";

	@SuppressWarnings("unchecked")
	private static JSONObject getICFGPCGInstanceJSON(ICFGPCG instance){
		JSONArray icfgpcgNodeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.icfgpcg.nodes())){
			icfgpcgNodeAddresses.add(address);
		}

		JSONArray icfgpcgEdgeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.icfgpcg.edges())){
			icfgpcgEdgeAddresses.add(address);
		}

		JSONArray icfgNodeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.icfg.nodes())){
			icfgNodeAddresses.add(address);
		}

		JSONArray cfgEdgeAddresses = new JSONArray();
		for(String address : getSortedAddressList(instance.icfg.edges())){
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
		json.put(ICFGPCG.JSON_ICFGPCG_NODES, icfgpcgNodeAddresses);
		json.put(ICFGPCG.JSON_ICFGPCG_EDGES, icfgpcgEdgeAddresses);
		json.put(ICFGPCG.JSON_ICFG_NODES, icfgNodeAddresses);
		json.put(ICFGPCG.JSON_ICFG_EDGES, cfgEdgeAddresses);
		json.put(ICFGPCG.JSON_MASTER_ENTRY, instance.getMasterEntry().address().toAddressString());
		json.put(ICFGPCG.JSON_ROOTS, rootAddresses);
		json.put(ICFGPCG.JSON_MASTER_EXIT, instance.getMasterExit().address().toAddressString());
		json.put(ICFGPCG.JSON_EXITS, exitAddresses);
		json.put(ICFGPCG.JSON_EVENTS, eventAddresses);
		json.put(ICFGPCG.JSON_ICFGPCG_INSTANCE_ID, instance.instanceID);
		json.put(ICFGPCG.JSON_CREATION_TIME, instance.creationTime);
		json.put(ICFGPCG.JSON_LAST_ACCESS_TIME, instance.lastAccessTime);
		json.put(ICFGPCG.JSON_GIVEN_NAME, instance.givenName);

		return json;
	}

	// helper method to return the ICFGPCG json object for each instance stored on the master entry node
	private static JSONArray getInstances(Node masterEntry){
		if(masterEntry.hasAttr(ICFGPCG.ICFGPCGInstances)){
			String json = masterEntry.getAttr(ICFGPCG.ICFGPCGInstances).toString();
			JSONParser parser = new JSONParser();
			try {
				return (JSONArray) parser.parse(json);
			} catch (ParseException e) {
				Log.error("Could not load ICFGPCG instances", e);
				return new JSONArray();
			}
		} else {
			return new JSONArray();
		}
	}
}