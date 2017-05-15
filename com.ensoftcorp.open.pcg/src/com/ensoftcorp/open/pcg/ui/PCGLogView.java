package com.ensoftcorp.open.pcg.ui;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
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

public class PCGLogView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.ensoftcorp.open.pcg.ui.pcgLogView";
	
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss MM-dd-yyyy");
	
	private ArrayList<PCG> pcgs = new ArrayList<PCG>();
	
	private Table table;
	private Comparator<PCG> tableSorter = new LastAccessedComparator();
	
	private static class LastAccessedComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			return Long.compare(pcg1.getLastAccessTime(), pcg2.getLastAccessTime());
		}
	};
	
	private static class CreationComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			return Long.compare(pcg1.getCreationTime(), pcg2.getCreationTime());
		}
	};
	
	private static class GivenNameComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			return pcg1.getGivenName().compareTo(pcg2.getGivenName());
		}
	};
	
	private static class FunctionNameComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			return CommonQueries.getQualifiedFunctionName(pcg1.getFunction()).compareTo(CommonQueries.getQualifiedFunctionName(pcg2.getFunction()));
		}
	};
	
	private static class NumberEventsComparator implements Comparator<PCG> {
		@Override
		public int compare(PCG pcg1, PCG pcg2) {
			return Long.compare(pcg1.getEvents().eval().nodes().size(), pcg2.getEvents().eval().nodes().size());
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
			table.removeAll();
			
			Set<PCG> instances = PCG.getInstances();
			pcgs = new ArrayList<PCG>(instances);
			pcgs.sort(tableSorter);
			
	        for (PCG pcg : pcgs) {
	        	TableItem item = new TableItem(table, SWT.NONE);
				item.setText(0, pcg.getGivenName());
				item.setText(1, CommonQueries.getQualifiedFunctionName(pcg.getFunction()));
				item.setText(2, "" + pcg.getEvents().eval().nodes().size());
				item.setText(3, "" + dateFormat.format(new Date(pcg.getLastAccessTime())));
				item.setText(4, "" + dateFormat.format(new Date(pcg.getCreationTime())));
				item.setData(pcg);
	        }
		}
	}
	
	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));

		table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		TableColumn givenNameColumn = new TableColumn(table, SWT.NONE);
		givenNameColumn.setWidth(100);
		givenNameColumn.setText("Given Name");
		
		TableColumn functionNameColumn = new TableColumn(table, SWT.NONE);
		functionNameColumn.setMoveable(true);
		functionNameColumn.setWidth(100);
		functionNameColumn.setText("Function");
		
		TableColumn numberEventsColumn = new TableColumn(table, SWT.NONE);
		numberEventsColumn.setMoveable(true);
		numberEventsColumn.setWidth(100);
		numberEventsColumn.setText("# Events");
		
		TableColumn lastAccessedColumn = new TableColumn(table, SWT.NONE);
		lastAccessedColumn.setWidth(100);
		lastAccessedColumn.setText("Last Accessed");
		
		TableColumn creationTimeColumn = new TableColumn(table, SWT.NONE);
		creationTimeColumn.setMoveable(true);
		creationTimeColumn.setWidth(100);
		creationTimeColumn.setText("Created");
		
		Listener sortListener = new Listener() {
            public void handleEvent(Event e) {
                TableColumn column = (TableColumn) e.widget;
                if (column == givenNameColumn) {
                	tableSorter = new GivenNameComparator();
                }
                if (column == functionNameColumn) {
                	tableSorter = new FunctionNameComparator();
                }
                if (column == numberEventsColumn) {
                	tableSorter = new NumberEventsComparator();
                }
                if (column == lastAccessedColumn) {
                	tableSorter = new LastAccessedComparator();
                }
                if (column == creationTimeColumn) {
                	tableSorter = new CreationComparator();
                }
                table.setSortColumn(column);
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
				PCG pcg = (PCG) e.item.getData();
				String givenName = pcg.getGivenName();
				String title = givenName.equals("") ? CommonQueries.getQualifiedFunctionName(pcg.getFunction()) : givenName;
				Markup markup = new Markup();
				markup.setNode(pcg.getEvents(), MarkupProperty.NODE_BACKGROUND_COLOR, Color.CYAN);
				HighlighterUtils.applyHighlightsForCFEdges(markup);
				DisplayUtils.show(pcg.getPCG(), markup, true, title);
			}
		});
		
	}

	@Override
	public void setFocus() {
		// intentionally left blank
	}
}