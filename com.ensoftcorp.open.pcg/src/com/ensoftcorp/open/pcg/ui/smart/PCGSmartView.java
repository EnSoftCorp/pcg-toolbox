package com.ensoftcorp.open.pcg.ui.smart;

import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.scripts.selections.AbstractAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.AtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.CFG;
import com.ensoftcorp.open.commons.analysis.StandardQueries;
import com.ensoftcorp.open.pcg.common.HighlighterUtils;
import com.ensoftcorp.open.pcg.factory.PCGFactory;

/**
 * Input:
 * One or more ControlFlow_Nodes or DataFlow_Nodes.
 * 
 * DataFlow_Nodes imply selection of their enclosing ControlFlow_Nodes and Methods. 
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

		Q methods = cfSelection.getImpliedMethods();
		
		if (methods.eval().nodes().isEmpty()){
			return null;
		}
		
		Q events = cfSelection.getImpliedControlFlow();
		
		Q cfg;
		if(inlcudeExceptionalControlFlow()){
			cfg = CFG.excfg(methods); // exceptional
		} else {
			cfg = CFG.cfg(methods); // non-exceptional
		}
		
		Q pcg = PCGFactory.PCG(cfg, events);
		
		// ensure original selection is visible
		pcg = pcg.union(cfSelection.getSelectedDataFlow());
		
		Markup m = new Markup();
		HighlighterUtils.applyHighlightsForCFEdges(m);
		
		return new StyledResult(pcg, m);
	}
	
	protected static class ControlFlowSelection {
		
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
}
