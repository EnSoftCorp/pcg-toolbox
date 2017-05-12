package com.ensoftcorp.open.pcg.ui;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.IPCG;

public class PCGComponents implements Comparable<PCGComponents> {
	private String name;
	private long createdAt;
	private AtlasSet<Node> controlFlowEvents;
	private AtlasSet<Node> includedAncestors;
	private AtlasSet<Node> expandedFunctions;
	private boolean exceptionalControlFlow;
	private boolean callEdgeOverlay;

	public PCGComponents(String name) {
		this.name = name;
		this.createdAt = System.currentTimeMillis();
		this.controlFlowEvents = new AtlasHashSet<Node>();
		this.includedAncestors = new AtlasHashSet<Node>();
		this.expandedFunctions = new AtlasHashSet<Node>();
		exceptionalControlFlow = false;
		callEdgeOverlay = true;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isExceptionalControlFlowEnabled() {
		return exceptionalControlFlow;
	}

	public void setExceptionalControlFlow(boolean exceptionalControlFlow) {
		this.exceptionalControlFlow = exceptionalControlFlow;
	}

	public boolean isCallEdgeOverlayEnabled() {
		return callEdgeOverlay;
	}

	public void setCallEdgeOverlay(boolean callEdgeOverlay) {
		this.callEdgeOverlay = callEdgeOverlay;
	}
	
	public AtlasSet<Node> getControlFlowEvents() {
		return controlFlowEvents;
	}
	
	public void addExpandedFunction(Node expandableFunction) {
		if(getExpandableFunctions().contains(expandableFunction)){
			expandedFunctions.add(expandableFunction);
		}
	}
	
	public void removeExpandedFunction(Node expandableFunction) {
		expandedFunctions.remove(expandableFunction);
	}
	
	public AtlasSet<Node> getExpandableFunctions() {
		return IPCG.getExpandableFunctions(Common.toQ(getControlFlowEvents()), Common.toQ(getIncludedAncestorFunctions())).eval().nodes();
	}
	
	public AtlasSet<Node> getExpandedFunctions() {
		return expandedFunctions;
	}
	
	public void addIncludedAncestorFunction(Node ancestorFunction) {
		if(getAncestorFunctions().contains(ancestorFunction)){
			includedAncestors.add(ancestorFunction);
		}
	}
	
	public void removeIncludedAncestorFunction(Node ancestorFunction) {
		includedAncestors.remove(ancestorFunction);
	}
	
	public AtlasSet<Node> getIncludedAncestorFunctions(){
		return includedAncestors;
	}
	
	public AtlasSet<Node> getAncestorFunctions(){
		return IPCG.getAncestorFunctions(Common.toQ(getControlFlowEvents())).eval().nodes();
	}
	
	public AtlasSet<Node> getContainingFunctions(){
		Q containingFunctions = CommonQueries.getContainingFunctions(Common.toQ(getControlFlowEvents()));
		return containingFunctions.eval().nodes();
	}

	public void setControlFlowEvents(AtlasSet<Node> controlFlowEvents) {
		this.controlFlowEvents = controlFlowEvents;
	}
	
	public boolean addControlFlowEvents(AtlasSet<Node> controlFlowEvents) {
		return this.controlFlowEvents.addAll(controlFlowEvents);
	}
	
	public boolean removeControlFlowEvents(AtlasSet<Node> controlFlowEvents) {
		boolean result = false;
		for(Node event : controlFlowEvents){
			result |= this.controlFlowEvents.remove(event);
		}
		return result;
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