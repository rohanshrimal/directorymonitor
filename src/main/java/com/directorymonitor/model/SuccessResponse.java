package com.directorymonitor.model;

import com.fasterxml.jackson.annotation.JsonView;

public class SuccessResponse {
	
	@JsonView(Views.Public.class)
	boolean flag;
	
	@JsonView(Views.Public.class)
	String msg;
	
	public SuccessResponse() {

	}
	
	public SuccessResponse(String msg, boolean flag) {
		this.msg = msg;
		this.flag = flag;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public SuccessResponse(boolean flag) {
		this.flag = flag;
	}

	public boolean isFlag() {
		return flag;
	}

	public void setFlag(boolean flag) {
		this.flag = flag;
	}
	
}
