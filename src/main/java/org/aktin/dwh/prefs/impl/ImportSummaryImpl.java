package org.aktin.dwh.prefs.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.aktin.dwh.ImportSummary;

@Singleton
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name="import-statistics")
@XmlType(propOrder={"start","lastWrite","lastFailure","importedCount","updatedCount","invalidCount","failedCount","lastErrors"})
public class ImportSummaryImpl implements ImportSummary{

	/** Timestamp since when the summary is logging
	 * (or when it was last cleared/deleted)
	 */
	protected long since;
	protected Long lastErrorTime;
	protected Long lastImportTime;
	// all fields are used by JAXB
	protected int maxNumStacktraces;
	protected int numValidationFailed;
	protected int numRejected;
	protected int numCreated;
	protected int numUpdated;

	private Deque<String> previousErrors;

//	private static class ThrowableAdapter extends XmlAdapter<String, Throwable>{
//
//		@Override
//		public Throwable unmarshal(String v) throws Exception {
//			throw new UnsupportedOperationException();
//		}
//
//		@Override
//		public String marshal(Throwable v) throws Exception {
//			StringWriter w = new StringWriter();
//			v.printStackTrace(new PrintWriter(w));
//			return w.toString();
//		}
//		
//	}
	public ImportSummaryImpl(){
		since = System.currentTimeMillis();
		previousErrors = new LinkedList<>();
		maxNumStacktraces = 10;
	}

	@Override
	public void reset(){
		numRejected = 0;
		numValidationFailed = 0;
		numCreated = 0;
		numUpdated = 0;
		this.previousErrors.clear();
		this.since = System.currentTimeMillis();
		this.lastErrorTime = null;
		this.lastImportTime = null;
	}

	@Override
	public synchronized void addRejected(boolean valid, String error){
		if( valid == false ){
			numValidationFailed ++;
		}else{
			numRejected ++;
		}
		if( previousErrors.size() >= maxNumStacktraces ){
			previousErrors.removeFirst();
		}
		previousErrors.addLast(error);
		this.lastErrorTime = System.currentTimeMillis();
	}

	@Override
	public void addCreated(){
		this.lastImportTime = System.currentTimeMillis();
		this.numCreated ++;
	}
	@Override
	public void addUpdated(){
		this.lastImportTime = System.currentTimeMillis();
		this.numUpdated ++;
	}

	@Override
	public long getStartTime() {
		return since;
	}
	@XmlElement
	public Date getStart(){
		return new Date(getStartTime());
	}


	@Override
	public Long getLastWriteTime() {
		return lastImportTime;
	}

	private static Date dateOrNull(Long ms){
		if( ms == null ){
			return null;
		}else{
			return new Date(ms);
		}
	}
	@XmlElement(name="last-write")
	public Date getLastWrite(){
		return dateOrNull(getLastWriteTime());
	}


	@Override
	public Long getLastRejectTime() {
		return lastErrorTime;
	}

	@XmlElement(name="last-reject")
	public Date getLastFailure(){
		return dateOrNull(getLastRejectTime());
	}

	@Override
	@XmlElement(name="failed")
	public int getFailedCount() {
		return numValidationFailed;
	}


	@Override
	@XmlElement(name="invalid")
	public int getInvalidCount() {
		return numRejected;
	}


	@Override
	@XmlElement(name="imported")
	public int getImportedCount() {
		return numCreated;
	}
	@Override
	@XmlElement(name="updated")
	public int getUpdatedCount() {
		return numUpdated;
	}


	@Override
	@XmlElementWrapper(name="last-errors")
	@XmlElement(name="error")
	public List<String> getLastErrors() {
		List<String> l = new ArrayList<>(previousErrors);
		return l;
	}
}
