package org.aktin.dwh.prefs.impl;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;

import org.aktin.prefs.Preference;
import org.aktin.prefs.Preferences;

/**
 * Implements the AKTIN preferences interface
 * and reads/stores the preferences in a database table
 * 
 *
 */
@Singleton
public class JdbcPreferences implements Preferences, Closeable{

	public JdbcPreferences() {
		// get database connection
		// prepare connections
		// load preferences
	}
	
	@PreDestroy
	@Override
	public void close() throws IOException {
		// write (changed/all) preferences
		// close database connection
	}

	@Override
	public String getString(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getInteger(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void putString(String key, String value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void putInteger(String key, Integer value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Preference<?> get(String key) {
		// TODO Auto-generated method stub
		return null;
	}

}
