package org.aktin.dwh.anon;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.ejb.Stateless;

import org.aktin.dwh.Anonymizer;

// technically, singleton is not needed,
// but we need some bean qualifier to allow external dependencies
// to inject this class
@Stateless
public class OneWayAnonymizer implements Anonymizer {

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
	 * @throws DigestException error calculating message digest 
	 */
	@Override
	public String calculateAbstractPseudonym(String ...strings) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// should not happen. SHA-1 is guaranteed to be included in the JRE
			throw new IllegalStateException("Digest algorithm not available",e);
		}
		// join arguments
		String composite = String.join("/", strings);
		// logging
		// encode to bytes
		ByteBuffer input = Charset.forName("UTF-8").encode(composite);
		// calculate digest and encode with base64
		digest.update(input);
		String result = Base64.getUrlEncoder().encodeToString(digest.digest());
		return result;
	}

}
