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

	@Override
	public FrontierStyledResult explore(FrontierEdgeExploreEvent event, FrontierStyledResult oldResult) {
		return SimpleScriptUtil.explore(this, event, oldResult);
	}

	@Override
	public FrontierStyledResult evaluate(IAtlasSelectionEvent selection, int reverse, int forward) {
		// get the selected events
		Q selections = filter(selection); // could be control or data flow selections
		Q events = selections.nodes(XCSG.ControlFlow_Node).union(selections.nodes(XCSG.DataFlow_Node).parent());
		
		// don't try to create a pcg if the selection is empty
		if(CommonQueries.isEmpty(events)){
			return null;
		}
		
		// don't respond to inputs of events that span multiple functions
		if(CommonQueries.getContainingFunctions(events).eval().nodes().size() > 1){
			return null;
		}

		// compute the selected PCG slice
		PCG current = PCGSlice.getPCGSlice(events, reverse, forward);

		// compute what is on the frontier
		PCG next = PCGSlice.getPCGSlice(events, (reverse+1), (forward+1));
		Q frontierReverse = current.getPCG().reverseStepOn(next.getPCG().difference(Common.toQ(next.getMasterExit())), 1);
		frontierReverse = frontierReverse.retainEdges().differenceEdges(current.getPCG());
		frontierReverse = (reverse == Integer.MAX_VALUE ? Common.empty() : frontierReverse);
		Q frontierForward = current.getPCG().forwardStepOn(next.getPCG().difference(Common.toQ(next.getMasterEntry())), 1);
		frontierForward = frontierForward.retainEdges().differenceEdges(current.getPCG());
		frontierForward = (forward == Integer.MAX_VALUE ? Common.empty() : frontierForward);
		
		// style and return the result
		IMarkup markup = PCGHighlighter.getPCGMarkup(current.getEvents());
		FrontierStyledResult result = new FrontierStyledResult(current.getPCG(), frontierReverse, frontierForward, markup);
		result.setInput(events);
		return result;
	}

	@Override
	public int getDefaultStepTop() {
		return 0;
	}
	
	@Override
	public int getDefaultStepBottom() {
		return 0;
	}

	@Override
	protected StyledResult selectionChanged(IAtlasSelectionEvent input, Q filteredSelection) {
		return null;
	}
}

