package com.ensoftcorp.open.pcg.ui.smart;

import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.scripts.selections.AbstractAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.AtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.HighlighterUtils;
import com.ensoftcorp.open.pcg.common.PCG;

/**
 * Input:
 * One or more ControlFlow_Nodes or DataFlow_Nodes.
 * 
 * DataFlow_Nodes imply selection of their enclosing ControlFlow_Nodes and Functions. 
 * ControlFlow_Nodes are interpreted as selected events.
 * 
 * Output:
 * The Event Flow Graph, where events are selected ControlFlow_Nodes.
 * 
 * Highlights: 
 * GREEN = true ControlFlow_Edges
 * RED   = false ControlFlow_Edges
 * CYAN  = events (ControlFlow_Node, DataFlow_Node)
 */
public class PCGSmartView extends AbstractAtlasSmartViewScript implements AtlasSmartViewScript{

	protected boolean inlcudeExceptionalControlFlow(){
		return false;
	}
	
	@Override
	public String getTitle() {
		return "Projected Control Graph (PCG)";
	}

	@Override
	public StyledResult selectionChanged(IAtlasSelectionEvent selected) {
		ControlFlowSelection cfSelection = ControlFlowSelection.processSelection(selected);

		Q functions = cfSelection.getImpliedFunctions();
		
		if (functions.eval().nodes().isEmpty()){
			return null;
		}
		
		Q events = cfSelection.getImpliedControlFlow();
		Q pcg = PCG.create(events, inlcudeExceptionalControlFlow());
		
		// ensure original selection is visible
		pcg = pcg.union(cfSelection.getSelectedDataFlow());
		
		Markup m = new Markup();
		HighlighterUtils.applyHighlightsForCFEdges(m);
		
		return new StyledResult(pcg, m);
	}
	
	protected static class ControlFlowSelection {
		
		public Q getSelectedFunctions() {
			return selectedFunctions;
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
		 * Directly selected functions and the 
		 * functions which enclose any selected
		 * data or control flow nodes.
		 * 
		 * @return
		 */
		public Q getImpliedFunctions() {
			return impliedFunctions;
		}

		private Q selectedFunctions;
		private Q selectedDataFlow;
		private Q selectedControlFlow;
		private Q impliedControlFlow;
		private Q impliedFunctions;

		private ControlFlowSelection(Q selectedFunctions, Q selectedDataFlow,
				Q selectedControlFlow, Q enclosingControlFlow,
				Q enclosingFunctions) {
					this.selectedFunctions = selectedFunctions;
					this.selectedDataFlow = selectedDataFlow;
					this.selectedControlFlow = selectedControlFlow;
					this.impliedControlFlow = enclosingControlFlow;
					this.impliedFunctions = enclosingFunctions;
		}

		public static ControlFlowSelection processSelection(IAtlasSelectionEvent atlasSelection) {
			Q selected = atlasSelection.getSelection();

			Q selectedFunctions = selected.nodesTaggedWithAny(XCSG.Function);
			Q selectedDataFlow = selected.nodesTaggedWithAny(XCSG.DataFlow_Node);
			Q selectedControlFlow = selected.nodesTaggedWithAny(XCSG.ControlFlow_Node);		
			
			Q impliedControlFlow = selectedControlFlow.union(selectedDataFlow.parent());
			
			Q impliedFunctions = selectedFunctions.union(CommonQueries.getContainingFunctions(selectedControlFlow.union(selectedDataFlow)));
			
			return new ControlFlowSelection(selectedFunctions, selectedDataFlow, selectedControlFlow, impliedControlFlow, impliedFunctions);
		}
	}
}
