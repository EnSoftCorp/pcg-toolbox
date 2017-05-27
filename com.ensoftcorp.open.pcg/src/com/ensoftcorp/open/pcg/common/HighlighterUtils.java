package com.ensoftcorp.open.pcg.common;

import java.awt.Color;

import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;

public class HighlighterUtils {

	public static final Color cfgDefault = java.awt.Color.GRAY;
	public static final Color cfgTrue = java.awt.Color.WHITE;
	public static final Color cfgFalse = java.awt.Color.BLACK;
	public static final Color cfgExceptional = java.awt.Color.BLUE;

	public static final Color pcgEvent = java.awt.Color.CYAN;
	public static final Color ipcgMaster = java.awt.Color.GRAY;
	
	/**
	 * GRAY  = ControlFlowBackEdge
	 * GREEN = true ControlFlow_Edges
	 * RED   = false ControlFlow_Edges
	 * BLUE  = ExceptionalControlFlow_Edges
	 * @param h
	 */
	public static void applyHighlightsForCFEdges(Markup m) {
		Q cfEdge = Query.universe().edgesTaggedWithAny(XCSG.ControlFlow_Edge, PCG.PCGEdge.EventFlow_Edge);
		m.setEdge(cfEdge, MarkupProperty.EDGE_COLOR, cfgDefault);
		Q cvTrue = Query.universe().selectEdge(XCSG.conditionValue, Boolean.TRUE, "true");
		Q cvFalse = Query.universe().selectEdge(XCSG.conditionValue, Boolean.FALSE, "false");
		m.setEdge(cvTrue, MarkupProperty.EDGE_COLOR, cfgTrue);
		m.setEdge(cvFalse, MarkupProperty.EDGE_COLOR, cfgFalse);
		m.setEdge(Query.universe().edgesTaggedWithAny(XCSG.ExceptionalControlFlow_Edge), MarkupProperty.EDGE_COLOR, cfgExceptional);
	}
	
	public static Markup getPCGMarkup(Q pcg, Q events) {
		Markup m = new Markup();
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, pcgEvent);
		// highlight control flow edges
		applyHighlightsForCFEdges(m);
		return m;
	}
	
	public static Markup getIPCGMarkup(Q ipcg, Q events, Q selectedAncestors, Q selectedExpansions) {
		events = events.nodes(XCSG.ControlFlow_Node);
		Q implicitCallsiteEvents = Common.toQ(IPCG.getImplicitCallsiteEvents(events, selectedAncestors, selectedExpansions));
		
		Markup m = new Markup();
		
		// color the selected events
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, pcgEvent);
		m.setNode(implicitCallsiteEvents, MarkupProperty.NODE_BACKGROUND_COLOR, pcgEvent);
		
		// gray and dot the call edges
		Q callEdges = Common.universe().edges(XCSG.Call).retainEdges();
		m.setEdge(callEdges, MarkupProperty.EDGE_STYLE, MarkupProperty.LineStyle.DASHED_DOTTED);
		
		// highlight the IPCG root function red
		Q ipcgCallGraph = IPCG.getIPCGCallGraph(CommonQueries.getContainingFunctions(events), selectedAncestors);
		Q ipcgCallGraphRoots = ipcgCallGraph.roots();
		m.setNode(ipcgCallGraphRoots, MarkupProperty.NODE_BACKGROUND_COLOR, ipcgMaster);
		
		// highlight the master entry/exit nodes of root function red
		Q ipcgCallGraphRootMasterNodes = ipcgCallGraphRoots.children().nodes(PCGNode.EventFlow_Master_Entry, PCGNode.EventFlow_Master_Exit);
		// alternatively it could be disconnected, so just grab them by traversing the functions CFG
		Q eventFlowEdges = Common.universe().edges(PCGEdge.EventFlow_Edge);
		Q functionCFG = CommonQueries.cfg(ipcgCallGraphRootMasterNodes);
		ipcgCallGraphRootMasterNodes = ipcgCallGraphRootMasterNodes.union(eventFlowEdges.predecessors(functionCFG).nodes(PCGNode.EventFlow_Master_Entry));
		ipcgCallGraphRootMasterNodes = ipcgCallGraphRootMasterNodes.union(eventFlowEdges.successors(functionCFG).nodes(PCGNode.EventFlow_Master_Exit));
		m.setNode(ipcgCallGraphRootMasterNodes, MarkupProperty.NODE_BACKGROUND_COLOR, ipcgMaster);

		// highlight control flow edges
		applyHighlightsForCFEdges(m);
		
		return m;
	}
	
}
