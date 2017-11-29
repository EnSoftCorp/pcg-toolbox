package com.ensoftcorp.open.pcg.common;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.algorithms.UniqueEntryExitControlFlowGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;

public class RawPCG extends PCG {

	private Graph pcg;
	private Graph cfg;
	private Node masterEntry;
	private Node masterExit;
	private AtlasSet<Node> roots;
	private AtlasSet<Node> exits;
	private AtlasSet<Node> events;
	private long creationTime;
	private String name;
	
	protected RawPCG(Graph pcg, UniqueEntryExitControlFlowGraph ucfg, AtlasSet<Node> events){
		this(pcg, ucfg.getCFG(), ucfg.getEntryNode(), ucfg.getRoots(), ucfg.getExitNode(), ucfg.getExits(), events);
	}
	
	public RawPCG(Graph pcg, Graph cfg, Node masterEntry, AtlasSet<Node> roots, Node masterExit, AtlasSet<Node> exits, AtlasSet<Node> events) {
		this.pcg = pcg;
		this.cfg = cfg;
		this.masterEntry = masterEntry;
		this.masterExit = masterExit;
		this.roots = roots;
		this.exits = exits;
		this.events = events;
		this.creationTime = System.currentTimeMillis();
		this.name = "";
	}
	
	@Override
	public long getCreationTime() {
		return creationTime;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Q getPCG() {
		return Common.toQ(pcg);
	}

	@Override
	public Q getCFG() {
		return Common.toQ(cfg);
	}

	@Override
	public Node getMasterEntry() {
		return masterEntry;
	}

	@Override
	public Q getRoots() {
		return Common.toQ(roots);
	}

	@Override
	public Q getExits() {
		return Common.toQ(exits);
	}

	@Override
	public Node getMasterExit() {
		return masterExit;
	}

	@Override
	public Q getEvents() {
		return Common.toQ(events);
	}

}
