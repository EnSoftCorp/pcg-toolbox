package com.ensoftcorp.open.pcg.common;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCGFactory;

public class FunctionCallSequenceGenerator {
	static AtlasSet<GraphElement> SequencegraphElements = new AtlasHashSet<GraphElement>();
	static Q sequenceGraph = Common.empty();
	public static List<List<String>> getSequence(Node function) {
		Q cfg = CommonQueries.cfg(function);
		AtlasSet<Edge> backedges = cfg.edgesTaggedWithAny(XCSG.ControlFlowBackEdge).eval().edges();
		Q dag = cfg.differenceEdges(Common.toQ(backedges));
		AtlasSet<Node> controlFlowNodes = dag.nodes(XCSG.ControlFlow_Node).eval().nodes();
		AtlasSet<Node> events = new AtlasHashSet<Node>();
		AtlasSet<Node> icfgFunctions = new AtlasHashSet<Node>();
		for (Node cfNode : controlFlowNodes) {
			if (isCallSite(Common.toQ(cfNode))) {
				Q callsites = getContainingCallSites(Common.toQ(cfNode));
				AtlasSet<Node> targets = CallSiteAnalysis.getTargets(callsites).eval().nodes();
				for (Node n : targets) {
					if (!CommonQueries.isEmpty(CommonQueries.cfg(n))) {
						events.add(cfNode);
						icfgFunctions.add(n);
					}
				}

			}
		}

		Q pcg = PCGFactory.create(dag, Common.toQ(events)).getPCG();
		return getFunctionSequence(pcg,icfgFunctions);
	}

	public static List<List<String>> getFunctionSequence(Q pcg, AtlasSet<Node >icfgFunctions) {
		List<List<String>> functionSequence = new ArrayList<List<String>>();
		AtlasSet<Node> pcgLeaves = pcg.leaves().eval().nodes();
		Node pcgRoot = pcg.roots().eval().nodes().one();
		if (pcgRoot == null) {
			return functionSequence;
		} else if (pcgLeaves.contains(pcgRoot)) {
			Q pcgRootQ = Common.toQ(pcgRoot);
			ArrayList<String> sequence = new ArrayList<String>();
			if (isCallSite(pcgRootQ)) {
				Q callsites = getContainingCallSites(pcgRootQ);
				AtlasSet<Node> targets = CallSiteAnalysis.getTargets(callsites).eval().nodes();
				for (Node n : targets) {
					if(icfgFunctions.contains(n)) {
				    String functionName = getFunctionName(n);
				    sequence.add(functionName);
					SequencegraphElements.add(CommonQueries.functions(functionName).eval().nodes().one());
					}
				}
			}
			if (sequence.size() != 0) {
				functionSequence.add(sequence);
				sequenceGraph = Common.toQ(SequencegraphElements);
			}
			SequencegraphElements.clear();
			return functionSequence;
		}
		Stack<SearchNode> stack = new Stack<SearchNode>();
		stack.push(new SearchNode(0, pcgRoot));
		List<Node> history = new ArrayList<Node>();
		while (!stack.isEmpty()) {
			SearchNode searchNode = stack.pop();
			Node currentNode = searchNode.getNode();
			int depth = searchNode.getDepth();
			history = new LinkedList<>(history.subList(0, depth));
			history.add(currentNode);
			if (pcgLeaves.contains(currentNode)) {
				ArrayList<Node> functionsInOneSequence = new ArrayList<Node>();
				List<String> Savehistory = new ArrayList<String>();
				for (Node node : history) {
					if (isCallSite(Common.toQ(node))) {
						Q callsites = getContainingCallSites(Common.toQ(node));
						AtlasSet<Node> targets = CallSiteAnalysis.getTargets(callsites).eval().nodes();
						for (Node n : targets) {
							if(icfgFunctions.contains(n)) {
								String functionName = getFunctionName(n);
								Savehistory.add(functionName);
								SequencegraphElements.add(CommonQueries.functions(functionName).eval().nodes().one());
								functionsInOneSequence.add(CommonQueries.functions(functionName).eval().nodes().one());
							}
						}
					}

				}

				List<String> sequence = new ArrayList<String>(Savehistory);
				if (!functionSequence.contains(sequence))
					if (sequence.size() != 0) {
						functionSequence.add(sequence);
						int j = 1;
						for (int i = 0; i < functionsInOneSequence.size(); i++) {
							createEdge(i, j, functionsInOneSequence);
							j++;
						}
					}
			} else {
				Q outgoingEdges = pcg.forwardStep(Common.toQ(currentNode));
				Q trueEdges = outgoingEdges.selectEdge(XCSG.conditionValue, true, "true");
				Q nonTrueEdges = outgoingEdges.differenceEdges(trueEdges);
				List<Edge> sortedOutgoingEdges = new ArrayList<Edge>();
				for (Edge outgoingEdge : nonTrueEdges.eval().edges()) {
					sortedOutgoingEdges.add(outgoingEdge);
				}
				Collections.sort(sortedOutgoingEdges, new Comparator<Edge>() {
					@Override
					public int compare(Edge e1, Edge e2) {
						return getConditionValue(e1).compareTo(getConditionValue(e2));
					}

					private String getConditionValue(Edge e) {
						if (e.hasAttr(XCSG.conditionValue)) {
							return e.getAttr(XCSG.conditionValue).toString();
						} else {
							return "unconditional";
						}
					}
				});
				for (Edge outgoingEdge : trueEdges.eval().edges()) {
					sortedOutgoingEdges.add(outgoingEdge);
				}
				for (Edge outgoingEdge : sortedOutgoingEdges) {
					Node successor = outgoingEdge.to();
					stack.push(new SearchNode(depth + 1, successor));
				}

			}
		}
		if (!SequencegraphElements.isEmpty())
			sequenceGraph = Common.toQ(SequencegraphElements);
		SequencegraphElements.clear();
		return functionSequence;
	}

	private static class SearchNode {
		private int depth;
		private Node node;

		public SearchNode(int depth, Node node) {
			super();
			this.depth = depth;
			this.node = node;
		}

		public int getDepth() {
			return depth;
		}

		public Node getNode() {
			return node;
		}
	}

	private static String getFunctionName(Node node) {

		String functionName = "end";
		if (node.hasAttr(XCSG.sourceCorrespondence)) {
			SourceCorrespondence sc = (SourceCorrespondence) node.getAttr(XCSG.sourceCorrespondence);
			if (sc != null) {
				functionName = node.getAttr(XCSG.name).toString();
			}
		}
		return functionName;
	}

	public static boolean isCallSite(Q cfNode, Q functionContext) {
		Q callsites = getContainingCallSites(cfNode);
		if (CommonQueries.isEmpty(callsites)) {
			return false;
		}
		if (!CommonQueries.isEmpty(functionContext)) {
			Q targets = CallSiteAnalysis.getTargets(callsites);
			if (CommonQueries.isEmpty(targets.intersection(functionContext))) {
				return false;
			}
		}
		return true;
	}

	public static boolean isCallSite(Q cfNode) {
		return isCallSite(cfNode, Common.empty());
	}

	public static Q getContainingCallSites(Q cfNode) {
		return cfNode.children().nodes(XCSG.CallSite);
	}
	
	static void createEdge(int i, int j, ArrayList<Node> sequence) {
		Edge e = null;
		if (j < sequence.size()) {
			Node from = sequence.get(i);
			Node to = sequence.get(j);
			Q rs = Common.universe().edges("FunctionSequence_Edge").between(Common.toQ(from), Common.toQ(to));
			if (CommonQueries.isEmpty(rs.retainEdges())) {
					e = Graph.U.createEdge(from, to);
					e.tag("FunctionSequence_Edge");
			}

		}
		if (e != null)
			SequencegraphElements.add(e);
	}

	public static Q getSequenceGraph() {
		Q res = sequenceGraph;
		sequenceGraph = Common.empty();
		return res;
	}

}
