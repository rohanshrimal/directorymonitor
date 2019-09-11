package com.directorymonitor.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.directorymonitor.dao.DirectoryMonitorDao;
import com.directorymonitor.model.ExtendedFile;
import com.directorymonitor.model.ExtendedFileAttributes;
import com.directorymonitor.model.SuccessResponse;

@Service
public class DirectoryMonitorService {

	@Autowired
	private DirectoryMonitorDao theDirectoryMonitorDao;
	
	@PostConstruct
	public void startWatcherService() {
		Thread watcherThread = new Thread(theDirectoryMonitorDao);
		watcherThread.start();
	}
	
	public Map<String, ExtendedFile> addPath(ExtendedFile directoryToMonitor) throws IOException {
		return theDirectoryMonitorDao.addPath(directoryToMonitor);
	}

	public Map<String, ExtendedFile> getDirectoryList() {
		return theDirectoryMonitorDao.getDirectoryList();
	}

	public LinkedHashSet<ExtendedFileAttributes> getAttributesToPublish() {
		return theDirectoryMonitorDao.getAttributesToPublish();
	}

	public Map<String, ExtendedFile> getFilesAndSubDirectory(String path) {
		return theDirectoryMonitorDao.getFilesAndSubDirectory(path);
	}

	public Map<String, Integer> getTokenStatistics(String alphabet) {
		return theDirectoryMonitorDao.getTokenStatistics(alphabet);
	}

	public Map<String, Integer> getTokenDetails(String parent, String filePath) {
		return theDirectoryMonitorDao.getTokenDetails(parent, filePath);
	}

	public Map<String, Integer> getTokenFiles(String token) {
		return theDirectoryMonitorDao.getTokenFiles(token);
	}

	public boolean setIndexDirectory(ExtendedFile directoryToStoreIndex) throws IOException {
		return theDirectoryMonitorDao.setIndexDirectory(directoryToStoreIndex);
	}

	public Set<String> getTokenSuggestions(String searchQuery) {
		return theDirectoryMonitorDao.getTokenSuggestions(searchQuery);
	}

	public Map<String, ExtendedFile> getSearchResults(String searchQuery) {
		return theDirectoryMonitorDao.getSearchResults(searchQuery);
	}

	public ArrayList<String> getFileContent(String filePath) {
		return theDirectoryMonitorDao.getFileContent(filePath);
	}

	public Map<String, ExtendedFile> searchFiles(String attribute, String searchQuery, String searchLocation) {
		return theDirectoryMonitorDao.searchFiles(attribute, searchQuery, searchLocation);
	}

	public SuccessResponse addStopWords(String wordList) {
		return theDirectoryMonitorDao.addStopWords(wordList);
	}

	public Set<String> getStopWords() {
		return theDirectoryMonitorDao.getStopWords();
	}

	public long getFileCount(String path) {
		return theDirectoryMonitorDao.getFileCount(path);
	}

	public Integer getFolderCount(String path) {
		return theDirectoryMonitorDao.getFolderCount(path);
	}

	public Long getFolderSize(String path) {
		return theDirectoryMonitorDao.getFolderSize(path);
	}

	public Map<String, List<ExtendedFile>> getFilesByExtension(String path) {
		return theDirectoryMonitorDao.getFilesByExtension(path);
	}

	public Map<String, Integer> getFolderTokens(String path) {
		return theDirectoryMonitorDao.getFolderTokens(path);
	}

	public void downloadAll(String filePaths, HttpServletRequest request, HttpServletResponse response) {
		theDirectoryMonitorDao.downloadAll(filePaths, request, response);
	}

	public List<String> getRootDirectories() {
		return theDirectoryMonitorDao.getRootDirectories();
	}

	public Map<String, String> browseFilesAndSubDirectory(String path) {
		return theDirectoryMonitorDao.browseFilesAndSubDirectory(path);
	}

}
