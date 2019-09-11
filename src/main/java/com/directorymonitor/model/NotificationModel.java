package com.directorymonitor.model;

public class NotificationModel {
	
	private String type;
	private String message;
	private long timestamp;
	
	public NotificationModel(String message, long timstamp) {
		this.message = message;
		this.timestamp = timstamp;
	}
	
	public NotificationModel(String type ,String message, long timstamp) {
		this.type = type;
		this.message = message;
		this.timestamp = timstamp;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
