package com.ensoftcorp.open.pcg.common;

import java.awt.Color;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;

public class HighlighterUtils {

	/** 
	 * The default blue fill color for CFG nodes
	 */
	public static final Color cfgNodeFillColor = new Color(51, 175, 243);
	
	public static final Color cfgDefault = java.awt.Color.GRAY;
	public static final Color cfgTrue = java.awt.Color.WHITE;
	public static final Color cfgFalse = java.awt.Color.BLACK;
	public static final Color cfgExceptional = java.awt.Color.BLUE;
	public static final Color cfgBack = java.awt.Color.GRAY;

	/**
	 * GRAY  = ControlFlowBackEdge
	 * GREEN = true ControlFlow_Edges
	 * RED   = false ControlFlow_Edges
	 * BLUE  = ExceptionalControlFlow_Edges
	 * @param h
	 */
	public static void applyHighlightsForCFEdges(Markup m) {
		// default to Yellow
		Q cfEdge = Query.universe().edgesTaggedWithAny(XCSG.ControlFlow_Edge, PCG.PCGEdge.EventFlow_Edge);
		//h.highlightEdges(cfEdge, cfgDefault);
		m.setEdge(cfEdge, MarkupProperty.EDGE_COLOR, cfgDefault);
		Q cvTrue = Query.universe().selectEdge(XCSG.conditionValue, Boolean.TRUE, "true");
		Q cvFalse = Query.universe().selectEdge(XCSG.conditionValue, Boolean.FALSE, "false");
		m.setEdge(cvTrue, MarkupProperty.EDGE_COLOR, cfgTrue);
		m.setEdge(cvFalse, MarkupProperty.EDGE_COLOR, cfgFalse);
		m.setEdge(Query.universe().edgesTaggedWithAny(XCSG.ExceptionalControlFlow_Edge), MarkupProperty.EDGE_COLOR, cfgExceptional);
	}
	
	public static Markup getIPCG2Markup(Q ipcg, Q events, Q selectedAncestors, Q selectedExpansions) {
		events = events.nodes(XCSG.ControlFlow_Node);
		Q eventFunctions = IPCG2.getFunctionsContainingEvents(events);
		Q ipcgCallGraph = IPCG2.getIPCGCallGraph(eventFunctions, selectedAncestors);
		Q ipcgFunctions = ipcgCallGraph.retainNodes();
		Q expandableFunctions = ipcgCallGraph.retainNodes().difference(eventFunctions);
		Q ancestorFunctions = IPCG2.getAncestorFunctions(events);
		
		Markup m = new Markup();
		
		// color the selected events
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, Color.CYAN);
		
		// make expandable functions dotted
		m.setNode(expandableFunctions, MarkupProperty.NODE_BORDER_STYLE, MarkupProperty.LineStyle.DASHED_DOTTED);
		
		// highlight border of ancestor functions
		m.setNode(ancestorFunctions, MarkupProperty.NODE_BORDER_COLOR, Color.RED);
		
		// set ipcg callsite control flow gray
		AtlasSet<Edge> iPCGEdgesEntryOrExit = new AtlasHashSet<Edge>();
		for(Edge edge : ipcg.eval().edges()) {
			GraphElement from = edge.getNode(EdgeDirection.FROM);
			GraphElement to = edge.getNode(EdgeDirection.TO);
			if(from.taggedWith(PCGNode.EventFlow_Master_Entry) || to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesEntryOrExit.add(edge);
			}
		}
		m.setEdge(Common.toQ(iPCGEdgesEntryOrExit), MarkupProperty.EDGE_COLOR, Color.GRAY);

		// highlight control flow edges
		applyHighlightsForCFEdges(m);
		
		return m;
	}
	
	public static Markup getIPCGMarkup(Q ipcg, Q entryFunctions, Q events) {
		Markup m = new Markup();
		
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, Color.CYAN);

		Q iPCGEdgesEntryOrExit = Common.empty();
		Q iPCGEdgesFunctions1 = Common.empty();
		Q iPCGEdgesFunctions2 = Common.empty();
		
		for(GraphElement ge : ipcg.eval().edges()) {
			GraphElement from = ge.getNode(EdgeDirection.FROM);
			GraphElement to = ge.getNode(EdgeDirection.TO);
			if(from.taggedWith(PCGNode.EventFlow_Master_Entry) || to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesEntryOrExit = iPCGEdgesEntryOrExit.union(Common.toQ(ge));
			}
		}
		
		m.setEdge(iPCGEdgesEntryOrExit, MarkupProperty.EDGE_COLOR, Color.GRAY);
		m.setEdge(iPCGEdgesFunctions1, MarkupProperty.EDGE_COLOR, Color.YELLOW.brighter());
		m.setEdge(iPCGEdgesFunctions1, MarkupProperty.EDGE_WEIGHT, new Integer(2));
		m.setEdge(iPCGEdgesFunctions2, MarkupProperty.EDGE_COLOR, Color.MAGENTA.brighter());
		m.setEdge(iPCGEdgesFunctions2, MarkupProperty.EDGE_WEIGHT, new Integer(2));
		m.setEdge(iPCGEdgesFunctions2, MarkupProperty.EDGE_STYLE, MarkupProperty.LineStyle.DASHED_DOTTED);

		// highlight edges
		applyHighlightsForCFEdges(m);
		
		return m;
	}
	
}
