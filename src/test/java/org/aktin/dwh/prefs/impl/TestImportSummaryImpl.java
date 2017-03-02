package org.aktin.dwh.prefs.impl;


import javax.xml.bind.JAXB;

import org.junit.Test;

public class TestImportSummaryImpl {

	@Test
	public void marshallImportSummary(){
		ImportSummaryImpl s = new ImportSummaryImpl();
		s.addCreated();
		s.addRejected(false, "Test");
		s.addRejected(true, "Other");
		s.addRejected(true, new AssertionError().toString());
		JAXB.marshal(s, System.out);
	}
}
