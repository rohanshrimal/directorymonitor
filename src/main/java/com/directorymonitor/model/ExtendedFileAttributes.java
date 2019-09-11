package com.directorymonitor.model;

import com.fasterxml.jackson.annotation.JsonView;

public enum ExtendedFileAttributes {
	
	FILE_NAME("name"), FILE_SIZE("size"), CREATION_DATE("creationDate"), LAST_ACCESS_DATE("lastAccessDate"), LAST_MODIFIED_DATE("dateModified"), FILE_PATH("path"), FILE_EXTENSION("extension"), FILE_TOKENS("tokens");
	
	@JsonView(Views.Public.class)
	private String customName;
	
	private ExtendedFileAttributes(String customName) {
		this.customName = customName;
	}
	public String getCustomName() {
        return this.customName;
    }
}