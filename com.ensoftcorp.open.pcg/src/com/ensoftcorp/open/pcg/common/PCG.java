package com.ensoftcorp.open.pcg.common;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.xcsg.XCSG_Extension;

public abstract class PCG {
	
	/**
	 * Defines tags and attributes for PCG nodes
	 */
	public static interface PCGNode {
		/**
		 * Tag applied to the newly created master entry node
		 */
		@XCSG_Extension
		public static final String PCG_Master_Entry = "PCG_Master_Entry";
		
		/**
		 * Tag applied to the newly create master exit node
		 */
		@XCSG_Extension
		public static final String PCG_Master_Exit = "PCG_Master_Exit";
	}
	
	/**
	 * Defines tags and attributes for PCG edges
	 */
	public static interface PCGEdge {
		/**
		 * Tag applied to CFG edges that are retained in the final PCG
		 */
		@XCSG_Extension
		public static final String PCG_Edge = "PCG_Edge";
	}
	
	/**
	 * Gets the time (unix time) that the this PCG was created
	 * @return
	 */
	public abstract long getCreationTime();
	
	/**
	 * Updates this PCG's given name
	 * @param givenName 
	 */
	public abstract void setName(String name);
	
	/**
	 * Returns the name assigned to this PCG
	 * @return
	 */
	public abstract String getName();
	
	/**
	 * Returns the function that contains this PCG
	 * @return
	 */
	public Node getFunction() {
		// assertion: the factory class has already asserted that the cfg has to
		// be contained within a single function and is non-empty
		return CommonQueries.getContainingFunctions(getCFG()).eval().nodes().one();
	}
	
	/**
	 * Returns the project control graph
	 * @return
	 */
	public abstract Q getPCG();
	
	/**
	 * Returns the original control flow graph
	 * @return
	 */
	public abstract Q getCFG();
	
	/**
	 * Returns the master entry of the PCG and CFG
	 * @return
	 */
	public abstract Node getMasterEntry();
	
	/**
	 * Returns the roots of the control flow graph specified to create the PCG
	 * 
	 * @return
	 */
	public abstract Q getRoots();
	
	/**
	 * Returns the exits of the control flow graph specified to create the PCG
	 * 
	 * @return
	 */
	public abstract Q getExits();
	
	/**
	 * Returns the master exit of the PCG and CFG
	 * @return
	 */
	public abstract Node getMasterExit();
	
	/**
	 * Returns the set of events used to construct the PCG
	 * @return
	 */
	public abstract Q getEvents();
	
}
