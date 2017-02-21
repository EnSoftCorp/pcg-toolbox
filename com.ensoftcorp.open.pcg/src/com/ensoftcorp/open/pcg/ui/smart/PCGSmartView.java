package com.ensoftcorp.open.pcg.ui.smart;

import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.ui.scripts.selections.AbstractAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.AtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.CFG;
import com.ensoftcorp.open.pcg.factory.PCGFactory;
import com.ensoftcorp.open.pcg.ui.smart.SelectionHighlighterUtils.ControlFlowSelection;

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
	public StyledResult selectionChanged(IAtlasSelectionEvent arg0) {
		ControlFlowSelection cfSelection = SelectionHighlighterUtils.ControlFlowSelection.processSelection(arg0);

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
		SelectionHighlighterUtils.applyHighlightsForCFEdges(m);
		
		return new StyledResult(pcg, m);
	}
}
