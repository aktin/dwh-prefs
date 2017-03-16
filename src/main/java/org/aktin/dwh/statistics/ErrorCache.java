package org.aktin.dwh.statistics;

import java.util.LinkedHashMap;

/**
 * Error cache to store the most recent error messages. Repeating errors
 * are counted.
 * @author R.W.Majeed
 *
 */
public class ErrorCache extends LinkedHashMap<String,RepeatableError> {
	private static final long serialVersionUID = 1L;
	private int maxSize;

	public ErrorCache(int maxSize){
		super(maxSize, maxSize, true);
		this.maxSize = maxSize;
	}
	public void add(String errorMessage, long timestamp){
		RepeatableError e = this.get(errorMessage);
		if( e == null ){
			e = new RepeatableError(errorMessage, timestamp);
			this.put(errorMessage, e);
		}else{
			e.incrementCount(timestamp);
		}
	}

	@Override
	protected boolean removeEldestEntry(java.util.Map.Entry<String, RepeatableError> eldest) {
		return size() > maxSize;
	}

	public int getMaxSize(){
		return maxSize;
	}
}
