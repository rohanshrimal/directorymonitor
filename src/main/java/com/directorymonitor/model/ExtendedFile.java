package com.directorymonitor.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;

public class ExtendedFile extends File {

	private static final long serialVersionUID = -6413937750386078478L;

	@JsonView(Views.Public.class)
	private String name;

	@JsonView(Views.Public.class)
	private long size;

	@JsonView(Views.Public.class)
	private long creationDate;

	@JsonView(Views.Public.class)
	private long lastAccessDate;

	@JsonView(Views.Public.class)
	private long lastModifiedDate;

	@JsonView(Views.Public.class)
	private String path;

	@JsonView(Views.Public.class)
	private String extension;
	
	@JsonView(Views.Public.class)
	private boolean directory;
	
	@JsonView(Views.Public.class)
	private String parent;
	
	private int countOfFiles;
	
	private int countOfFolders;
	
	private Map<String, Integer> tokens; 

	private Map<String, ExtendedFile> filesAndSubDirectories;

	public ExtendedFile(String path) throws IOException {
		super(path);
		BasicFileAttributes attributes = null;

		if (super.exists() && super.canRead()) {
			attributes = Files.readAttributes(super.toPath(), BasicFileAttributes.class);
			this.name = super.getName();
			this.path = super.getPath();
			this.size = super.length();
			this.directory = super.isDirectory();
			this.setExtension();
			this.creationDate = attributes.creationTime().toMillis();
			this.lastAccessDate = attributes.lastAccessTime().toMillis();
			this.lastModifiedDate = attributes.lastModifiedTime().toMillis();
			this.parent = super.getParent(); 
			
			if(!this.isDirectory()){
				tokens = new HashMap<>();
			}
			if(this.isDirectory()){
				this.size = 0;
			}
		}
		else {
			System.out.println("ERROR: File or Directory Not Found. "+this.getPath());
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public void setCreationDate(long creationDate) {
		this.creationDate = creationDate;
	}

	public void setLastAccessDate(long lastAccessDate) {
		this.lastAccessDate = lastAccessDate;
	}

	public void setLastModifiedDate(long lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		if(this.isDirectory()){
			this.size = 0;
			if(filesAndSubDirectories != null) {
				for(Map.Entry<String, ExtendedFile> eachChildEntry : filesAndSubDirectories.entrySet()){
					this.size = this.size + eachChildEntry.getValue().getSize();
				}
				return this.size;
			}
			return 0;
		}
		else
		return size;
	}

	public long getCreationDate() {
		return creationDate;
	}

	public long getLastAccessDate() {
		return lastAccessDate;
	}

	public String getExtension() {
		return extension;
	}

	public boolean isExists() {
		return super.exists();
	}

	public String getPath() {
		if(this.path == null) {
			return super.getPath();
		}
		return path;
	}

	public String getFileExtension() {
		return extension;
	}

	public long getLastModifiedDate() {
		return lastModifiedDate;
	}

	public boolean isDirectory() {
		return directory;
	}

	public void setDirectory(boolean directory) {
		this.directory = directory;
	}

	private void setExtension() {
		String extension = "";
		String fileName = this.getName();

		int i = fileName.lastIndexOf('.');
		int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));

		if (i > p) {
			extension = fileName.substring(i + 1);
		}
		this.extension = extension;
	}

	public String getParent() {
		if(this.parent == null) {
			return super.getParent();
		}
		else {
			return this.parent;
		}
	}

	public void setParent(String parent) {
		this.parent = parent;
	}

	public Object getAttributeValue(ExtendedFileAttributes attribute) {
		switch (attribute) {
		case FILE_NAME:
			return this.getName();

		case FILE_SIZE:
			return this.getSize();

		case CREATION_DATE:
			return this.getCreationDate();

		case LAST_ACCESS_DATE:
			return this.getLastAccessDate();

		case LAST_MODIFIED_DATE:
			return this.getLastModifiedDate();

		case FILE_PATH:
			return this.getPath();

		case FILE_EXTENSION:
			return this.getFileExtension();
			
		case FILE_TOKENS:
			return null;
		}
		return null;
	}

	public void setFilesAndSubDirectories(Map<String, ExtendedFile> filesAndSubDirectories) {
		this.filesAndSubDirectories = filesAndSubDirectories;
	}

	public Map<String, ExtendedFile> getFilesAndSubDirectories() {
		return filesAndSubDirectories;
	}

	public void addToFilesAndSubDirectories(String path, ExtendedFile newFileOrDirectory) {
		if (filesAndSubDirectories != null) {
			filesAndSubDirectories.put(path, newFileOrDirectory);
		}
	}

	public void removeFromFilesAndSubDirectories(String path) {
		if (filesAndSubDirectories != null) {
			filesAndSubDirectories.remove(path);
		}
	}

	public void replaceFilesAndSubDirectories(String path, ExtendedFile newFileOrDirectory) {
		if (filesAndSubDirectories != null) {
			filesAndSubDirectories.replace(path, newFileOrDirectory);
		}
	}

	public void incrementTokenCount(String token) {
		if(token!=null && tokens != null && tokens.containsKey(token.intern()) && tokens.get(token.intern())!= null)
		tokens.replace(token.intern(), tokens.get(token.intern())+1);
		
	}
	
	public void addNewToken(String token) {
		if(tokens != null && token != null)
		tokens.put(token.intern(), 1);
	}

	public ExtendedFile getChild(String filePath) {
		return this.filesAndSubDirectories.get(filePath);
	}

	public Map<String, Integer> getTokens() {
		return tokens;
	}

	public void setTokens(Map<String, Integer> tokens) {
		this.tokens = tokens;
	}

	public int getCountOfFiles() {
		return countOfFiles;
	}

	public void setCountOfFiles(int countOfFiles) {
		this.countOfFiles = countOfFiles;
	}

	public int getCountOfFolders() {
		if(this.isDirectory()){
			int countOfFolders = this.countOfFolders;
			if(filesAndSubDirectories != null) {
				for(Map.Entry<String, ExtendedFile> eachChildEntry : filesAndSubDirectories.entrySet()){
					countOfFolders = countOfFolders + eachChildEntry.getValue().getCountOfFolders();
				}
				return countOfFolders;
			}
			return 0;
		}
		return 0;
	}

	public void setCountOfFolders(int countOfFolders) {
		this.countOfFolders = countOfFolders;
	}
	
	public void incCountOfFiles(){
		countOfFiles++;
	}
	
	public void incCountOfFolders(){
		countOfFolders++;
	}
	
	public void decCountOfFiles(){
		countOfFiles--;
	}
	
	public void decCountOfFolders(){
		countOfFolders--;
	}
	
	public void incFolderSize(long size) {
		this.size = this.size + size;
	}
	
	public void decFolderSize(long size) {
		this.size = this.size - size;
	}
}
