package com.ensoftcorp.open.pcg.ui;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.ensoftcorp.open.pcg.Activator;
import com.ensoftcorp.open.pcg.preferences.PCGPreferences;

/**
 * UI for setting PCG analysis preferences
 * 
 * @author Ben Holland
 */
public class PCGPreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private static final String SERIALIZE_PCG_INSTANCES_DESCRIPTION = "Serialize PCG instances into the Atlas graph (required for the PCG Log views to work)";

	private static boolean changeListenerAdded = false;
	
	public PCGPreferencesPage() {
		super(GRID);
	}

	@Override
	public void init(IWorkbench workbench) {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		setPreferenceStore(preferences);
		setDescription("Configure preferences for the PCG Toolbox plugin.");
		
		// use to update cached values if user edits a preference
		if(!changeListenerAdded){
			getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
				@Override
				public void propertyChange(org.eclipse.jface.util.PropertyChangeEvent event) {
					PCGPreferences.loadPreferences();
				}
			});
			changeListenerAdded = true;
		}
	}

	@Override
	protected void createFieldEditors() {
		addField(new BooleanFieldEditor(PCGPreferences.SERIALIZE_PCG_INSTANCES, "&" + SERIALIZE_PCG_INSTANCES_DESCRIPTION, getFieldEditorParent()));
	}
	
}

