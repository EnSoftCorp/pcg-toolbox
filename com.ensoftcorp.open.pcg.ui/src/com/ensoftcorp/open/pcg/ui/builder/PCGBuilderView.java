package com.ensoftcorp.open.pcg.ui.builder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.ResourceManager;
import org.eclipse.wb.swt.SWTResourceManager;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.markup.IMarkup;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.algorithms.ICFG;
import com.ensoftcorp.open.commons.algorithms.InterproceduralControlFlowGraph;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.ui.utilities.DisplayUtils;
import com.ensoftcorp.open.commons.utilities.selection.GraphSelectionListenerView;
import com.ensoftcorp.open.pcg.common.ICFGPCGFactory;
import com.ensoftcorp.open.pcg.common.IPCG;
import com.ensoftcorp.open.pcg.common.highlighter.PCGHighlighter;
public class PCGBuilderView extends GraphSelectionListenerView {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.ensoftcorp.open.pcg.ui.pcgBuilderView";

	private static Map<String,PCGComponents> pcgs = new HashMap<String,PCGComponents>();
	private static PCGBuilderView VIEW;
	
	private static boolean initialized = false;
	private static int pcgCounter = 1;
	
	public static class PCGBuilder {
		public static PCGBuilderComponents get(String name){
			if(!pcgs.containsKey(name)){
				pcgCounter++;
				PCGComponents pcg = new PCGComponents(name);
				pcgs.put(name, pcg);
				Display.getDefault().asyncExec(new Runnable(){
					@Override
					public void run() {
						VIEW.addPCG(VIEW.pcgFolder, pcg);
						VIEW.setFocus(pcg);
					}
				});
			}
			return new PCGBuilderComponents(name);
		}
	}
	
	public static class PCGBuilderComponents {
		private String name;

		private PCGBuilderComponents(String name){
			this.name = name;
		}
		
		public boolean addEvents(Q events){
			events = events.nodes(XCSG.ControlFlow_Node);
			if(events.eval().nodes().isEmpty()){
				throw new IllegalArgumentException("No selected control flow events");
			}
			final PCGComponents pcg = pcgs.get(name);
			if(pcg == null){
				throw new IllegalArgumentException("PCG instance no longer exists.");
			}
			boolean result = pcg.addControlFlowEvents(events.eval().nodes());
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					VIEW.refreshAll(pcg);
					VIEW.setFocus(pcg);
				}
			});
			return result;
		}
		
		public boolean removeEvents(Q events){
			events = events.nodes(XCSG.ControlFlow_Node);
			if(events.eval().nodes().isEmpty()){
				throw new IllegalArgumentException("No selected control flow events");
			}
			final PCGComponents pcg = pcgs.get(name);
			if(pcg == null){
				throw new IllegalArgumentException("PCG instance no longer exists.");
			}
			boolean result = pcg.removeControlFlowEvents(events.eval().nodes());
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					VIEW.refreshAll(pcg);
					VIEW.setFocus(pcg);
				}
			});
			return result;
		}
		
		public void setEvents(Q events){
			events = events.nodes(XCSG.ControlFlow_Node);
			if(events.eval().nodes().isEmpty()){
				throw new IllegalArgumentException("No selected control flow events");
			}
			final PCGComponents pcg = pcgs.get(name);
			if(pcg == null){
				throw new IllegalArgumentException("PCG instance no longer exists.");
			}
			pcg.setControlFlowEvents(events.eval().nodes());
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					VIEW.refreshAll(pcg);
					VIEW.setFocus(pcg);
				}
			});
		}
		
		@Override
		public String toString() {
			final PCGComponents pcg = pcgs.get(name);
			if(pcg == null){
				throw new IllegalArgumentException("PCG instance no longer exists.");
			}
			return "PCGBuilderComponents [name=" + name + ", #events=" + pcg.getControlFlowEvents().size() + "]";
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PCGBuilderComponents other = (PCGBuilderComponents) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
	}
	
	private Button exceptionalControlFlowCheckbox;
	private Button extendStructureCheckbox;
	private ScrolledComposite controlFlowEventsScrolledComposite;
	private ScrolledComposite containingFunctionsScrolledComposite;
	private ScrolledComposite ancestorFunctionsScrolledComposite;
	private ScrolledComposite expandableFunctionsScrolledComposite;
	
	/**
	 * The constructor.
	 */
	public PCGBuilderView() {
		VIEW = this;
	}
	
	private static String getUniqueName(String pcgName){
		int suffix = 2;
		while(pcgs.containsKey(pcgName)){
			pcgName = pcgName + "_" + (suffix++);
		}
		return pcgName;
	}
	
	private void setFocus(PCGComponents pcg){
		int index = 0;
		ArrayList<PCGComponents> sortedPCGs = new ArrayList<PCGComponents>(pcgs.values());
		Collections.sort(sortedPCGs); // sorted by creation time
		for(PCGComponents sortedPCG : sortedPCGs){
			if(pcg.equals(sortedPCG)){
				break;
			} else {
				index++;
			}
		}
		if(index <= pcgFolder.getItemCount()-1){
			pcgFolder.setSelection(index);
		}
	}
	
	private CTabFolder pcgFolder;
	
	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));
		
		pcgFolder = new CTabFolder(parent, SWT.CLOSE);
		pcgFolder.setBorderVisible(true);
		pcgFolder.setSimple(false); // adds the Eclipse style "swoosh"
		
		// add a prompt to ask if we should really close the builder tab
		pcgFolder.addCTabFolder2Listener(new CTabFolder2Adapter() {
			public void close(CTabFolderEvent event) {
				MessageBox messageBox = new MessageBox(Display.getCurrent().getActiveShell(),
						SWT.ICON_QUESTION | SWT.YES | SWT.NO);
				messageBox.setMessage("Close PCG builder instance?");
				messageBox.setText("Closing Tab");
				int response = messageBox.open();
				if (response == SWT.YES) {
					String tabName = pcgFolder.getSelection().getText();
					pcgs.remove(tabName);
				} else {
					event.doit = false;
				}
			}
		});
		
		pcgFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		// create a new PCG if this is the first launch
		if(!initialized){
			int PCG_NUMBER = (pcgCounter++);
			String PCG_NAME = "PCG" + PCG_NUMBER;
			PCGComponents pcg = new PCGComponents(PCG_NAME);
			pcgs.put(PCG_NAME, pcg);
			addPCG(pcgFolder, pcg);
			initialized = true;
		} else {
			// otherwise load what is already in memory
			ArrayList<PCGComponents> sortedPCGs = new ArrayList<PCGComponents>(pcgs.values());
			Collections.sort(sortedPCGs); // sorted by creation time
			for(PCGComponents pcg : sortedPCGs){
				addPCG(pcgFolder, pcg);
			}
		}
		
		// add an add PCG tab button to the action bar
		final Action addPCGAction = new Action() {
			public void run() {
				int PCG_NUMBER = (pcgCounter++);
				String PCG_NAME = getUniqueName("PCG" + PCG_NUMBER);
				PCGComponents pcg = new PCGComponents(PCG_NAME);
				pcgs.put(PCG_NAME, pcg);
				addPCG(pcgFolder, pcg);
			}
		};
		
		// uncomment to preview with window builder
//		PCGComponents pcg = new PCGComponents("TEST");
//		pcgs.put("TEST", pcg);
//		addPCG(pcgFolder, pcg);
		
		addPCGAction.setText("New PCG");
		addPCGAction.setToolTipText("Creates another PCG builder tab");
		ImageDescriptor newConfigurationIcon = ImageDescriptor.createFromImage(ResourceManager.getPluginImage("com.ensoftcorp.open.pcg.ui", "icons/new_configuration_button.png"));
		addPCGAction.setImageDescriptor(newConfigurationIcon);
		addPCGAction.setDisabledImageDescriptor(newConfigurationIcon);
		addPCGAction.setHoverImageDescriptor(newConfigurationIcon);
		getViewSite().getActionBars().getToolBarManager().add(addPCGAction);
		
		// add the selection event handlers
		this.registerGraphHandlers();
	}

	private void addPCG(final CTabFolder pcgFolder, final PCGComponents pcg) {
		final CTabItem pcgTab = new CTabItem(pcgFolder, SWT.NONE);
		pcgTab.setText(pcg.getName());
		
		Composite pcgComposite = new Composite(pcgFolder, SWT.NONE);
		pcgTab.setControl(pcgComposite);
		pcgComposite.setLayout(new GridLayout(1, false));
		
		Composite pcgControlPanelComposite = new Composite(pcgComposite, SWT.NONE);
		pcgControlPanelComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		pcgControlPanelComposite.setLayout(new GridLayout(6, false));
		
		Label pcgNameLabel = new Label(pcgControlPanelComposite, SWT.NONE);
		pcgNameLabel.setSize(66, 14);
		pcgNameLabel.setText("PCG Label: ");
		
		final Text pcgLabelText = new Text(pcgControlPanelComposite, SWT.BORDER);
		pcgLabelText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		pcgLabelText.setSize(473, 19);
		pcgLabelText.setText(pcg.getName());
		
		pcgLabelText.addTraverseListener(new TraverseListener(){
			@Override
			public void keyTraversed(TraverseEvent event) {
				if(event.detail == SWT.TRAVERSE_RETURN){
					String newName = pcgLabelText.getText();
					pcgTab.setText(newName);
					pcg.setName(newName);
				}
			}
		});
		
		exceptionalControlFlowCheckbox = new Button(pcgControlPanelComposite, SWT.CHECK);
		exceptionalControlFlowCheckbox.setText("Exceptional Control Flow");
		
		exceptionalControlFlowCheckbox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				pcg.setExceptionalControlFlow(exceptionalControlFlowCheckbox.getSelection());
			}
		});
		
		final Button humanConsumerCheckbox = new Button(pcgControlPanelComposite, SWT.CHECK);
		humanConsumerCheckbox.setSelection(true);
		humanConsumerCheckbox.setText("Human Consumer");
		
		extendStructureCheckbox = new Button(pcgControlPanelComposite, SWT.CHECK);
		extendStructureCheckbox.setSelection(true);
		extendStructureCheckbox.setText("Extend Structure");
		
		extendStructureCheckbox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				pcg.setExtendStructure(extendStructureCheckbox.getSelection());
			}
		});
		
		final Button showButton = new Button(pcgControlPanelComposite, SWT.NONE);
		showButton.setText("Show PCG");
		
		final Composite pcgBuilderComposite = new Composite(pcgComposite, SWT.NONE);
		pcgBuilderComposite.setLayout(new GridLayout(1, false));
		pcgBuilderComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		final SashForm groupSashForm = new SashForm(pcgBuilderComposite, SWT.NONE);
		groupSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		final SashForm eventFunctionsGroupSashForm = new SashForm(groupSashForm, SWT.NONE);
		eventFunctionsGroupSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		final Group eventsGroup = new Group(eventFunctionsGroupSashForm, SWT.NONE);
		eventsGroup.setText("Control Flow Events");
		eventsGroup.setLayout(new GridLayout(1, false));
		
		final Composite addControlFlowEventsComposite = new Composite(eventsGroup, SWT.NONE);
		addControlFlowEventsComposite.setLayout(new GridLayout(2, false));
		addControlFlowEventsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		
		final Label addControlFlowEventsLabel = new Label(addControlFlowEventsComposite, SWT.NONE);
		addControlFlowEventsLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
		addControlFlowEventsLabel.setText("Add Selected");
		
		final Label addControlFlowEventsButton = new Label(addControlFlowEventsComposite, SWT.NONE);
		addControlFlowEventsButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		addControlFlowEventsButton.setImage(ResourceManager.getPluginImage("com.ensoftcorp.open.pcg.ui", "icons/add_button.png"));
		
		controlFlowEventsScrolledComposite = new ScrolledComposite(eventsGroup, SWT.H_SCROLL | SWT.V_SCROLL);
		controlFlowEventsScrolledComposite.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		controlFlowEventsScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		controlFlowEventsScrolledComposite.setExpandHorizontal(true);
		controlFlowEventsScrolledComposite.setExpandVertical(true);
		
		final Group containingFunctionsGroup = new Group(eventFunctionsGroupSashForm, SWT.NONE);
		containingFunctionsGroup.setText("Containing Functions");
		containingFunctionsGroup.setLayout(new GridLayout(1, false));
		
		containingFunctionsScrolledComposite = new ScrolledComposite(containingFunctionsGroup, SWT.H_SCROLL | SWT.V_SCROLL);
		containingFunctionsScrolledComposite.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		containingFunctionsScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		containingFunctionsScrolledComposite.setExpandHorizontal(true);
		containingFunctionsScrolledComposite.setExpandVertical(true);
		
		final SashForm ancestorExpandableGroupSashForm = new SashForm(groupSashForm, SWT.NONE);
		ancestorExpandableGroupSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		final Group ancestorFunctionsGroup = new Group(ancestorExpandableGroupSashForm, SWT.NONE);
		ancestorFunctionsGroup.setText("Ancestor Functions");
		ancestorFunctionsGroup.setLayout(new GridLayout(1, false));
		
		final Composite ancestorFunctionsComposite = new Composite(ancestorFunctionsGroup, SWT.NONE);
		ancestorFunctionsComposite.setLayout(new GridLayout(1, false));
		ancestorFunctionsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
//		final Label showAncestorFunctionsLabel = new Label(ancestorFunctionsComposite, SWT.NONE);
//		showAncestorFunctionsLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
//		showAncestorFunctionsLabel.setText("Show Ancestors");
//		
//		final Label showAncestorFunctionsButton = new Label(ancestorFunctionsComposite, SWT.NONE);
//		showAncestorFunctionsButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
//		showAncestorFunctionsButton.setImage(ResourceManager.getPluginImage("com.ensoftcorp.open.pcg.ui", "icons/add_button.png"));
		
		final Button showAncestorFunctionsButton = new Button(ancestorFunctionsComposite, SWT.NONE);
		showAncestorFunctionsButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		showAncestorFunctionsButton.setText("Show Ancestor Call Graph");
		
		ancestorFunctionsScrolledComposite = new ScrolledComposite(ancestorFunctionsComposite, SWT.H_SCROLL | SWT.V_SCROLL);
		ancestorFunctionsScrolledComposite.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		ancestorFunctionsScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		ancestorFunctionsScrolledComposite.setExpandHorizontal(true);
		ancestorFunctionsScrolledComposite.setExpandVertical(true);

		final Group expandableFunctionsGroup = new Group(ancestorExpandableGroupSashForm, SWT.NONE);
		expandableFunctionsGroup.setText("Expandable Functions");
		expandableFunctionsGroup.setLayout(new GridLayout(1, false));

		expandableFunctionsScrolledComposite = new ScrolledComposite(expandableFunctionsGroup, SWT.H_SCROLL | SWT.V_SCROLL);
		expandableFunctionsScrolledComposite.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		expandableFunctionsScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		expandableFunctionsScrolledComposite.setExpandHorizontal(true);
		expandableFunctionsScrolledComposite.setExpandVertical(true);

		addControlFlowEventsButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				AtlasSet<Node> selection = getSelection().eval().nodes();
				if(selection.isEmpty()){
					DisplayUtils.showError("Nothing is selected.");
				} else {
					AtlasSet<Node> controlFlowNodes = getFilteredSelections(XCSG.ControlFlow_Node);
					
					// expand search to control flow nodes that correspond to this node
					if(controlFlowNodes.isEmpty()){
						controlFlowNodes = Common.toQ(selection).containers().nodes(XCSG.ControlFlow_Node).eval().nodes();
					}
					
					if(controlFlowNodes.isEmpty()){
						DisplayUtils.showError("Selections must correspond to control flow statements.");
					} else {
						if(pcg.addControlFlowEvents(controlFlowNodes)){
							refreshControlFlowEvents(pcg);
							refreshContainingFunctions(pcg);
							refreshAncestorFunctions(pcg);
							refreshExpandableFunctions(pcg);
						}
					}
				}
			}
		});
		
		showButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean noControlFlowEvents = pcg.getControlFlowEvents().isEmpty();
				if(noControlFlowEvents){
					DisplayUtils.showError("No control flow events are defined.");
				} else {
					try {
						Q events = Common.toQ(pcg.getControlFlowEvents());
						if(humanConsumerCheckbox.getSelection()) {
							Q selectedAncestors = Common.toQ(pcg.getIncludedAncestorFunctions());
							Q selectedExpansions = Common.toQ(pcg.getExpandedFunctions());
							Q pcgResult = IPCG.getIPCG(events, selectedAncestors, selectedExpansions, exceptionalControlFlowCheckbox.getSelection());
							IMarkup pcgResultMarkup = PCGHighlighter.getIPCGMarkup(pcgResult, events, selectedAncestors, selectedExpansions);
							DisplayUtils.show(pcgResult, pcgResultMarkup, pcg.isExtendStructureEnabled(), pcg.getName());
						} else {
							Q containingFunctions = Common.toQ(pcg.getContainingFunctions());
							Q selectedAncestors = Common.toQ(pcg.getIncludedAncestorFunctions());
							Q selectedFunctions = containingFunctions.union(selectedAncestors);
							AtlasSet<Node> selectedFunctionRoots = Query.universe().edges(XCSG.Call).between(selectedFunctions, selectedFunctions).roots().eval().nodes();
							if(selectedFunctionRoots.size() != 1) {
								DisplayUtils.showError("There must be a single root among all event containing functions and selected ancestor functions.");
							} else {
								Q selectedExpansions = Common.toQ(pcg.getExpandedFunctions());
								selectedExpansions = selectedExpansions.union(containingFunctions, selectedAncestors);
								ICFG icfg = InterproceduralControlFlowGraph.icfg(selectedFunctionRoots.one(), selectedExpansions.nodes(XCSG.Function).eval().nodes());
								DisplayUtils.show(icfg.getICFG(), "ICFG");
								Thread.sleep(200);
								Q pcgResult = ICFGPCGFactory.create(icfg.getICFG(), events).getICFGPCG();
								IMarkup pcgResultMarkup = PCGHighlighter.getIPCGMarkup(pcgResult, events);
								DisplayUtils.show(pcgResult, pcgResultMarkup, pcg.isExtendStructureEnabled(), pcg.getName());
							}
						}
					} catch (Throwable t){
						DisplayUtils.showError(t, "An error occurred while constructing the IPCG.");
					}
				}
			}
		});
		
		showAncestorFunctionsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean noAncestors = pcg.getAncestorFunctions().isEmpty();
				if(noAncestors){
					DisplayUtils.showError("There are no ancestors to show.");
				} else {
					Q containingFunctions = Common.toQ(pcg.getContainingFunctions());
					
					Markup markup = new Markup();
					
					// color the functions containing events
					markup.setNode(containingFunctions, MarkupProperty.NODE_BACKGROUND_COLOR, Color.CYAN);
					
					Graph ancestorIPCGCallGraph = IPCG.getIPCGCallGraph(containingFunctions, Common.toQ(pcg.getAncestorFunctions())).eval();
					AtlasSet<Edge> ancestorIPCGCallGraphEdges = new AtlasHashSet<Edge>();
					ancestorIPCGCallGraphEdges.addAll(ancestorIPCGCallGraph.edges());
					AtlasSet<Edge> ipcgCallGraphEdges = IPCG.getIPCGCallGraph(containingFunctions, Common.empty()).eval().edges();
					for(Edge ipcgCallGraphEdge : ipcgCallGraphEdges){
						ancestorIPCGCallGraphEdges.remove(ipcgCallGraphEdge);
					}
					
					// color the ancestor call edges (that could be optionally included as dashed gray edges)
					markup.setEdge(Common.toQ(ancestorIPCGCallGraphEdges), MarkupProperty.EDGE_STYLE, MarkupProperty.LineStyle.DASHED_DOTTED);
					markup.setEdge(Common.toQ(ancestorIPCGCallGraphEdges), MarkupProperty.EDGE_COLOR, Color.GRAY);
					
					DisplayUtils.show(Common.toQ(ancestorIPCGCallGraph), markup, true, pcg.getName() + " Ancestor Call Graph");
				}
			}
		});
		
		// set the tab selection to this newly created tab
		pcgFolder.setSelection(pcgFolder.getItemCount()-1);
	}
	
	private void refreshAncestorFunctions(final PCGComponents pcg) {
		Composite ancestorFunctionsScrolledCompositeContent = new Composite(ancestorFunctionsScrolledComposite, SWT.NONE);
		
		for(final Node ancestorFunction : pcg.getAncestorFunctions()){
			ancestorFunctionsScrolledCompositeContent.setLayout(new GridLayout(1, false));
			
			Label controlFlowEventsSeperatorLabel = new Label(ancestorFunctionsScrolledCompositeContent, SWT.SEPARATOR | SWT.HORIZONTAL);
			controlFlowEventsSeperatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			
			Composite ancestorFunctionsComposite = new Composite(ancestorFunctionsScrolledCompositeContent, SWT.NONE);
			ancestorFunctionsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
			ancestorFunctionsComposite.setLayout(new GridLayout(2, false));
			
			final Button includeAncestorCheckbox = new Button(ancestorFunctionsComposite, SWT.CHECK);
			includeAncestorCheckbox.addSelectionListener(new SelectionAdapter() {
		        @Override
		        public void widgetSelected(SelectionEvent event) {
		            Button checkbox = (Button) event.getSource();
		            if(checkbox.getSelection()){
		            	pcg.addIncludedAncestorFunction(ancestorFunction);
		            } else {
		            	pcg.removeIncludedAncestorFunction(ancestorFunction);
		            }
		            refreshExpandableFunctions(pcg);
		        }
		    });

			Label eventLabel = new Label(ancestorFunctionsComposite, SWT.NONE);
			eventLabel.setToolTipText(CommonQueries.getQualifiedFunctionName(ancestorFunction) + "\nAddress: " + ancestorFunction.address().toAddressString());
			eventLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			eventLabel.setBounds(0, 0, 59, 14);
			
			eventLabel.setText(ancestorFunction.getAttr(XCSG.name).toString());
		}
		ancestorFunctionsScrolledComposite.setContent(ancestorFunctionsScrolledCompositeContent);
		ancestorFunctionsScrolledComposite.setMinSize(ancestorFunctionsScrolledCompositeContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}
	
	private void refreshControlFlowEvents(final PCGComponents pcg) {
		Composite controlFlowEventsScrolledCompositeContent = new Composite(controlFlowEventsScrolledComposite, SWT.NONE);
		for(final Node event : pcg.getControlFlowEvents()){
			controlFlowEventsScrolledCompositeContent.setLayout(new GridLayout(1, false));
			
			Label controlFlowEventsSeperatorLabel = new Label(controlFlowEventsScrolledCompositeContent, SWT.SEPARATOR | SWT.HORIZONTAL);
			controlFlowEventsSeperatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			
			Composite controlFlowEventsEntryComposite = new Composite(controlFlowEventsScrolledCompositeContent, SWT.NONE);
			controlFlowEventsEntryComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
			controlFlowEventsEntryComposite.setLayout(new GridLayout(2, false));
			
			final Label deleteButton = new Label(controlFlowEventsEntryComposite, SWT.NONE);
			deleteButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1));
			deleteButton.setImage(ResourceManager.getPluginImage("com.ensoftcorp.open.pcg.ui", "icons/delete_button.png"));

			Label eventLabel = new Label(controlFlowEventsEntryComposite, SWT.NONE);
			eventLabel.setText(event.getAttr(XCSG.name).toString());
			eventLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			eventLabel.setBounds(0, 0, 59, 14);
			
			Node function = CommonQueries.getContainingFunction(event);
			eventLabel.setToolTipText(event.getAttr(XCSG.name).toString() 
					+ " (" + CommonQueries.getQualifiedFunctionName(function) + ")" + "\nAddress: " + event.address().toAddressString());
			
			deleteButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					pcg.removeControlFlowEvent(event);
					refreshAll(pcg);
				}
			});
		}
		controlFlowEventsScrolledComposite.setContent(controlFlowEventsScrolledCompositeContent);
		controlFlowEventsScrolledComposite.setMinSize(controlFlowEventsScrolledCompositeContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}
	
	private void refreshButtonStates(final PCGComponents pcg){
		exceptionalControlFlowCheckbox.setSelection(pcg.isExceptionalControlFlowEnabled());
//		callGraphOverlayCheckbox.setSelection(pcg.isCallEdgeOverlayEnabled());
	}
	
	private void refreshExpandableFunctions(final PCGComponents pcg) {
		Composite expandableFunctionsScrolledCompositeContent = new Composite(expandableFunctionsScrolledComposite, SWT.NONE);

		boolean isFirst = true;
		for(final Node expandableFunction : pcg.getExpandableFunctions()){
			expandableFunctionsScrolledCompositeContent.setLayout(new GridLayout(1, false));
			
			if(isFirst){
				isFirst = false;
			} else {
				Label containingFunctionsSeperatorLabel = new Label(expandableFunctionsScrolledCompositeContent, SWT.SEPARATOR | SWT.HORIZONTAL);
				containingFunctionsSeperatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			}
			
			Composite expandableFunctionsComposite = new Composite(expandableFunctionsScrolledCompositeContent, SWT.NONE);
			expandableFunctionsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
			expandableFunctionsComposite.setLayout(new GridLayout(2, false));
			
			final Button expandFunctionCheckbox = new Button(expandableFunctionsComposite, SWT.CHECK);
			expandFunctionCheckbox.addSelectionListener(new SelectionAdapter() {
		        @Override
		        public void widgetSelected(SelectionEvent event) {
		            Button checkbox = (Button) event.getSource();
		            if(checkbox.getSelection()){
		            	pcg.addExpandedFunction(expandableFunction);
		            } else {
		            	pcg.removeExpandedFunction(expandableFunction);
		            }
		        }
		    });

			Label eventLabel = new Label(expandableFunctionsComposite, SWT.NONE);
			eventLabel.setToolTipText(CommonQueries.getQualifiedFunctionName(expandableFunction) + "\nAddress: " + expandableFunction.address().toAddressString());
			eventLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			eventLabel.setBounds(0, 0, 59, 14);
			
			eventLabel.setText(expandableFunction.getAttr(XCSG.name).toString());
		}
	
		expandableFunctionsScrolledComposite.setContent(expandableFunctionsScrolledCompositeContent);
		expandableFunctionsScrolledComposite.setMinSize(expandableFunctionsScrolledCompositeContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}
	
	private void refreshContainingFunctions(final PCGComponents pcg) {
		Composite containingFunctionsScrolledCompositeContent = new Composite(containingFunctionsScrolledComposite, SWT.NONE);

		boolean isFirst = true;
		for(final Node function : pcg.getContainingFunctions()){
			containingFunctionsScrolledCompositeContent.setLayout(new GridLayout(1, false));
			
			if(isFirst){
				isFirst = false;
			} else {
				Label containingFunctionsSeperatorLabel = new Label(containingFunctionsScrolledCompositeContent, SWT.SEPARATOR | SWT.HORIZONTAL);
				containingFunctionsSeperatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			}
			
			Composite containingFunctionsEntryComposite = new Composite(containingFunctionsScrolledCompositeContent, SWT.NONE);
			containingFunctionsEntryComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
			containingFunctionsEntryComposite.setLayout(new GridLayout(2, false));
			
			final Label deleteButton = new Label(containingFunctionsEntryComposite, SWT.NONE);
			deleteButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1));
			deleteButton.setImage(ResourceManager.getPluginImage("com.ensoftcorp.open.pcg.ui", "icons/delete_button.png"));

			Label functionLabel = new Label(containingFunctionsEntryComposite, SWT.NONE);
			functionLabel.setToolTipText(CommonQueries.getQualifiedFunctionName(function) + "\nAddress: " + function.address().toAddressString());
			functionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			functionLabel.setBounds(0, 0, 59, 14);
			functionLabel.setText(function.getAttr(XCSG.name).toString());
			
			deleteButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					// if there is a containing function there are corresponding events that need to be removed
					AtlasSet<Node> controlFlowNodesToRemove = new AtlasHashSet<Node>();
					for(Node controlFlowNode : pcg.getControlFlowEvents()){
						Node containingFunction = CommonQueries.getContainingFunction(controlFlowNode);
						if(function.equals(containingFunction)){
							controlFlowNodesToRemove.add(controlFlowNode);
						}
					}
					
					MessageBox messageBox = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
					messageBox.setMessage("Removing this function from the call graph context would remove " 
							+ controlFlowNodesToRemove.size() + " control flow events. Would you like to proceed?");
					messageBox.setText("Removing Control Flow Events");
					int response = messageBox.open();
					if (response == SWT.YES) {
						for(Node controlFlowEventToRemove : controlFlowNodesToRemove){
							pcg.removeControlFlowEvent(controlFlowEventToRemove);
						}
						refreshAll(pcg);
					}
				}

			});
		}
		containingFunctionsScrolledComposite.setContent(containingFunctionsScrolledCompositeContent);
		containingFunctionsScrolledComposite.setMinSize(containingFunctionsScrolledCompositeContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}
	
	private void refreshAll(final PCGComponents pcg) {
		refreshControlFlowEvents(pcg);
		refreshContainingFunctions(pcg);
		refreshAncestorFunctions(pcg);
		refreshExpandableFunctions(pcg);
		refreshButtonStates(pcg);
	}
	
	private AtlasSet<Node> getFilteredSelections(String... tags){
		AtlasSet<Node> selection = getSelection().eval().nodes();
		AtlasSet<Node> currentSelection = new AtlasHashSet<Node>(selection);
		AtlasSet<Node> result = new AtlasHashSet<Node>();
		for(Node node : currentSelection){
			for(String tag : tags){
				if(node.taggedWith(tag)){
					result.add(node);
					break;
				}
			}
		}
		return result;
	}

	@Override
	public void setFocus() {}

	@Override
	public void selectionChanged(Graph selection) {}

	@Override
	public void indexBecameUnaccessible() {
		for(CTabItem tab : VIEW.pcgFolder.getItems()) {
			String tabName = tab.getText();
			pcgs.remove(tabName);
			tab.dispose();
		}
	}

	@Override
	public void indexBecameAccessible() {
		int PCG_NUMBER = 1;
		String PCG_NAME = getUniqueName("PCG" + PCG_NUMBER);
		PCGComponents pcg = new PCGComponents(PCG_NAME);
		pcgs.put(PCG_NAME, pcg);
		addPCG(pcgFolder, pcg);
	}
	
}