package com.ensoftcorp.open.pcg.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.part.ViewPart;

import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;
import com.ensoftcorp.open.pcg.common.HighlighterUtils;
import com.ensoftcorp.open.pcg.common.PCG;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

public class PCGLogView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.ensoftcorp.open.pcg.ui.pcgLogView";
	
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss MM-dd-yyyy");
	
	private ArrayList<PCG> pcgs = new ArrayList<PCG>();
	
	private Table table;
	private Button showButton;
	private Button deleteButton;
	
	// default sort descending by last accessed time
	private boolean sortAscending = false;
	private Comparator<PCG> tableSorter = new LastAccessedComparator();
	
	private static class LastAccessedComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			long pcg1LastAccessTime;
			try {
				pcg1LastAccessTime = pcg1.getLastAccessTime();
			} catch (Exception e){
				pcg1LastAccessTime = -1;
			}
			
			long pcg2LastAccessTime;
			try {
				pcg2LastAccessTime = pcg2.getLastAccessTime();
			} catch (Exception e){
				pcg2LastAccessTime = -1;
			}
			
			return Long.compare(pcg1LastAccessTime, pcg2LastAccessTime);
		}
	};
	
	private static class CreationComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			long pcg1CreationTime;
			try {
				pcg1CreationTime = pcg1.getCreationTime();
			} catch (Exception e){
				pcg1CreationTime = -1;
			}
			
			long pcg2CreationTime;
			try {
				pcg2CreationTime = pcg2.getCreationTime();
			} catch (Exception e){
				pcg2CreationTime = -1;
			}
			
			return Long.compare(pcg1CreationTime, pcg2CreationTime);
		}
	};
	
	private static class GivenNameComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			
			String pcg1GivenName;
			try {
				pcg1GivenName = pcg1.getGivenName();
			} catch (Exception e){
				pcg1GivenName = "";
			}
			
			String pcg2GivenName;
			try {
				pcg2GivenName = pcg2.getGivenName();
			} catch (Exception e){
				pcg2GivenName = "";
			}
			
			return pcg1GivenName.compareTo(pcg2GivenName);
		}
	};
	
	private static class FunctionNameComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			String pcg1QualifiedFunctionName;
			try {
				pcg1QualifiedFunctionName = CommonQueries.getQualifiedFunctionName(pcg1.getFunction());
			} catch (Exception e){
				pcg1QualifiedFunctionName = "";
			}
			
			String pcg2QualifiedFunctionName;
			try {
				pcg2QualifiedFunctionName = CommonQueries.getQualifiedFunctionName(pcg2.getFunction());
			} catch (Exception e){
				pcg2QualifiedFunctionName = "";
			}
			
			return pcg1QualifiedFunctionName.compareTo(pcg2QualifiedFunctionName);
		}
	};
	
	private static class NumberEventsComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			long pcg1Events;
			try {
				pcg1Events = pcg1.getEvents().eval().nodes().size();
			} catch (Exception e){
				pcg1Events = -1;
			}
			
			long pcg2Events;
			try {
				pcg2Events = pcg2.getEvents().eval().nodes().size();
			} catch (Exception e){
				pcg2Events = -1;
			}
			
			return Long.compare(pcg1Events, pcg2Events);
		}
	};

	/**
	 * The constructor.
	 */
	public PCGLogView() {
		updateTable();
	}

	private void updateTable() {
		if(table != null){
			// reloading table clears selections
			showButton.setEnabled(false);
			deleteButton.setEnabled(false);
			
			// remove all rows
			table.removeAll();
			
			Set<PCG> instances = PCG.getInstances();
			pcgs = new ArrayList<PCG>(instances);
			
			if(sortAscending){
				pcgs.sort(tableSorter);
			} else {
				pcgs.sort(tableSorter.reversed());
			}

	        for (PCG pcg : pcgs) {
	        	TableItem item = new TableItem(table, SWT.NONE);
	        	item.setData("pcg", pcg);
	        	
	        	// set given name
	        	try {
					item.setText(0, pcg.getGivenName());
				} catch (Exception e){
					item.setBackground(0, Display.getDefault().getSystemColor(SWT.COLOR_RED));
					item.setText(0, "Error");
				}
	        	
	        	// set qualified function name
	        	try {
					item.setText(1, CommonQueries.getQualifiedFunctionName(pcg.getFunction()));
				} catch (Exception e){
					item.setBackground(1, Display.getDefault().getSystemColor(SWT.COLOR_RED));
					item.setText(1, "Error");
				}
				
	        	// set number of events
				try {
					item.setText(2, "" + pcg.getEvents().eval().nodes().size());
				} catch (Exception e){
					item.setBackground(2, Display.getDefault().getSystemColor(SWT.COLOR_RED));
					item.setText(2, "Error");
				}
				
				// set last access time
				try {
					item.setText(3, "" + dateFormat.format(new Date(pcg.getLastAccessTime())));
				} catch (Exception e){
					item.setBackground(3, Display.getDefault().getSystemColor(SWT.COLOR_RED));
					item.setText(3, "Error");
				}
				
				// set creation time
				try {
					item.setText(4, "" + dateFormat.format(new Date(pcg.getCreationTime())));
				} catch (Exception e){
					item.setBackground(4, Display.getDefault().getSystemColor(SWT.COLOR_RED));
					item.setText(4, "Error");
				}
	        }
	        
	        resizeTable(table);
		}
	}
	
	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));
		
		Group tableSelectionControlsGroup = new Group(parent, SWT.NONE);
		tableSelectionControlsGroup.setLayout(new GridLayout(2, false));
		tableSelectionControlsGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		tableSelectionControlsGroup.setText("Table Selection Controls");
		
		showButton = new Button(tableSelectionControlsGroup, SWT.NONE);
		showButton.setText("Show");
		
		showButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					if(table.getSelectionCount() == 1){
						TableItem selection = table.getSelection()[0];
						PCG pcg = (PCG) selection.getData("pcg");
						String givenName = pcg.getGivenName();
						String title = givenName.equals("") ? CommonQueries.getQualifiedFunctionName(pcg.getFunction()) : givenName;
						Markup markup = new Markup();
						markup.setNode(pcg.getEvents(), MarkupProperty.NODE_BACKGROUND_COLOR, java.awt.Color.CYAN);
						HighlighterUtils.applyHighlightsForCFEdges(markup);
						pcg.setUpdateLastAccessTime();
						DisplayUtils.show(pcg.getPCG(), markup, true, title);
					} else {
						DisplayUtils.showError("Please make a selection in the table.");
					}
				} catch (Exception ex){
					DisplayUtils.showError(ex, "Error loading PCG");
				}
			}
		});
		showButton.setEnabled(false);
		
		deleteButton = new Button(tableSelectionControlsGroup, SWT.NONE);
		deleteButton.setText("Delete");
		
		deleteButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					DisplayUtils.showMessage("Not Implemented");
				} catch (Exception ex){
					DisplayUtils.showError(ex, "Error loading PCG");
				}
			}
		});
		deleteButton.setEnabled(false);

		table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		TableColumn givenNameColumn = new TableColumn(table, SWT.NONE);
		givenNameColumn.setWidth(100);
		givenNameColumn.setText("Given Name");
		
		TableColumn functionNameColumn = new TableColumn(table, SWT.NONE);
		functionNameColumn.setWidth(100);
		functionNameColumn.setText("Function");
		
		TableColumn numberEventsColumn = new TableColumn(table, SWT.NONE);
		numberEventsColumn.setWidth(100);
		numberEventsColumn.setText("# Events");
		
		TableColumn lastAccessedColumn = new TableColumn(table, SWT.NONE);
		lastAccessedColumn.setWidth(100);
		lastAccessedColumn.setText("Last Accessed");
		
		TableColumn creationTimeColumn = new TableColumn(table, SWT.NONE);
		creationTimeColumn.setWidth(100);
		creationTimeColumn.setText("Created");
		
		Listener sortListener = new Listener() {
            public void handleEvent(Event e) {
                TableColumn column = (TableColumn) e.widget;
                if (column == givenNameColumn) {
                	if(tableSorter instanceof GivenNameComparator){
                		sortAscending = !sortAscending;
                	} else {
                		// default sort ascending
                		sortAscending = true;
                	}
                	tableSorter = new GivenNameComparator();
                }
                if (column == functionNameColumn) {
                	if(tableSorter instanceof FunctionNameComparator){
                		sortAscending = !sortAscending;
                	} else {
                		// default sort ascending
                		sortAscending = true;
                	}
                	tableSorter = new FunctionNameComparator();
                }
                if (column == numberEventsColumn) {
                	if(tableSorter instanceof NumberEventsComparator){
                		sortAscending = !sortAscending;
                	} else {
                		// default sort descending
                		sortAscending = false;
                	}
                	tableSorter = new NumberEventsComparator();
                }
                if (column == lastAccessedColumn) {
                	if(tableSorter instanceof LastAccessedComparator){
                		sortAscending = !sortAscending;
                	} else {
                		// default sort descending
                		sortAscending = false;
                	}
                	tableSorter = new LastAccessedComparator();
                }
                if (column == creationTimeColumn) {
                	if(tableSorter instanceof CreationComparator){
                		sortAscending = !sortAscending;
                	} else {
                		// default sort descending
                		sortAscending = false;
                	}
                	tableSorter = new CreationComparator();
                }
                table.setSortColumn(column);
                table.setSortDirection(sortAscending ? SWT.UP : SWT.DOWN);
                updateTable();
            }
        };
        givenNameColumn.addListener(SWT.Selection, sortListener);
        functionNameColumn.addListener(SWT.Selection, sortListener);
        numberEventsColumn.addListener(SWT.Selection, sortListener);
        lastAccessedColumn.addListener(SWT.Selection, sortListener);
        creationTimeColumn.addListener(SWT.Selection, sortListener);
        
        table.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// selection was just made or changed
				showButton.setEnabled(true);
				deleteButton.setEnabled(true);
			}
		});
	}
	
	private static void resizeTable(Table table) {
		for (TableColumn column : table.getColumns()) {
			column.pack();
		}
	}

	@Override
	public void setFocus() {
		// intentionally left blank
	}
}