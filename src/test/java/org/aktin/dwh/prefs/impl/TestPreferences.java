package org.aktin.dwh.prefs.impl;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.aktin.dwh.PreferenceKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestPreferences {
	private PropertyFilePreferences prefs;
	
	@Before
	public void loadTestPrefs() throws SQLException, IOException{
		try( InputStream in = getClass().getResourceAsStream("/aktin.properties") ){
			prefs = new PropertyFilePreferences(in);
		}
	}
	
	@Test
	public void verifyMandatoryPreferenceKeys() throws SQLException, IOException{
		for( PreferenceKey key : PreferenceKey.values() ){
			Assert.assertNotNull(prefs.get(key.key()),"Preference entry expected for key "+key);
		}
	}
}
