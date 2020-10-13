package org.aktin.dwh.email;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.junit.Assert;
import org.junit.Test;


public class TestEmailServiceImpl {

	@Test
	public void emptyAdressListShouldProduceEmptyArray() throws AddressException {
		Assert.assertEquals(0,InternetAddress.parse("").length);
		Assert.assertEquals(0,InternetAddress.parse(" ").length);
	}
}
