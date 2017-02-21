package com.ensoftcorp.open.pcg.ui;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;

public class PCGComponents implements Comparable<PCGComponents> {
	private String name;
	private long createdAt;
	private AtlasSet<Node> callGraphFunctions;
	private AtlasSet<Node> differentiatingCallsitesSetA;
	private AtlasSet<Node> differentiatingCallsitesSetB;
	private AtlasSet<Node> controlFlowEvents;

	public PCGComponents(String name) {
		this.name = name;
		this.createdAt = System.currentTimeMillis();
		this.callGraphFunctions = new AtlasHashSet<Node>();
		this.differentiatingCallsitesSetA = new AtlasHashSet<Node>();
		this.differentiatingCallsitesSetB = new AtlasHashSet<Node>();
		this.controlFlowEvents = new AtlasHashSet<Node>();
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public AtlasSet<Node> getCallGraphFunctions() {
		return callGraphFunctions;
	}
	
	public void setCallGraphFunctions(AtlasSet<Node> callGraphFunctions) {
		this.callGraphFunctions = callGraphFunctions;
	}

	public boolean addCallGraphFunctions(AtlasSet<Node> callGraphFunctions){
		return this.callGraphFunctions.addAll(callGraphFunctions);
	}
	
	public boolean removeCallGraphFunction(Node callGraphFunction){
		return this.callGraphFunctions.remove(callGraphFunction);
	}

	public AtlasSet<Node> getDifferentiatingCallsitesSetA() {
		return differentiatingCallsitesSetA;
	}

	public void setDifferentiatingCallsitesSetA(AtlasSet<Node> differentiatingCallsitesSetA) {
		this.differentiatingCallsitesSetA = differentiatingCallsitesSetA;
	}
	
	public boolean addDifferentiatingCallsitesSetA(AtlasSet<Node> differentiatingCallsitesSetA) {
		return this.differentiatingCallsitesSetA.addAll(differentiatingCallsitesSetA);
	}
	
	public boolean removeDifferentiatingCallsiteSetA(Node differentiatingCallsiteSetA) {
		return this.differentiatingCallsitesSetA.remove(differentiatingCallsiteSetA);
	}

	public AtlasSet<Node> getDifferentiatingCallsitesSetB() {
		return differentiatingCallsitesSetB;
	}

	public void setDifferentiatingCallsitesSetB(AtlasSet<Node> differentiatingCallsitesSetB) {
		this.differentiatingCallsitesSetB = differentiatingCallsitesSetB;
	}
	
	public boolean addDifferentiatingCallsitesSetB(AtlasSet<Node> differentiatingCallsitesSetB) {
		return this.differentiatingCallsitesSetB.addAll(differentiatingCallsitesSetB);
	}
	
	public boolean removeDifferentiatingCallsiteSetB(Node differentiatingCallsiteSetB) {
		return this.differentiatingCallsitesSetB.remove(differentiatingCallsiteSetB);
	}

	public AtlasSet<Node> getControlFlowEvents() {
		return controlFlowEvents;
	}

	public void setControlFlowEvents(AtlasSet<Node> controlFlowEvents) {
		this.controlFlowEvents = controlFlowEvents;
	}
	
	public boolean addControlFlowEvents(AtlasSet<Node> controlFlowEvents) {
		return this.controlFlowEvents.addAll(controlFlowEvents);
	}
	
	public boolean removeControlFlowEvent(Node controlFlowEvent) {
		return this.controlFlowEvents.remove(controlFlowEvent);
	}

	@Override
	public int compareTo(PCGComponents other) {
		return Long.compare(this.createdAt, other.createdAt);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		PCGComponents other = (PCGComponents) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}