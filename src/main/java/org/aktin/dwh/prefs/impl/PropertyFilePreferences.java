package org.aktin.dwh.prefs.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;
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
public class PropertyFilePreferences implements Preferences{
	private static final Logger log = Logger.getLogger(PropertyFilePreferences.class.getName());
	private Properties props;

	public PropertyFilePreferences() throws IOException {
		// load preferences (call load(default file)
		Path propFile = Paths.get(System.getProperty("jboss.server.config.dir"), "aktin.properties");
		try( Reader in = Files.newBufferedReader(propFile, StandardCharsets.UTF_8)){
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
