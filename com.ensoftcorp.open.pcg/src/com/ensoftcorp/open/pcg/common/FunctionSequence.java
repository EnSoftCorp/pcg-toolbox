package com.ensoftcorp.open.pcg.common;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
public class FunctionSequence {

	static Map<Node,List<List<String>>> sequence = new HashMap<Node,List<List<String>>>();
	static Map<Node,Integer> sequenceCount = new HashMap<Node,Integer>();
	static Map<Node,BigInteger> finalsequenceCount = new HashMap<Node,BigInteger>();
	static Map<Node,BigInteger> storeSequenceCount = new HashMap<Node,BigInteger>();
	static AtlasSet<Node> processedFunction = new AtlasHashSet<Node>();
	static AtlasSet<GraphElement> el = new AtlasHashSet<GraphElement>();
	static Q sequenceGraph;
	public static void functionSequence(Q functions) {
		    Q cg = Common.universe().edges(XCSG.Call).forward(functions);
		    sequenceGraph=Common.empty();
			for(Node function: cg.eval().nodes()) {
				if(CommonQueries.isEmpty(CommonQueries.cfg(function))) continue;
				List<List<String>> seq = FunctionCallSequenceGenerator.getSequence(function);
				sequence.put(function, seq);
				sequenceCount.put(function, seq.size());
				sequenceGraph =sequenceGraph.union(FunctionCallSequenceGenerator.getSequenceGraph());
			
			}
			Q graph = Common.empty();
			for(Node root: sequenceGraph.roots().eval().nodes()) {
				graph = graph.union(cg.reverseStep(Common.toQ(root)));
			}
			sequenceGraph = sequenceGraph.union(graph);
			sequence.clear();
			sequenceCount.clear();
			finalsequenceCount.clear();
			processedFunction.clear();
		}
	public static Q getSequenceGraph(Q functions) {
		functionSequence(functions);
		Q res = sequenceGraph;
		sequenceGraph.empty();
		return res;
	}

}
