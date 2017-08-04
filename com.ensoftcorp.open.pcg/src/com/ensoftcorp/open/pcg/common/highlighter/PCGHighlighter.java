package com.ensoftcorp.open.pcg.common.highlighter;

import java.awt.Color;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.markup.IMarkup;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.markup.PropertySet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.highlighter.CFGHighlighter;
import com.ensoftcorp.open.pcg.common.IPCG;
import com.ensoftcorp.open.pcg.common.IPCG.IPCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;

public class PCGHighlighter {

	public static final Color pcgEvent = java.awt.Color.CYAN;
	public static final Color ipcgMaster = java.awt.Color.GRAY;
	
	public static IMarkup getPCGMarkup(Q events) {
		
		// labels for conditionValue
		Markup m2 = new Markup() {
			
			@Override
			public PropertySet get(GraphElement element) {
				if (element instanceof Edge) {
					if (element.taggedWith(PCGEdge.EventFlow_Edge) && element.hasAttr(XCSG.conditionValue)) {
						return new PropertySet().set(MarkupProperty.LABEL_TEXT, ""+element.getAttr(XCSG.conditionValue)); //$NON-NLS-1$
					}
				}
				return null;
			}
		};

		Markup m = new Markup(m2);

		// treat event flow edges as control flow edges
		Q cfEdge = Query.universe().edges(PCGEdge.EventFlow_Edge);
		m.setEdge(cfEdge, MarkupProperty.EDGE_COLOR, CFGHighlighter.cfgDefault);
		
		// highlight control flow edges
		CFGHighlighter.applyHighlightsForCFEdges(m);
		
		
		// highlight loop depths
		CFGHighlighter.applyHighlightsForLoopDepth(m);
		
		// color events (this should override previous settings)
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, pcgEvent);
		
		
		return m;
	}
	
	public static IMarkup getIPCGMarkup(Q ipcg, Q events, Q selectedAncestors, Q selectedExpansions) {
		events = events.nodes(XCSG.ControlFlow_Node);
		Markup m = new Markup();

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

		// treat event flow edges as control flow edges
		Q cfEdge = Query.universe().edgesTaggedWithAny(PCGEdge.EventFlow_Edge, IPCGEdge.InterproceduralEventFlow_Edge);
		m.setEdge(cfEdge, MarkupProperty.EDGE_COLOR, CFGHighlighter.cfgDefault);
		
		// highlight control flow edges
		CFGHighlighter.applyHighlightsForCFEdges(m);
		
		// highlight loop depths
		CFGHighlighter.applyHighlightsForLoopDepth(m);
		
		// color the events and implicit callsite events (this should override previous settings)
		Q implicitCallsiteEvents = Common.toQ(IPCG.getImplicitCallsiteEvents(events, selectedAncestors, selectedExpansions));
		m.setNode(events, MarkupProperty.NODE_BACKGROUND_COLOR, pcgEvent);
		m.setNode(implicitCallsiteEvents, MarkupProperty.NODE_BACKGROUND_COLOR, pcgEvent);
		
		return m;
	}
	
}
