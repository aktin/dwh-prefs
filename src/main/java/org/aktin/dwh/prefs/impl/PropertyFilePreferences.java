package org.aktin.dwh.prefs.impl;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Singleton;
import org.aktin.Preferences;
import org.aktin.dwh.PreferenceKey;

/**
 * Implements the AKTIN preferences interface and reads the
 * AKTIN preferences from a properties file 'aktin.properties'
 * in the application server configuration directory on startup.
 */
@Singleton
public class PropertyFilePreferences implements Preferences {
	private static final Logger log = Logger.getLogger(PropertyFilePreferences.class.getName());
	private Properties props;
	private final Path aktinPropertiesFilepath;

	private final WildflyGuardian guard;

	public PropertyFilePreferences() throws IOException {
		// load preferences (call load(default file)
		Path propFile = Paths.get(System.getProperty("jboss.server.config.dir"), "aktin.properties");
		try( Reader in = Files.newBufferedReader(propFile, StandardCharsets.UTF_8)){
			load(in);
		}
		this.aktinPropertiesFilepath = propFile;
		this.guard = new WildflyGuardian(this.aktinPropertiesFilepath.toString());
	}

	public PropertyFilePreferences(InputStream properties) throws IOException{
		Path propFile = Paths.get(System.getProperty("jboss.server.config.dir"), "aktin.properties");
		try( Reader in = new InputStreamReader(properties, StandardCharsets.UTF_8) ){
			load(in);
		}
		this.aktinPropertiesFilepath = propFile;
		this.guard = new WildflyGuardian(this.aktinPropertiesFilepath.toString());
	}

	public static PropertyFilePreferences empty(){
		try {
			return new PropertyFilePreferences(new ByteArrayInputStream(new byte[0]));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	private void load(Reader properties) throws IOException{
		props = new Properties();
		props.load(properties);
		// check for missing properties which can be filled automatically
		if( !props.containsKey(PreferenceKey.serverUrl.key()) ){
			// generate server URL
			String url = determineServerURL();
			log.warning("Server URL undefined. Guessing: "+url);
			props.setProperty(PreferenceKey.serverUrl.key(), url);
		}
	}
	private String determineServerURL(){
		InetAddress addr;
		try {
			addr = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			log.log(Level.WARNING,"Unable to retrieve local host address",e);
			addr = InetAddress.getLoopbackAddress();
		}

		if( addr.isLoopbackAddress() ){
			// try to find a different non-loopback address
			Enumeration<NetworkInterface> nics;
			try {
				nics = NetworkInterface.getNetworkInterfaces();
			} catch (SocketException e) {
				log.log(Level.WARNING,"Unable list network interfaces to find local address",e);
				nics = Collections.emptyEnumeration();
			}
			boolean foundOne = false;
			while( nics.hasMoreElements() ){
				NetworkInterface nic = nics.nextElement();
				Enumeration<InetAddress> ias = nic.getInetAddresses();
				while( ias.hasMoreElements() ){
					addr = ias.nextElement();
					if( !addr.isLoopbackAddress() ){
						foundOne = true;
						break;
					}
				}
				if( foundOne ){
					break;
				}
			}
		}
		return "http://"+addr.getHostAddress()+"/";
	}

	/**
	 * Receives a List of key value pairs of updated preference properties.
	 * Iterates the current aktin.properties file and updates the values, then overwrites the original file.
	 * Returns a String that contains an error message if file could not be loaded or changed,
	 * otherwise return is response of the wildfly restart response.
	 * @param newProps
	 * @return String
	 */
	public String updatePropertiesFile(Map<String, String> newProps) throws IOException {
		// Copy the current property file to a backup space
		this.getGuard().createBackup();
		Properties properties = this.getProperties();
		// Update the values of the current properties with new values from "newProps"
		for (Map.Entry<String, String> entry : newProps.entrySet()) {
			properties.setProperty(entry.getKey(), entry.getValue());
		}
		// Store new configuration in the active properties filepath
		try(FileWriter writer = new FileWriter(String.valueOf(this.aktinPropertiesFilepath))) {
			properties.store(writer,"Updated Properties");
		}
		return this.getGuard().restartWildflyService();	// Restart wildfly to apply the new properties
	}

	/**
	 * This method instructs the wildfly guardian to restore the last backup of the properties configuration.
	 * It automatically restarts the wildfly service.
	 * @throws IOException
	 */
	public void loadBackupFile() throws IOException {
		this.getGuard().rollbackToLastVersion();
	}

	/**
	 * This method instructs the wildfly guardian to restore a specific backup version of the properties configuration.
	 * It automatically restarts the wildfly service.
	 * @param path - Path to the specific backup file the user wants to restore
	 * @throws IOException
	 */
	public void loadBackupFile(Path path) throws IOException {
		this.getGuard().rollbackToSpecificVersion(path);
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

	public WildflyGuardian getGuard() {
		return this.guard;
	}

	private Properties getProperties() {
		return this.props;
	}

}
