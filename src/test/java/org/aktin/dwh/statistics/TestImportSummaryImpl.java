package org.aktin.dwh.statistics;


import javax.xml.bind.JAXB;

import org.aktin.dwh.statistics.ImportSummaryImpl;
import org.junit.Assert;
import org.junit.Test;

public class TestImportSummaryImpl {

	@Test
	public void marshallImportSummary(){
		ImportSummaryImpl s = new ImportSummaryImpl();
		s.addCreated("X");
		s.addRejected("X", false, "Test");
		s.addRejected("X", true, "Other");
		for( int i=0; i<20; i++ ){
			s.addRejected("X", false, "Test");			
		}
		s.addRejected("X", true, new AssertionError().toString());
		JAXB.marshal(s, System.out);
	}

	@Test
	public void verifyMaxSizeNotExceeded(){
		ImportSummaryImpl s = new ImportSummaryImpl();
		s.addCreated(null);
		s.addRejected(null, false, "Test");
		for( int i=0; i<s.previousErrors.getMaxSize()+5; i++ ){
			s.addRejected(null, true, "Failed #"+i);
			s.addRejected(null, false, "Test");
		}
		Assert.assertEquals(s.previousErrors.getMaxSize(), s.previousErrors.size());
		// newest error should be "Test"
		Assert.assertEquals("Test", s.getLastRepeatedErrors().get(s.previousErrors.size()-1).message);
	}
}
