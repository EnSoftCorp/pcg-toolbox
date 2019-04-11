package com.ensoftcorp.open.pcg.ui.builder;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.IPCG;

public class PCGComponents {
	private String name;
	private AtlasSet<Node> controlFlowEvents;
	private AtlasSet<Node> includedAncestors;
	private AtlasSet<Node> expandedFunctions;
	private boolean exceptionalControlFlow;
	private boolean extendStructure;
	private boolean humanConsumer;

	public PCGComponents(String name) {
		this.name = name;
		this.controlFlowEvents = new AtlasHashSet<Node>();
		this.includedAncestors = new AtlasHashSet<Node>();
		this.expandedFunctions = new AtlasHashSet<Node>();
		exceptionalControlFlow = false;
		extendStructure = true;
		humanConsumer = true;
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

	public boolean isExtendStructureEnabled() {
		return extendStructure;
	}

	public void setExtendStructure(boolean extendStructure) {
		this.extendStructure = extendStructure;
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

	public void setHumanConsumer(boolean humanConsumer) {
		this.humanConsumer = humanConsumer;
	}
	
	public boolean isHumanConsumer() {
		return this.humanConsumer;
	}

}