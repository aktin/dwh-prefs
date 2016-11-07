package org.aktin.dwh.prefs.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

import javax.inject.Singleton;

import org.aktin.Preferences;

/**
 * Implements the AKTIN preferences interface and reads the
 * AKTIN preferences from a properties file 'aktin.properties'
 * in the application server configuration directory on startup.
 * 
 *
 */
@Singleton
public class PropertyFilePreferences implements Preferences{

	private Properties props;

	public PropertyFilePreferences() throws IOException {
		// load preferences (call load(default file)
		Path propFile = Paths.get(System.getProperty("jboss.server.config.dir"), "aktin.properties");
		try( InputStream in = Files.newInputStream(propFile) ){
			load(in);
		}
	}

	public PropertyFilePreferences(InputStream properties) throws IOException{
		load(properties);
	}

	public static PropertyFilePreferences empty(){
		try {
			return new PropertyFilePreferences(new ByteArrayInputStream(new byte[0]));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	private void load(InputStream properties) throws IOException{
		props = new Properties();
		props.load(properties);
		
	}
	@Override
	public String get(String key) {
		return props.getProperty(key);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Set<String> keySet() {
		return (Set)props.keySet();
	}

	public void put(String key, String value){
		props.put(key, value);
	}
}
