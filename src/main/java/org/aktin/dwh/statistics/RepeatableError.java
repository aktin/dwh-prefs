package org.aktin.dwh.statistics;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

@XmlAccessorType(XmlAccessType.NONE)
public class RepeatableError {

	private int count;
	@XmlValue
	String message;

	private long timestamp;

	public RepeatableError(String message, long timestamp){
		this.message = message;
		this.count = 1;
		this.timestamp = timestamp;
	}

	public void incrementCount(long timestamp){
		this.count ++;
		this.timestamp = timestamp;
	}

	@XmlAttribute
	public Integer getRepeats(){
		if( count == 1 ){
			return null;
		}else{
			return count;
		}
	}
	@XmlAttribute
	public Date getTimestamp(){
		return new Date(timestamp);
	}
}
