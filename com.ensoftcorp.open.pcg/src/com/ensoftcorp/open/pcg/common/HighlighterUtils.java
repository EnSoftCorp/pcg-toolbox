package com.ensoftcorp.open.pcg.common;

import java.awt.Color;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.StandardQueries;
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
	
	public static Markup getIPCGMarkup(Q ipcg, Q entryMethods, Q events) {
		Markup m = new Markup();
		
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, Color.CYAN);

		Q iPCGEdgesEntryOrExit = Common.empty();
		Q iPCGEdgesMethods1 = Common.empty();
		Q iPCGEdgesMethods2 = Common.empty();
		
		for(GraphElement ge : ipcg.eval().edges()) {
			GraphElement from = ge.getNode(EdgeDirection.FROM);
			GraphElement to = ge.getNode(EdgeDirection.TO);
			if(from.taggedWith(PCGNode.EventFlow_Master_Entry) || to.taggedWith(PCGNode.EventFlow_Master_Exit)) {
				iPCGEdgesEntryOrExit = iPCGEdgesEntryOrExit.union(Common.toQ(ge));
			}
		}
		
		m.setEdge(iPCGEdgesEntryOrExit, MarkupProperty.EDGE_COLOR, Color.GRAY);
		m.setEdge(iPCGEdgesMethods1, MarkupProperty.EDGE_COLOR, Color.YELLOW.brighter());
		m.setEdge(iPCGEdgesMethods1, MarkupProperty.EDGE_WEIGHT, new Integer(2));
		m.setEdge(iPCGEdgesMethods2, MarkupProperty.EDGE_COLOR, Color.MAGENTA.brighter());
		m.setEdge(iPCGEdgesMethods2, MarkupProperty.EDGE_WEIGHT, new Integer(2));
		m.setEdge(iPCGEdgesMethods2, MarkupProperty.EDGE_STYLE, MarkupProperty.LineStyle.DASHED_DOTTED);

		// highlight edges
		applyHighlightsForCFEdges(m);
		
		return m;
	}
	
}
