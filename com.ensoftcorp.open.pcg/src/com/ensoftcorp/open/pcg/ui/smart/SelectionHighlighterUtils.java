package com.ensoftcorp.open.pcg.ui.smart;

import java.awt.Color;

import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.StandardQueries;
import com.ensoftcorp.open.pcg.common.PCG;

public class SelectionHighlighterUtils {

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
	
	public static class ControlFlowSelection {
		
		public Q getSelectedMethods() {
			return selectedMethods;
		}

		public Q getSelectedDataFlow() {
			return selectedDataFlow;
		}

		public Q getSelectedControlFlow() {
			return selectedControlFlow;
		}

		/**
		 * Directly selected control flow nodes and the 
		 * control flow nodes which enclose any selected
		 * data flow nodes.
		 * 
		 * @return
		 */
		public Q getImpliedControlFlow() {
			return impliedControlFlow;
		}

		/**
		 * Directly selected methods and the 
		 * methods which enclose any selected
		 * data or control flow nodes.
		 * 
		 * @return
		 */
		public Q getImpliedMethods() {
			return impliedMethods;
		}

		private Q selectedMethods;
		private Q selectedDataFlow;
		private Q selectedControlFlow;
		private Q impliedControlFlow;
		private Q impliedMethods;

		private ControlFlowSelection(Q selectedMethods, Q selectedDataFlow,
				Q selectedControlFlow, Q enclosingControlFlow,
				Q enclosingMethods) {
					this.selectedMethods = selectedMethods;
					this.selectedDataFlow = selectedDataFlow;
					this.selectedControlFlow = selectedControlFlow;
					this.impliedControlFlow = enclosingControlFlow;
					this.impliedMethods = enclosingMethods;
		}

		public static ControlFlowSelection processSelection(IAtlasSelectionEvent atlasSelection) {
			Q selected = atlasSelection.getSelection();

			Q selectedMethods = selected.nodesTaggedWithAny(XCSG.Method);
			Q selectedDataFlow = selected.nodesTaggedWithAny(XCSG.DataFlow_Node);
			Q selectedControlFlow = selected.nodesTaggedWithAny(XCSG.ControlFlow_Node);		
			
			Q impliedControlFlow = selectedControlFlow.union(selectedDataFlow.parent());
			
			Q impliedMethods = selectedMethods.union(StandardQueries.getContainingFunctions(selectedControlFlow.union(selectedDataFlow)));
			
			return new ControlFlowSelection(selectedMethods, selectedDataFlow, selectedControlFlow, impliedControlFlow, impliedMethods);
		}
	}

	public static java.awt.Color mediumGreen = new java.awt.Color(60,140,70);
	
}
