package com.ensoftcorp.open.pcg.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.ensoftcorp.open.pcg.Activator;
import com.ensoftcorp.open.pcg.log.Log;

public class PCGPreferences extends AbstractPreferenceInitializer {

	/**
	 * Returns the preference store used for these preferences
	 * @return
	 */
	public static IPreferenceStore getPreferenceStore() {
		return Activator.getDefault().getPreferenceStore();
	}
	
	private static boolean initialized = false;
	
	/**
	 * Enable/disable serializing PCG instances into the Atlas graph
	 */
	public static final String SERIALIZE_PCG_INSTANCES = "SERIALIZE_PCG_INSTANCES";
	public static final Boolean SERIALIZE_PCG_INSTANCES_DEFAULT = true;
	private static boolean serializePCGInstancesValue = SERIALIZE_PCG_INSTANCES_DEFAULT;
	
	/**
	 * Configures resource disposal
	 */
	public static void enableSerializePCGInstances(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(SERIALIZE_PCG_INSTANCES, enabled);
		loadPreferences();
	}
	
	public static boolean isSerializePCGInstancesEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return serializePCGInstancesValue;
	}
	
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setDefault(SERIALIZE_PCG_INSTANCES, SERIALIZE_PCG_INSTANCES_DEFAULT);
	}
	
	/**
	 * Restores the default preferences
	 */
	public static void restoreDefaults() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(SERIALIZE_PCG_INSTANCES, SERIALIZE_PCG_INSTANCES_DEFAULT);
		loadPreferences();
	}
	
	/**
	 * Loads or refreshes current preference values
	 */
	public static void loadPreferences() {
		try {
			IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
			serializePCGInstancesValue = preferences.getBoolean(SERIALIZE_PCG_INSTANCES);
		} catch (Exception e){
			Log.warning("Error accessing PCG analysis preferences, using defaults...", e);
		}
		initialized = true;
	}

}
