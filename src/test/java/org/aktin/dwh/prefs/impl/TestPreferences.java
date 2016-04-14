package org.aktin.dwh.prefs.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.aktin.dwh.PreferenceKeys;
import org.aktin.dwh.db.TestDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestPreferences {
	private Connection dbc;
	
	@Before
	public void openDatabase() throws SQLException{
		dbc = TestDatabase.createTestConnection();
	}
	
	@Test
	public void testSomething() throws SQLException, IOException{
		JdbcPreferences prefs = new JdbcPreferences();
		prefs.load(dbc);
		prefs.putString(PreferenceKeys.TLS_KEYSTORE_PATH, "test/path/keystore.pkcs12");
		// TODO: check if the preference can be retrieved again
		prefs.write(dbc);
		prefs.close();
	}
	@After
	public void closeDatabase() throws SQLException{
		dbc.close();
	}
}
