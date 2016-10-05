package org.aktin.dwh.prefs.impl;

import java.io.IOException;
import java.io.InputStream;

import org.aktin.Preferences;
import org.aktin.dwh.PreferenceKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestPreferences {
	private PropertyFilePreferences prefs;
	
	@Before
	public void loadTestPrefs() throws IOException{
		try( InputStream in = getClass().getResourceAsStream("/aktin.properties") ){
			prefs = new PropertyFilePreferences(in);
		}
	}
	
	@Test
	public void verifyMandatoryPreferenceKeys() throws IOException{
		for( PreferenceKey pref : PreferenceKey.values() ){
			String value = prefs.get(pref.key());
			Assert.assertNotNull("Preference entry expected for key "+pref.key(), value);
		}
	}

	public Preferences getPreferences(){
		return prefs;
	}

	public static Preferences getTestPreferences() throws IOException{
		TestPreferences p = new TestPreferences();
		p.loadTestPrefs();
		return p.getPreferences();
	}
}
