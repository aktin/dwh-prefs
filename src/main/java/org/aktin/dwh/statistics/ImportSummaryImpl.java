package org.aktin.dwh.statistics;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Singleton;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.aktin.dwh.ImportSummary;

/**
 * Count statistics
 * @author marap1
 *
 */
@Singleton
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name="import-statistics")
@XmlType(propOrder={"start","lastWrite","lastFailure","importedCount","updatedCount","invalidCount","failedCount","lastRepeatedErrors"})
public class ImportSummaryImpl implements ImportSummary{

	/** Timestamp since when the summary is logging
	 * (or when it was last cleared/deleted)
	 */
	protected long since;
	protected Long lastErrorTime;
	protected Long lastImportTime;
	// all fields are used by JAXB
	protected int numValidationFailed;
	protected int numRejected;
	protected int numCreated;
	protected int numUpdated;

	// error cache (protected for unit tests)
	protected ErrorCache previousErrors;

	public ImportSummaryImpl(){
		since = System.currentTimeMillis();
		previousErrors = new ErrorCache(20);
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

	// TODO count repeating errors without adding duplicates to the list. e.g. via ordered hash map
	
	@Override
	public synchronized void addRejected(String templateId, boolean valid, String error){
		if( valid == false ){
			numValidationFailed ++;
		}else{
			numRejected ++;
		}
		this.lastErrorTime = System.currentTimeMillis();
		previousErrors.add(error, this.lastErrorTime);
	}

	@Override
	public void addCreated(String templateId){
		this.lastImportTime = System.currentTimeMillis();
		this.numCreated ++;
	}
	@Override
	public void addUpdated(String templateId){
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
		return numRejected;
	}


	@Override
	@XmlElement(name="invalid")
	public int getInvalidCount() {
		return numValidationFailed;
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


	@XmlElementWrapper(name="last-errors")
	@XmlElement(name="error")
	public synchronized List<RepeatableError> getLastRepeatedErrors() {
		List<RepeatableError> l = new ArrayList<>(previousErrors.values());
		return l;
	}

	@Override
	public synchronized Iterable<String> getLastErrors() {
		List<String> l = new ArrayList<>(previousErrors.size());
		previousErrors.forEach( (a,b) -> l.add(a) );
		return l;
	}
}
