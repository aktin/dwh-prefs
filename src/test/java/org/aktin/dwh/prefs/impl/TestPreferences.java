package org.aktin.dwh.prefs.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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

	@Test
	public void verifyHostnameGuessing() throws IOException{
		String value = prefs.get(PreferenceKey.serverUrl);
		Assert.assertNotNull(value);
	}

	@Test
	public void verifyPrefixedPreferenceLists() {
		List<String> keys = new ArrayList<>();
		List<String> values = new ArrayList<>();
		int count = prefs.forPrefix("mail.", (k,v) -> {keys.add(k); values.add(v);} );
		Assert.assertEquals(10, count);
		Assert.assertEquals(count, keys.size());
		for( int i=0; i<keys.size(); i++ ) {
			String key = keys.get(i);
			Assert.assertEquals(prefs.get(key), values.get(i));
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
