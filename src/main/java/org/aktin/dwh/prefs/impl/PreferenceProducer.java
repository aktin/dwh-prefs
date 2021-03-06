package org.aktin.dwh.prefs.impl;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import org.aktin.Preference;
import org.aktin.Preferences;
import org.aktin.dwh.PreferenceKey;

public class PreferenceProducer {

	@Inject
	private Preferences prefs;

	@Produces
	@Preference(key=PreferenceKey.commonName)
	public String getPreferenceString(InjectionPoint p){
		PreferenceKey key = p.getAnnotated().getAnnotation(Preference.class).key();
		return prefs.get(key.key());
	}
}
