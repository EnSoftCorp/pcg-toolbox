package com.ensoftcorp.open.pcg.ui.smart;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.scripts.selections.AtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.FilteringAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCGFactory;
import com.ensoftcorp.open.pcg.common.highlighter.PCGHighlighter;

/**
 * Input:
 * One or more ControlFlow_Nodes or DataFlow_Nodes.
 * 
 * DataFlow_Nodes imply selection of their enclosing ControlFlow_Nodes.
 * ControlFlow_Nodes are interpreted as selected events.
 * 
 * Output:
 * The Event Flow Graph, where events are selected ControlFlow_Nodes.
 * 
 * Highlights: 
 * GREEN = true ControlFlow_Edges
 * RED   = false ControlFlow_Edges
 * CYAN  = events ControlFlow_Node
 */
public class PCGSmartView extends FilteringAtlasSmartViewScript implements AtlasSmartViewScript{

	@Override
	protected String[] getSupportedNodeTags() {
		return new String[]{XCSG.ControlFlow_Node, XCSG.DataFlow_Node};
	}

	@Override
	protected String[] getSupportedEdgeTags() {
		return NOTHING;
	}

	@Override
	public String getTitle() {
		return "Projected Control Graph (PCG)";
	}

	protected boolean inlcudeExceptionalControlFlow(){
		return false;
	}
	
	@Override
	public StyledResult selectionChanged(IAtlasSelectionEvent event) {
		ControlFlowSelection filteredCFSelection = ControlFlowSelection.processSelection(filter(event));
		
		Q events = filteredCFSelection.getSelectedControlFlow().union(filteredCFSelection.getImpliedControlFlow());
		
		// don't try to create a pcg if the selection is empty
		if(CommonQueries.isEmpty(events)){
			return null;
		}
		
		// don't respond to inputs of events that span multiple functions
		AtlasSet<Node> functions = CommonQueries.getContainingFunctions(events).eval().nodes();
		if(functions.size() > 1){
			return null;
		}
		
		Node function = functions.one();
		Q cfg = inlcudeExceptionalControlFlow() ? CommonQueries.excfg(function) : CommonQueries.cfg(function);
		Q pcg = PCGFactory.create(cfg, events, true).getPCG();
		
		// need to union in the contains edges because they are not contained in the default index
		pcg = pcg.union(Query.universe().edges(XCSG.Contains).reverse(pcg));
		
		return new StyledResult(pcg, PCGHighlighter.getPCGMarkup(events));
	}
	
	private static class ControlFlowSelection {
		
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

		private Q selectedControlFlow;
		private Q impliedControlFlow;

		private ControlFlowSelection(Q selectedControlFlow, Q enclosingControlFlow) {
					this.selectedControlFlow = selectedControlFlow;
					this.impliedControlFlow = enclosingControlFlow;
		}

		public static ControlFlowSelection processSelection(Q filteredSelection) {
			Q selectedDataFlow = filteredSelection.nodes(XCSG.DataFlow_Node);
			Q selectedControlFlow = filteredSelection.nodes(XCSG.ControlFlow_Node);		
			Q impliedControlFlow = selectedControlFlow.union(selectedDataFlow.parent());
			return new ControlFlowSelection(selectedControlFlow, impliedControlFlow);
		}
	}

	@Override
	protected StyledResult selectionChanged(IAtlasSelectionEvent input, Q filteredSelection) {
		return null;
	}
}
