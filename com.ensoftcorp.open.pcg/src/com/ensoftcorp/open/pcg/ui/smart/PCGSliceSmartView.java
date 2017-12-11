package com.ensoftcorp.open.pcg.ui.smart;

import com.ensoftcorp.atlas.core.markup.IMarkup;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.FrontierStyledResult;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.scripts.selections.FilteringAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.IExplorableScript;
import com.ensoftcorp.atlas.ui.scripts.selections.IResizableScript;
import com.ensoftcorp.atlas.ui.scripts.util.SimpleScriptUtil;
import com.ensoftcorp.atlas.ui.selection.event.FrontierEdgeExploreEvent;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG;
import com.ensoftcorp.open.pcg.common.PCGSlice;
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
public class PCGSliceSmartView extends FilteringAtlasSmartViewScript implements IResizableScript, IExplorableScript {

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
		return "PCG Slice";
	}

	protected boolean inlcudeExceptionalControlFlow(){
		return false;
	}
	
	@Override
	public FrontierStyledResult explore(FrontierEdgeExploreEvent event, FrontierStyledResult oldResult) {
		return SimpleScriptUtil.explore(this, event, oldResult);
	}

	@Override
	public FrontierStyledResult evaluate(IAtlasSelectionEvent event, int reverse, int forward) {
		ControlFlowSelection filteredCFSelection = ControlFlowSelection.processSelection(filter(event));
		Q events = filteredCFSelection.getSelectedControlFlow().union(filteredCFSelection.getImpliedControlFlow());
		
		// don't try to create a pcg if the selection is empty
		if(CommonQueries.isEmpty(events)){
			return null;
		}
		
		// don't respond to inputs of events that span multiple functions
		if(CommonQueries.getContainingFunctions(events).eval().nodes().size() > 1){
			return null;
		}
		
		// must have reverse or forward > 0
		if(reverse == 0 && forward == 0){
			return null;
		}

		PCG current = PCGSlice.getPCGSlice(events, reverse, forward);

//		// compute what is on the frontier
//		PCG next = PCGSlice.getPCGSlice(events, (reverse+1), (forward+1));
//		Q frontierReverse = current.getPCG().reverseStepOn(next.getPCG().difference(Common.toQ(next.getMasterExit())), 1);
//		frontierReverse = frontierReverse.retainEdges().differenceEdges(current.getPCG());
//		Q frontierForward = current.getPCG().forwardStepOn(next.getPCG().difference(Common.toQ(next.getMasterEntry())), 1);
//		frontierForward = frontierForward.retainEdges().differenceEdges(current.getPCG());
//		
//		IMarkup markup = PCGHighlighter.getPCGMarkup(current.getEvents());
//		FrontierStyledResult result = new FrontierStyledResult(current.getPCG(), frontierReverse, frontierForward, markup);
		
		IMarkup markup = PCGHighlighter.getPCGMarkup(current.getEvents());
		FrontierStyledResult result = new FrontierStyledResult(current.getPCG(), Common.empty(), Common.empty(), markup);
		
		result.setInput(events);
		return result;
	}

	@Override
	public int getDefaultStepTop() {
		return 1;
	}
	
	@Override
	public int getDefaultStepBottom() {
		return 0;
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
			Q selectedDataFlow = filteredSelection.nodesTaggedWithAny(XCSG.DataFlow_Node);
			Q selectedControlFlow = filteredSelection.nodesTaggedWithAny(XCSG.ControlFlow_Node);		
			Q impliedControlFlow = selectedControlFlow.union(selectedDataFlow.parent());
			return new ControlFlowSelection(selectedControlFlow, impliedControlFlow);
		}
	}

	@Override
	protected StyledResult selectionChanged(IAtlasSelectionEvent input, Q filteredSelection) {
		return null;
	}
}

