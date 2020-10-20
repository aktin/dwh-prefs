package org.aktin.dwh.anon;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.aktin.Preferences;
import org.aktin.dwh.Anonymizer;
import org.aktin.dwh.PreferenceKey;

// technically, singleton is not needed,
// but we need some bean qualifier to allow external dependencies
// to inject this class
@Stateless
public class OneWayAnonymizer implements Anonymizer {

	@Inject
	private Preferences prefs;
	/**
	 * Calculate a one way hash function for the given input.
	 * The algorithm is as follows:
	 * <ol>
	 *  <li>Concatenate the arguments with a slash (/) as separator.</li>
	 *  <li>Encode the input arguments with UTF-8 encoding
	 *  <li>Generate a 160bit SHA-1 checksum</li>
	 *  <li>Produce bas64 encoding with url-safe alphabet</li>
	 * </ol>
	 * The resulting string length will be less than 30 characters.
	 * 
	 * @param strings input
	 * @return string hash
	 * @throws IllegalStateException e.g. wrapped NoSuchAlgorithmException if the algorithm is not available 
	 */
	@Override
	public String calculateAbstractPseudonym(String ...strings) throws IllegalStateException{
		MessageDigest digest;
		String algo = prefs.get(PreferenceKey.pseudonymAlgorithm);
		if( algo == null ){
			// default to SHA-1
			algo = "SHA-1";
		}
		try {
			digest = MessageDigest.getInstance(algo);
		} catch (NoSuchAlgorithmException e) {
			// should not happen. SHA-1 is guaranteed to be included in the JRE
			throw new IllegalStateException("Digest algorithm not available",e);
		}
		String salt = prefs.get(PreferenceKey.pseudonymSalt);
		if( salt == null ){
			salt = ""; // default to no salt
		}
		// join arguments
		String composite = salt + String.join("/", strings);
		// logging
		// encode to bytes
		ByteBuffer input = Charset.forName("UTF-8").encode(composite);
		// calculate digest and encode with base64
		digest.update(input);
		String result = Base64.getUrlEncoder().encodeToString(digest.digest());
		return result;
	}

}
