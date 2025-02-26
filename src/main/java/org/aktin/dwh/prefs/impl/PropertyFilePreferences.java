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
 * 
 *
 */
@Singleton
public class PropertyFilePreferences implements Preferences {
	private static final Logger log = Logger.getLogger(PropertyFilePreferences.class.getName());
	private Properties props;
	private final Path aktinPropertiesFilepath = Paths.get(System.getProperty("jboss.server.config.dir"), "aktin.properties");

	private WildflyGuardian guard = new WildflyGuardian(this.getBackupPath().toString(), this.aktinPropertiesFilepath.toString());

	public PropertyFilePreferences() throws IOException {
		// load preferences (call load(default file)
		try( Reader in = Files.newBufferedReader(this.aktinPropertiesFilepath, StandardCharsets.UTF_8)){
			load(in);
		}
	}

	public PropertyFilePreferences(InputStream properties) throws IOException{
		try( Reader r = new InputStreamReader(properties, StandardCharsets.UTF_8) ){
			load(r);
		}
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
	 * Iterates the current aktin.properties file and updates the values. Then overwrites the original file.
	 * Returns a String that contains an error message if file could not be loaded or changed,
	 * otherwise return is empty String.
	 * @param newProps
	 * @return String
	 */
	public String updatePropertiesFile(Map<String, String> newProps) throws IOException, InterruptedException {
		WildflyGuardian guard = new WildflyGuardian(getBackupPath().toString(), this.aktinPropertiesFilepath.toString());
		guard.createBackup();
		List<String> lines;
		try {
			lines = Files.readAllLines(this.aktinPropertiesFilepath);
		} catch (IOException e) {
            return "ERR: properties file could not be loaded at: "+String.valueOf(this.aktinPropertiesFilepath);
        }
        List<String> modifiedLines = new ArrayList<>();

		// Update values from property file
		for (String line: lines) {
			String[] keyValue = line.split("=", 2);
			if (keyValue.length == 2){
				String key = keyValue[0];
				String value = keyValue[1];
				if (!line.startsWith("#") && !key.isEmpty() && newProps.containsKey(key)) {
					line = line.replace(value, newProps.get(key));
				}
				modifiedLines.add(line);
			}
		}

		// Overwrite properties file with new values
		try {
			if(!modifiedLines.isEmpty()) {
				Files.write(this.aktinPropertiesFilepath, modifiedLines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		} catch (IOException e) {
			return "ERR: properties file could not be overwritten: "+String.valueOf(this.aktinPropertiesFilepath);
		}
		String restart = this.getGuard().restartWildflyService();

		return restart;
	}

	private String applyChanges(WildflyGuardian guard) throws IOException, InterruptedException {
		return guard.restartWildflyService();
//		guard.start();
//		String target = "src/main/java/org/aktin/dwh/prefs/impl/WildflyGuardian.java";
//		ProcessBuilder pb = new ProcessBuilder("java", "cp", target);
//		pb.environment().put("BACKUP_PATH", getBackupPath().toString());
//		pb.environment().put("ACTIVE_PATH", this.aktinPropertiesFilepath.toString());
//		pb.start();
	}

	private Path getBackupPath() {
		return Paths.get(String.valueOf(this.aktinPropertiesFilepath.getParent()), "backup.txt");
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

}
