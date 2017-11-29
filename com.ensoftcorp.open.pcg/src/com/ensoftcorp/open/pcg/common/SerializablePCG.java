package com.ensoftcorp.open.pcg.common;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitControlFlowGraph;
import com.ensoftcorp.open.commons.utilities.address.NormalizedAddress;
import com.ensoftcorp.open.commons.xcsg.XCSG_Extension;
import com.ensoftcorp.open.pcg.log.Log;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SerializablePCG extends PCG {

	/**
	 * A attribute used to serialize the PCG nodes and edges that identify a
	 * unique PCG instance serialized in the Atlas graph.
	 */
	@XCSG_Extension
	public static final String PCG_INSTANCE_ATTRIBUTE_PREFIX = "PCG_Instance_";
	
	private static class NormalizedGraphElementAddress {
		protected String address;
		
		@Override
		public String toString() {
			return address;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((address == null) ? 0 : address.hashCode());
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
			NormalizedGraphElementAddress other = (NormalizedGraphElementAddress) obj;
			if (address == null) {
				if (other.address != null)
					return false;
			} else if (!address.equals(other.address))
				return false;
			return true;
		}
	}
	
	public static class NormalizationException extends Exception {
		public NormalizationException(String message){
			super(message);
		}
	}
	
	private static class NormalizedNodeAddress extends NormalizedGraphElementAddress {
		public NormalizedNodeAddress(Node node) throws NormalizationException {
			Object addressAttr = node.getAttr(NormalizedAddress.NORMALIZED_ADDRESS_ATTRIBUTE);
			if(addressAttr != null){
				this.address = addressAttr.toString();
			} else {
				throw new NormalizationException("Node is missing " + NormalizedAddress.NORMALIZED_ADDRESS_ATTRIBUTE + " attribute.");
			}
		}

		public Node getNode(){
			return Common.universe().selectNode(NormalizedAddress.NORMALIZED_ADDRESS_ATTRIBUTE, address).eval().nodes().one();
		}
	}
	
	private static class NormalizedEdgeAddress extends NormalizedGraphElementAddress {
		public NormalizedEdgeAddress(Edge edge) throws NormalizationException {
			Object addressAttr = edge.getAttr(NormalizedAddress.NORMALIZED_ADDRESS_ATTRIBUTE);
			if(addressAttr != null){
				this.address = addressAttr.toString();
			} else {
				throw new NormalizationException("Edge is missing " + NormalizedAddress.NORMALIZED_ADDRESS_ATTRIBUTE + " attribute.");
			}
		}

		public Node getEdge(){
			return Common.universe().selectEdge(NormalizedAddress.NORMALIZED_ADDRESS_ATTRIBUTE, address).eval().nodes().one();
		}
	}
	
	private static class NormalizedNodeSet {
		private Set<NormalizedNodeAddress> nodeAddresses;
		
		public NormalizedNodeSet(AtlasSet<Node> nodes) throws NormalizationException{
			this.nodeAddresses = new HashSet<NormalizedNodeAddress>();
			for(Node node : nodes){
				this.nodeAddresses.add(new NormalizedNodeAddress(node));
			}
		}
		
		public Set<String> getAddressStrings(){
			Set<String> result = new HashSet<String>();
			for(NormalizedNodeAddress address : nodeAddresses){
				result.add(address.toString());
			}
			return result;
		}
		
		public AtlasSet<Node> getNodes(){
			AtlasSet<Node> result = new AtlasHashSet<Node>();
			for(NormalizedNodeAddress nodeAddress : nodeAddresses){
				result.add(nodeAddress.getNode());
			}
			return result;
		}
	}
	
	private static class NormalizedEdgeSet {
		private Set<NormalizedEdgeAddress> edgeAddresses;
		
		public NormalizedEdgeSet(AtlasSet<Edge> edges) throws NormalizationException{
			this.edgeAddresses = new HashSet<NormalizedEdgeAddress>();
			for(Edge edge : edges){
				this.edgeAddresses.add(new NormalizedEdgeAddress(edge));
			}
		}
		
		public Set<String> getAddressStrings(){
			Set<String> result = new HashSet<String>();
			for(NormalizedEdgeAddress address : edgeAddresses){
				result.add(address.toString());
			}
			return result;
		}
		
		public AtlasSet<Edge> getEdges(){
			AtlasSet<Edge> result = new AtlasHashSet<Edge>();
			for(NormalizedEdgeAddress edgeAddress : edgeAddresses){
				result.add(edgeAddress.getEdge());
			}
			return result;
		}
	}
	
	private static class NormalizedGraph {
		private NormalizedNodeSet nodeAddresses;
		private NormalizedEdgeSet edgeAddresses;

		public NormalizedGraph(Graph graph) throws NormalizationException{
			this.nodeAddresses = new NormalizedNodeSet(graph.nodes());
			this.edgeAddresses = new NormalizedEdgeSet(graph.edges());
		}
		
		public Graph getGraph(){
			return new UncheckedGraph(nodeAddresses.getNodes(), edgeAddresses.getEdges());
		}

		public NormalizedNodeSet getNodeAddresses() {
			return nodeAddresses;
		}

		public NormalizedEdgeSet getEdgeAddresses() {
			return edgeAddresses;
		}
	}
	
	private static class PCGData {
		private NormalizedGraph pcg;
		private NormalizedGraph cfg;
		private NormalizedNodeAddress masterEntry;
		private NormalizedNodeAddress masterExit;
		private NormalizedNodeSet roots;
		private NormalizedNodeSet exits;
		private NormalizedNodeSet events;
		private String instanceID;
		private long creationTime;
		private long lastAccessTime;
		private String name;
	}
	
	private PCGData data;
	
	protected SerializablePCG(Graph pcg, UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events) throws NormalizationException {
		this(pcg, ucfg.getCFG(), ucfg.getEntryNode(), ucfg.getRoots(), ucfg.getExitNode(), ucfg.getExits(), events);
	}
	
	private SerializablePCG(Graph pcg, Graph cfg, Node masterEntry, AtlasSet<Node> roots, Node masterExit, AtlasSet<Node> exits, AtlasSet<Node> events) throws NormalizationException {
		this.data.pcg = new NormalizedGraph(pcg);
		this.data.cfg = new NormalizedGraph(cfg);
		this.data.masterEntry = new NormalizedNodeAddress(masterEntry);
		this.data.roots = new NormalizedNodeSet(roots);
		this.data.masterExit = new NormalizedNodeAddress(masterExit);
		this.data.exits = new NormalizedNodeSet(exits);
		this.data.events = new NormalizedNodeSet(events);
		this.data.instanceID = getPCGInstanceID(this.data.cfg, this.data.roots, this.data.exits, this.data.events);
		long time = System.currentTimeMillis();
		this.data.creationTime = time;
		this.data.lastAccessTime = time;
		this.data.name = "";
	}
	
	private SerializablePCG(PCGData data) {
		this.data = data;
	}

	/**
	 * Returns a hash of the sorted node/edge addresses used to construct the
	 * PCG, which can be used to identify a unique PCG instance
	 * 
	 * @param pcg
	 * @return
	 */
	public static String getPCGInstanceID(NormalizedGraph cfg, NormalizedNodeSet roots, NormalizedNodeSet exits, NormalizedNodeSet events){
		StringBuilder instance = new StringBuilder();
		instance.append(getSortedAddressList(cfg.getNodeAddresses().getAddressStrings()).toString());
		instance.append(getSortedAddressList(cfg.getEdgeAddresses().getAddressStrings()).toString());
		instance.append(getSortedAddressList(roots.getAddressStrings()).toString());
		instance.append(getSortedAddressList(exits.getAddressStrings()).toString());
		instance.append(getSortedAddressList(events.getAddressStrings()).toString());
		return md5(instance.toString());
	}
	
	/**
	 * Returns an alphabetically sorted list of graph element addresses
	 * @param graphElements
	 * @return
	 */
	private static List<String> getSortedAddressList(Set<String> addresses){
		ArrayList<String> sortedAddresses = new ArrayList<String>(addresses);
		Collections.sort(sortedAddresses);
		return sortedAddresses;
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
	 * Loads a serialized PCG instance from the Atlas graph
	 * or null if the PCG instance does not exist
	 * @param instanceID
	 * @return
	 * @throws IOException  
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @throws NormalizationException 
	 */
	public static SerializablePCG load(UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events) throws JsonParseException, JsonMappingException, IOException, NormalizationException {
		return load(ucfg.getEntryNode(), getPCGInstanceID(new NormalizedGraph(ucfg.getCFG()), new NormalizedNodeSet(ucfg.getRoots()), new NormalizedNodeSet(ucfg.getExits()), new NormalizedNodeSet(events)));
	}

	/**
	 * Loads a serialized PCG instance from the Atlas graph
	 * or null if the PCG instance does not exist
	 * @param instanceID
	 * @return
	 * @throws IOException  
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public static SerializablePCG load(Node masterEntry, String instanceID) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper jsonMapper = new ObjectMapper();
		Object instance = masterEntry.getAttr(PCG_INSTANCE_ATTRIBUTE_PREFIX + instanceID);
		if(instance != null){
			String jsonString = instance.toString();
			PCGData data = jsonMapper.readValue(jsonString, SerializablePCG.PCGData.class);
			return new SerializablePCG(data);
		} else {
			return null;
		}
	}
	
	/**
	 * Loads a serialized PCG instance from the Atlas graph
	 * or null if the PCG instance does not exist
	 * @param instanceID
	 * @return
	 */
	public static Set<SerializablePCG> load(Node masterEntry) throws JsonParseException, JsonMappingException, IOException {
		Set<SerializablePCG> instances = new HashSet<SerializablePCG>();
		Set<String> attrs = new HashSet<String>();
		for(String attr : masterEntry.attr().keys()){
			attrs.add(attr);
		}
		for(String attr : attrs){
			if(attr.startsWith(PCG_INSTANCE_ATTRIBUTE_PREFIX)){
				instances.add(SerializablePCG.load(masterEntry, attr.substring(PCG_INSTANCE_ATTRIBUTE_PREFIX.length())));
			}
		}
		return instances;
	}
	
	/**
	 * Loads all PCGs stored in the Atlas graph
	 * @return
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public static Set<SerializablePCG> loadAll() throws JsonParseException, JsonMappingException, IOException {
		Set<SerializablePCG> pcgs = new HashSet<SerializablePCG>();
		for(Node masterEntry : Common.universe().nodes(PCG.PCGNode.PCG_Master_Entry).eval().nodes()){
			for(SerializablePCG pcg : SerializablePCG.load(masterEntry)){
				pcgs.add(pcg);
			}
		}
		return pcgs;
	}
	
	/**
	 * Saves the PCG instance to the Atlas graph
	 * @param instance
	 * @throws JsonProcessingException
	 */
	public static void save(SerializablePCG instance) throws JsonProcessingException {
		ObjectMapper jsonMapper = new ObjectMapper();
		String jsonString = jsonMapper.writeValueAsString(instance.data);
		instance.getMasterEntry().putAttr(PCG_INSTANCE_ATTRIBUTE_PREFIX + instance.getInstanceID(), jsonString);
	}
	
	/**
	 * Deletes the PCG instance from the Atlas graph
	 * @param instance
	 */
	public static void delete(SerializablePCG instance){
		instance.getMasterEntry().removeAttr(PCG_INSTANCE_ATTRIBUTE_PREFIX + instance.getInstanceID());
	}
	
	/**
	 * Purges all records of PCGs from the Atlas graph
	 */
	public static void deleteAll(){
		for(Node masterEntry : new AtlasHashSet<Node>(Common.universe().nodes(PCG.PCGNode.PCG_Master_Entry).eval().nodes())){
			Set<String> attrs = new HashSet<String>();
			for(String attr : masterEntry.attr().keys()){
				attrs.add(attr);
			}
			for(String attr : attrs){
				if(attr.startsWith(PCG_INSTANCE_ATTRIBUTE_PREFIX)){
					masterEntry.attr().remove(attr);
				}
			}
		}
	}

	/**
	 * Returns the instance ID of this PCG which can be used to load the PCG
	 * later
	 * 
	 * @return
	 */
	public String getInstanceID(){
		return this.data.instanceID;
	}
	
	@Override
	public long getCreationTime() {
		return data.creationTime;
	}

	@Override
	public String getName() {
		return data.name;
	}

	@Override
	public Q getPCG() {
		return Common.toQ(data.pcg.getGraph());
	}

	@Override
	public Q getCFG() {
		return Common.toQ(data.cfg.getGraph());
	}

	@Override
	public Node getMasterEntry() {
		return data.masterEntry.getNode();
	}

	@Override
	public Node getMasterExit() {
		return data.masterExit.getNode();
	}
	
	@Override
	public Q getRoots() {
		return Common.toQ(data.roots.getNodes());
	}

	@Override
	public Q getExits() {
		return Common.toQ(data.exits.getNodes());
	}
	
	@Override
	public Q getEvents() {
		return Common.toQ(data.events.getNodes());
	}

	@Override
	public void setName(String name) {
		data.name = name;
	}
	
	/**
	 * Gets the last access time
	 */
	public long getLastAccessTime() {
		return this.data.lastAccessTime;
	}
	
	/**
	 * Updates the last access time to the current time
	 */
	public void updateLastAccessTime() {
		this.data.lastAccessTime = System.currentTimeMillis();
	}

}
