package com.ensoftcorp.open.pcg.ui.smart;

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
 * BLUE  = ExceptionalControlFlow_Edges
 * CYAN  = events (ControlFlow_Node, DataFlow_Node)
 */
public class ExceptionalPCGSmartView extends PCGSmartView {

	@Override
	public String getTitle() {
		return "Exceptional Projected Control Graph (ExPCG)";
	}
	
	@Override
	protected boolean inlcudeExceptionalControlFlow(){
		return true;
	}
	
}
