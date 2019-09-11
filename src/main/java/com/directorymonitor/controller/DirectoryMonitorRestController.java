package com.directorymonitor.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.directorymonitor.model.ExtendedFile;
import com.directorymonitor.model.ExtendedFileAttributes;
import com.directorymonitor.model.SuccessResponse;
import com.directorymonitor.model.Views;
import com.directorymonitor.service.DirectoryMonitorService;

import com.fasterxml.jackson.annotation.JsonView;

@RestController
@RequestMapping
public class DirectoryMonitorRestController {

	@Autowired
	private DirectoryMonitorService theDirectoryMonitorService;
	
	@JsonView(Views.Public.class)
	@PostMapping("/path")
	public Map<String, ExtendedFile> addPath(@RequestParam("path") String path) {
		ExtendedFile directoryToMonitor = null;
		try {
			directoryToMonitor = new ExtendedFile(path);
			if (directoryToMonitor != null && directoryToMonitor.isExists() && directoryToMonitor.isDirectory()) {
				return theDirectoryMonitorService.addPath(directoryToMonitor);		
			}
		} catch (IOException e) {
			System.out.println("ERROR: Directory initialisation failed.");
		}
		return new HashMap<>();
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/directories")
	public Map<String, ExtendedFile> getDirectoryList(){
		return theDirectoryMonitorService.getDirectoryList();
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/attributesToPublish")
	public LinkedHashSet<String> getAttributesToPublish(){
		LinkedHashSet<ExtendedFileAttributes> attributes = theDirectoryMonitorService.getAttributesToPublish();
		LinkedHashSet<String> attributesToPublish = new LinkedHashSet<>(attributes.size());
		
		for(ExtendedFileAttributes attribute : attributes){
			attributesToPublish.add(attribute.getCustomName());
		}
		return attributesToPublish;
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/filesAndSubDirectory")
	public Map<String, ExtendedFile> getFilesAndSubdirectory(@RequestParam("path") String path){
		return theDirectoryMonitorService.getFilesAndSubDirectory(path);
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/browseFilesAndSubDirectory")
	public Map<String, String> browseFilesAndSubdirectory(@RequestParam("path") String path){
		return theDirectoryMonitorService.browseFilesAndSubDirectory(path);
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/tokenStatistics/{alphabet}")
	public Map<String,Integer> getTokenStatistics(@PathVariable("alphabet")String alphabet){
		return theDirectoryMonitorService.getTokenStatistics(alphabet);
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/tokenDetails")
	public Map<String,Integer> getTokenDetails(@RequestParam("parent")String parent, @RequestParam("filePath")String filePath){
		return theDirectoryMonitorService.getTokenDetails(parent, filePath);
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/tokenDetails/{token}")
	public Map<String,Integer> getTokenFiles(@PathVariable("token") String token){
		return theDirectoryMonitorService.getTokenFiles(token);
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/indexDirectory")
	public SuccessResponse setIndexDirectory(@RequestParam("path")String path) {
		ExtendedFile directoryToStoreIndex;
		SuccessResponse result = new SuccessResponse();
		try {
			directoryToStoreIndex = new ExtendedFile(path);
		
			if (directoryToStoreIndex != null && directoryToStoreIndex.isExists() && directoryToStoreIndex.isDirectory()) {
				if(theDirectoryMonitorService.setIndexDirectory(directoryToStoreIndex)) {
					result.setMsg("Index Store Set Successfully.");
					result.setFlag(true);
				}
			}
			else {
				result.setMsg("Operation Failed."+ path);
				result.setFlag(false);
			}
		} catch (IOException e) {
				result.setMsg("Directory Initialisation Failed.");
				result.setFlag(false);
			}
		return result;
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/tokenSuggestions")
	public Set<String> getTokenSuggestions(@RequestParam("term") String searchQuery){
		return theDirectoryMonitorService.getTokenSuggestions(searchQuery.toLowerCase());
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/search/{searchQuery}")
	public Map<String, ExtendedFile> getSearchResults(@PathVariable("searchQuery")String searchQuery){
		return theDirectoryMonitorService.getSearchResults(searchQuery.toLowerCase());
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/fileContent")
	public ArrayList<String> getFileContent(@RequestParam("path") String filePath) {
		ArrayList<String> content = theDirectoryMonitorService.getFileContent(filePath);
		return content;
	}
	
	@GetMapping("/downloadFile")
	public void downloadFile(@RequestParam("filePath")String filePath, HttpServletRequest request, HttpServletResponse response) {
		File downloadFile = new File(filePath);
        try {
			FileInputStream inputStream = new FileInputStream(downloadFile);
			String mimeType = request.getServletContext().getMimeType(filePath);
			
	        if (mimeType == null) {
	            mimeType = "application/octet-stream";
	        }
	        response.setContentType(mimeType);
	        response.setContentLength((int) downloadFile.length());
	        
	        String headerKey = "Content-Disposition";
	        String headerValue = String.format("attachment; filename=\"%s\"", downloadFile.getName());
	        response.setHeader(headerKey, headerValue);
	        
	        OutputStream outStream = response.getOutputStream();
	        byte[] buffer = new byte[4096];
	        int bytesRead = -1;
	 
	        while ((bytesRead = inputStream.read(buffer)) != -1) {
	            outStream.write(buffer, 0, bytesRead);
	        }
	 
	        inputStream.close();
	        outStream.close();
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Cannot download. "+filePath+" not found.");
		} catch (IOException e) {
			System.out.println("ERROR: Cannot download. "+filePath+" not found.");
		}
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/files/{attribute}")
	public Map<String,ExtendedFile> searchFiles(@PathVariable("attribute")String attribute, @RequestParam("searchQuery")String searchQuery, @RequestParam("searchLocation")String searchLocation){
		return theDirectoryMonitorService.searchFiles(attribute, searchQuery, searchLocation);
	}
	
	@RequestMapping(value = "/uploadFile", method = RequestMethod.POST)
	public String uploadFileHandler(@RequestParam("file") MultipartFile file, @RequestParam("path") String path) {
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();

				File dir = new File(path);
				if (!dir.exists())
					dir.mkdirs();

				File serverFile = new File(dir.getAbsolutePath() , file.getOriginalFilename());
				BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
				stream.write(bytes);
				stream.close();
				
				return file.getOriginalFilename()+" uploaded successfully.";
			} catch (Exception e) {
				return file.getOriginalFilename() + "failed to upload";
			}
		} else {
			return file.getOriginalFilename() + "failed to upload";
		}
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/stopwords")
	public SuccessResponse addStopWords(@RequestParam("words")String wordList){
		return theDirectoryMonitorService.addStopWords(wordList);
	}
	
	@JsonView(Views.Public.class)
	@GetMapping("/stopwords")
	public Set<String> getStopWords(){
		return theDirectoryMonitorService.getStopWords();
	}
	
	@PostMapping("/fileCount")
	public long getFileCount(@RequestParam("path")String path){
		return theDirectoryMonitorService.getFileCount(path);
	}
	
	@PostMapping("/folderCount")
	public Integer getFoldereCount(@RequestParam("path")String path){
		return theDirectoryMonitorService.getFolderCount(path);
	}
	
	@PostMapping("/folderSize")
	public Long getFoldereSize(@RequestParam("path")String path){
		return theDirectoryMonitorService.getFolderSize(path);
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/filesByExtension")
	public Map<String, List<ExtendedFile>> getFilesByExtension(@RequestParam("path")String path) {
		return theDirectoryMonitorService.getFilesByExtension(path);
	}
	
	@JsonView(Views.Public.class)
	@PostMapping("/folderTokens")
	public Map<String, Integer> getFolderTokens(@RequestParam("path")String path){
		return theDirectoryMonitorService.getFolderTokens(path);
	}
	
	@GetMapping("/downloadAll")
	public void downloadAll(@RequestParam("paths") String filePaths, HttpServletRequest request, HttpServletResponse response){
		theDirectoryMonitorService.downloadAll(filePaths, request, response);
	}
	
	@GetMapping("/rootDirectories")
	public List<String> getRootDirectories(){
		return theDirectoryMonitorService.getRootDirectories();
	}
}