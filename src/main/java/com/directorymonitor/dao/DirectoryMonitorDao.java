package com.directorymonitor.dao;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.stream.Collectors.*;
import static java.util.Comparator.comparingInt;

import java.nio.file.WatchEvent;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xslf.usermodel.DrawingParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.ServletContextAware;

import com.directorymonitor.model.ExtendedFile;
import com.directorymonitor.model.ExtendedFileAttributes;
import com.directorymonitor.model.NotificationModel;
import com.directorymonitor.model.SuccessResponse;
import com.directorymonitor.model.comparator.*;
import com.directorymonitor.notifier.ServerEndPoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class DirectoryMonitorDao implements ServletContextAware, Runnable {

	private Map<String,ExtendedFile> directoriesToMonitor;
	private Set<String> majorDirectories;
	private Map<String,Map<String,Integer>> tokens;
	private ExtendedFile directoryToStoreIndex;
	
	private LinkedHashSet<ExtendedFileAttributes> attributesToPublish;
	private ExtendedFileAttributes attributes[];
	
	private WatchService watcher;
	private Map<WatchKey, Path> keys;
	
	private Set<String> stopWords;
	private Connection con;
	private PreparedStatement thePreparedStatement;
	private ResultSet resultSet;
	int readable;	
	@Autowired
	private ServletContext context;
	
	public DirectoryMonitorDao() throws IOException {
		directoriesToMonitor=new HashMap<>();
		attributes = ExtendedFileAttributes.values();		
		watcher = FileSystems.getDefault().newWatchService();
		keys = new HashMap<>();
		attributesToPublish = new LinkedHashSet<>(attributes.length);
		tokens = new HashMap<>();
		readStorePath();
		for(ExtendedFileAttributes attribute: attributes) {
			attributesToPublish.add(attribute);
		}
	}
	
	private void fetchStopWords() {
		con = (Connection) context.getAttribute("datacon");
		if(con != null) {
			String query = "select word from stopwords"; 
			try {
				thePreparedStatement = con.prepareStatement(query);
				resultSet = thePreparedStatement.executeQuery();
				stopWords = new HashSet<>();
				while(resultSet.next()) {
					stopWords.add(resultSet.getString(1));
				}
			} catch (SQLException e) {
				System.out.println("ERROR: Unable to fetch stop words.");
			}
		}
		else {
			System.out.println("ERROR: Cannot fetch stop words. Database connection not established.");
		}
	}

	@Override
	public void setServletContext(ServletContext servletContext) {
		this.context = servletContext; 
		fetchStopWords();
	}
	
	@SuppressWarnings("unchecked")
	@PostConstruct
	private void readIndexFile() {
		try {   
	        FileInputStream file = new FileInputStream(new File(directoryToStoreIndex , "index.ser").getPath());
	        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(file));
	        directoriesToMonitor = (Map<String,ExtendedFile>)in.readObject();
	        in.close();
	        file.close();
	        System.out.println("SUCCESS: Index file readed successfully from "+directoryToStoreIndex.getPath());
        } catch(IOException ex) {
        	directoriesToMonitor = new HashMap<>();
            System.out.println("ERROR OCCURED: Not able to read index file.");
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR OCCURED: Not able to read index file.");
		}
		
		try {   
	        FileInputStream file = new FileInputStream(new File(directoryToStoreIndex , "tokens.ser").getPath());
	        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(file));
	        tokens = (Map<String , Map<String, Integer>>)in.readObject();
	        in.close();
	        file.close();
	        System.out.println("SUCCESS: Tokens file readed successfully from "+directoryToStoreIndex.getPath());
        } catch(IOException ex) {
        	tokens = new HashMap<>();
            System.out.println("ERROR OCCURED: Not able to read Tokens file.");
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR OCCURED: Not able to read Tokens file.");
		}
		
		try {   
	        FileInputStream file = new FileInputStream(new File(directoryToStoreIndex , "majorDirs.ser").getPath());
	        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(file));
	        majorDirectories = (Set<String>)in.readObject();
	        in.close();
	        file.close();
	        System.out.println("SUCCESS: Major Dirs file readed successfully from "+directoryToStoreIndex.getPath());
        } catch(IOException ex) {
        	majorDirectories = new HashSet<>();
            System.out.println("ERROR OCCURED: Not able to read Major Dirs file.");
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR OCCURED: Not able to read Major Dirs file.");
		}
		
		registerAll();
	}
	
	@PreDestroy
	private void writeIndexFile() {
		 try {   
			FileOutputStream file = new FileOutputStream(new File(directoryToStoreIndex , "index.ser").getPath());
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(file));     
            out.writeObject(directoriesToMonitor);
            out.close();
            file.close();
            System.out.println("SUCCESS: Index file created successfully at "+directoryToStoreIndex.getPath());
         } catch(IOException ex) {
        	 System.out.println("ERROR OCCURED: Not able to create Index file.");
         }
		 
		 try {   
			FileOutputStream file = new FileOutputStream(new File(directoryToStoreIndex , "tokens.ser").getPath());
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(file));     
            out.writeObject(tokens);
            out.close();
            file.close();
            System.out.println("SUCCESS: Tokens file created successfully at "+directoryToStoreIndex.getPath());
	     } catch(IOException ex) {
	    	 System.out.println("ERROR OCCURED: Not able to create Tokens file.");
	     }
		 
		 try {   
			FileOutputStream file = new FileOutputStream(new File(directoryToStoreIndex , "majorDirs.ser").getPath());
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(file));     
            out.writeObject(majorDirectories);
            out.close();
            file.close();
            System.out.println("SUCCESS: Major Dirs file created successfully at "+directoryToStoreIndex.getPath());
		 } catch(IOException ex) {
			 System.out.println("ERROR OCCURED: Not able to create Major Dirs file.");
		 }
	}
	
	private void readStorePath() {
		FileReader reader;
		try {
			reader = new FileReader("info.properties");
			Properties p= new Properties();  
			p.load(reader);
			this.directoryToStoreIndex = new ExtendedFile(p.getProperty("path"));
			reader.close();
		} catch (IOException e) {
			try {
				System.out.println("ERROR: info.properties not found.");
				setIndexDirectory(new ExtendedFile(System.getProperty("user.dir")));
			} catch (IOException e1) {
				System.out.println("ERROR: Failed to create info.properties.");
			}
		}
		
	}

	public long calculateTotalFiles(ExtendedFile directoryToMonitor){
		FileCounter theFileCounter = new FileCounter();
		try {
			Files.walkFileTree(directoryToMonitor.toPath(), theFileCounter);
			return theFileCounter.getFilesCount();
		} catch (IOException e) {
			System.out.println("ERROR: Counting of files cannot be done.");
			return -1;
		}
	}
	
	public Map<String, ExtendedFile> addPath(ExtendedFile directoryToMonitor) throws IOException {
		if(!directoriesToMonitor.containsKey(directoryToMonitor.getPath())) {
            	System.out.println("INFO: Calculating number of files and directories at "+ new Date());
				long totalFiles = calculateTotalFiles(directoryToMonitor);
				System.out.println("SUCCESS: Total number of files and directory calculation done at "+ new Date());
				System.out.println("INFO: Total number of files and subdirectory: "+ totalFiles);
				
				long startTime = new Date().getTime();
				long processedFiles = listFilesAndSubDirectory(directoryToMonitor,0L ,totalFiles, true, directoryToMonitor);
				System.out.println("INFO: Total No. of readable files: "+readable);
				if(processedFiles != -1) {
					processedFiles++;
					
					long endTime = new Date().getTime();
					long diff = endTime - startTime;
					long diffSeconds = diff / 1000 % 60;
					long diffMin = diff / (60 * 1000) % 60;
					long diffHours = diff / (60 * 60 * 1000) % 24;
					long diffDays = diff / (24 * 60 * 60 * 1000);
					String timeTaken = null;
					
					if(diffDays != 0) {
						timeTaken = new Long(diffDays).toString()+" days "+ new Long(diffHours).toString()+" hours "+ new Long(diffMin).toString()+" minutes "+ new Long(diffSeconds).toString()+" seconds ";
					}
					else if(diffHours != 0) {
						timeTaken = new Long(diffHours).toString()+" hours "+ new Long(diffMin).toString()+" minutes "+ new Long(diffSeconds).toString()+" seconds ";
					}
					else if(diffMin != 0) {
						timeTaken = new Long(diffMin).toString()+" minutes "+ new Long(diffSeconds).toString()+" seconds ";
					}
					else if(diffSeconds != 0) {
						timeTaken = new Long(diffSeconds).toString()+" seconds ";
					}
					else {
						timeTaken = new Long(diff).toString()+" milliseconds ";
					}
					
					System.out.println("INFO: Start time: "+ startTime+" ,End time: "+endTime);
					ServerEndPoint.notifyAllUsers(new NotificationModel("INDEX_PROGRESS"+directoryToMonitor.getPath(),new Float((totalFiles * 100)/totalFiles)+"-"+new Long(totalFiles)+"/"+ new Long(totalFiles) + " files indexed $ Indexing of "+directoryToMonitor.getPath()+" completed after "+timeTaken+ ".", new Date().getTime()));
					majorDirectories.add(directoryToMonitor.getPath());
					
					if( directoryToMonitor.getParent() != null && directoriesToMonitor.containsKey(directoryToMonitor.getParent())) {
						synchronized(directoriesToMonitor){
							directoriesToMonitor.get(directoryToMonitor.getParent()).incFolderSize(directoryToMonitor.getSize());
						}
					}
					
					Map<String, ExtendedFile> indexedDirectory = new HashMap<>();
					indexedDirectory.put(directoryToMonitor.getPath(), directoryToMonitor);
					return indexedDirectory;
				}
				else
					return new HashMap<>();
		}
		return null;
	}
	
	public long listFilesAndSubDirectory(ExtendedFile directory, long processedFiles, long totalFiles, boolean sendToClient, ExtendedFile currentIndexDirectory) {
		if (directory.isDirectory()) {	
			if(directory.getPath() != null && directoriesToMonitor.containsKey(directory.getPath())) {
				return processedFiles;
			}
			else {
				if( directory.getParent() != null && directoriesToMonitor.containsKey(directory.getParent())) {
					synchronized(directoriesToMonitor){
						directoriesToMonitor.get(directory.getParent()).addToFilesAndSubDirectories(directory.getPath(), directory);
						directoriesToMonitor.get(directory.getParent()).incCountOfFolders();
					}
				}
				
				try {
					synchronized(directoriesToMonitor) {
						register(directory.toPath());
						directoriesToMonitor.put(directory.getPath(),directory);
					}
				} catch (IOException e) {
					System.out.println("ERROR: Unable to register "+directory.getPath());
				}
				
				File filesAndSubDirectory[] = directory.listFiles();
	
				if (filesAndSubDirectory != null && directory.getFilesAndSubDirectories() == null) {
					directory.setFilesAndSubDirectories(new HashMap<>(filesAndSubDirectory.length));
				
					ExtendedFile extendedFile = null;
	
					for (File theFile : filesAndSubDirectory) {
						try {
							extendedFile = new ExtendedFile(theFile.getPath());
						
							if (extendedFile.exists() && extendedFile.canRead() && extendedFile.isFile()) {
								if(sendToClient)
								ServerEndPoint.notifyAllUsers(new NotificationModel("INDEX_PROGRESS"+currentIndexDirectory.getPath(), new Float((processedFiles * 100)/totalFiles)+"-"+new Long(processedFiles)+"/"+ new Long(totalFiles) + " files indexed $ Indexing :"+extendedFile.getPath(), new Date().getTime()));
								
								readTokensFromFile(extendedFile);
								processedFiles++;
								directory.incCountOfFiles();
								directory.addToFilesAndSubDirectories(extendedFile.getPath(), extendedFile);
								directory.incFolderSize(extendedFile.getSize());
						
							} else if (extendedFile.exists() && extendedFile.canRead() && extendedFile.isDirectory()) {
								if(sendToClient)
								ServerEndPoint.notifyAllUsers(new NotificationModel("INDEX_PROGRESS"+currentIndexDirectory.getPath() ,new Float((processedFiles * 100)/totalFiles)+"-"+new Long(processedFiles)+"/"+ new Long(totalFiles) + " files indexed $ Indexing :"+extendedFile.getPath(), new Date().getTime()));
								
								long tempProcessedFiles = listFilesAndSubDirectory(extendedFile, processedFiles, totalFiles, sendToClient, currentIndexDirectory);
								
								if(tempProcessedFiles != -1) {
									processedFiles = tempProcessedFiles;
									processedFiles++;
									directory.addToFilesAndSubDirectories(extendedFile.getPath(), extendedFile);
									directory.incFolderSize(extendedFile.getSize());
								}	
							}
						} catch (IOException e) {
							System.out.println("ERROR: Cannot index "+ theFile.getPath());
							continue;
						}
					}
					return processedFiles;
				}
				else {
					System.out.println("ERROR: Cannot list "+filesAndSubDirectory);
				}
			}
		}
		return -1;
	}
	
	private void readTokensFromFile(ExtendedFile file){

		try {
			if(file.getExtension()!=null && file.getExtension().equals("pdf")) {
				readTokenFromPDFFile(file);
				return;
			}
			else if(file.getExtension()!=null && file.getExtension().equals("docx")) {
				readTokensFromDocFile(file);
				return;
			}
			else if(file.getExtension()!=null && file.getExtension().equals("pptx")) {
				readTokensFromPPTFile(file);
				return;
			}
			
	        FileInputStream input = new FileInputStream(file);
	        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
	        decoder.onMalformedInput(CodingErrorAction.REPLACE);
	        InputStreamReader reader = new InputStreamReader(input, decoder);
	        BufferedReader bufferedReader = new BufferedReader( reader );
	        String line = null;
	        
	        do {
	            line = bufferedReader.readLine();
	            if(line != null && !line.contains(decoder.replacement())) {
		            StringTokenizer tokensFromLine = new StringTokenizer(line,",;:'\"{}[]()<>?/*\\<%>|1234567890.+-_=~!@#$^&` \b\n\t\r\f");
					
		            while(tokensFromLine.hasMoreTokens()) {
						String token = tokensFromLine.nextToken().toLowerCase().intern();
						if(stopWords.contains(token)) {
							continue;
						}
						else {	
							Map<String, Integer> value = null;
							if(tokens.containsKey(token.intern())) {
								synchronized(value = tokens.get(token.intern())) {
									if(value.containsKey(file.getPath().intern())){
										if(file.getPath().intern() != null && value.get(file.getPath().intern()) != null ) {
											value.replace(file.getPath().intern(), value.get(file.getPath().intern())+1);
											file.incrementTokenCount(token.intern());
										}
									}
									else if(file.getPath() != null){
										value.put(file.getPath().intern(),1);
										file.addNewToken(token.intern());
									}
								}
								synchronized(tokens) {
									if(value != null)
									tokens.replace(token.intern(), value);
								}
							}
							else {
								value = new HashMap<>();
								
								synchronized(value) {
									value.put(file.getPath().intern(), 1);
									file.addNewToken(token.intern());
								}
								
								synchronized(tokens) {
									tokens.put(token.intern(),value);
								}
							}
						}
					}
		        }
	            else if(line != null && line.contains(decoder.replacement())) {
	            	removeTokens(file);
	            	file.setTokens(new HashMap<>());
	            	bufferedReader.close();
	            	return;
	            }
	        } while( line != null );
	        readable++;
	        bufferedReader.close();
	    } catch (FileNotFoundException e) {
	        System.out.println("ERROR: File not found. "+ file.getName());
	    } catch(MalformedInputException ex) {
	    	System.out.println("ERROR: File is not readable. "+ file.getName());
	    } catch( IOException e ) {
	    	System.out.println("ERROR: File is not readable. "+ file.getName());
	    } catch (InvalidFormatException e) {
	    	System.out.println("ERROR: File is not readable. REASON: File format invalid." + file.getName());
		} catch(NotOfficeXmlFileException e) {
			System.out.println("ERROR: File is not readable. REASON: File format invalid." + file.getName());
		}
	}
	
	private void readTokensFromPPTFile(ExtendedFile file) throws FileNotFoundException, IOException {
		XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(file));
		List<XSLFSlide> slides = ppt.getSlides();
		ArrayList<String> linesList = new ArrayList<>();
		CharSequence text = null;
		
		for(int i=0;i<slides.size();i++) {
			XSLFSlide slide = slides.get(i);
			List<DrawingParagraph> data = slide.getCommonSlideData().getText();
	
			for(int j=0;j<data.size();j++) {
				DrawingParagraph para = data.get(j);
				text = para.getText();
				
				if(text != null)
				linesList.add(text.toString());
			}
		}
		ppt.close();
		
		for(String line : linesList) {
	      	StringTokenizer tokensFromLine = new StringTokenizer(line,",;:'\"{}[]()<>?/*\\<%>|1234567890.+-_=~!@#$^&` \b\n\t\r\f");
			
	          while(tokensFromLine.hasMoreTokens()) {
	 				String token = tokensFromLine.nextToken().toLowerCase().intern();
	 				if(stopWords.contains(token)) {
	 					continue;
	 				}
	 				else {	
	 					Map<String, Integer> value = tokens.get(token.intern());
	 					if(tokens.containsKey(token.intern()) && value != null) {
	 						
	 						synchronized(value) {
	 							if(value.containsKey(file.getPath().intern())){
	 								if(file.getPath() != null && value.get(file.getPath().intern()) != null ) {
	 									value.put(file.getPath().intern(), value.get(file.getPath().intern())+1);
	 									file.incrementTokenCount(token.intern());
	 								}
	 							}
	 							else if(file.getPath() != null){
	 								value.put(file.getPath().intern(),1);
	 								file.addNewToken(token.intern());
	 							}
	 						}
	 						
	 						synchronized(tokens) {
	 							if(value != null)
	 							tokens.replace(token.intern(), value);
	 						}
	 					}
	 					else {
	 						value = new HashMap<>();
	 						
	 						synchronized(value) {
	 							value.put(file.getPath().intern(), 1);
	 							file.addNewToken(token.intern());
	 						}
	 						
	 						synchronized(tokens) {
	 							tokens.put(token.intern(),value);
	 						}
	 					}
	 				}
	 			}
	      }
	}

	private void readTokensFromDocFile(ExtendedFile file) throws InvalidFormatException, IOException, NotOfficeXmlFileException {
		
		FileInputStream fis = new FileInputStream(file);
		XWPFDocument xdoc = new XWPFDocument(OPCPackage.open(fis));
		XWPFWordExtractor extractor = new XWPFWordExtractor(xdoc);
		String lines[] = extractor.getText().split("\\r?\\n");
		extractor.close();
		xdoc.close();
		fis.close();
		
	    ArrayList<String> linesList = new ArrayList<>(Arrays.asList(lines));
	      
	      for(String line : linesList) {
	      	StringTokenizer tokensFromLine = new StringTokenizer(line,",;:'\"{}[]()<>?/*\\<%>|1234567890.+-_=~!@#$^&` \b\n\t\r\f");
			
	          while(tokensFromLine.hasMoreTokens()) {
	 				String token = tokensFromLine.nextToken().toLowerCase().intern();
	 				if(stopWords.contains(token)) {
	 					continue;
	 				}
	 				else {	
	 					Map<String, Integer> value = tokens.get(token.intern());
	 					if(tokens.containsKey(token.intern()) && value != null) {
	 						
	 						synchronized(value) {
	 							if(value.containsKey(file.getPath().intern())){
	 								if(file.getPath() != null && value.get(file.getPath().intern()) != null ) {
	 									value.put(file.getPath().intern(), value.get(file.getPath().intern())+1);
	 									file.incrementTokenCount(token.intern());
	 								}
	 							}
	 							else if(file.getPath() != null){
	 								value.put(file.getPath().intern(),1);
	 								file.addNewToken(token.intern());
	 							}
	 						}
	 						
	 						synchronized(tokens) {
	 							if(value != null)
	 							tokens.replace(token.intern(), value);
	 						}
	 					}
	 					else {
	 						value = new HashMap<>();
	 						
	 						synchronized(value) {
	 							value.put(file.getPath().intern(), 1);
	 							file.addNewToken(token.intern());
	 						}
	 						
	 						synchronized(tokens) {
	 							tokens.put(token.intern(),value);
	 						}
	 					}
	 				}
	 			}
	      }
		
	}

	private void readTokenFromPDFFile(ExtendedFile file) throws InvalidPasswordException, IOException {
		
		try (PDDocument document = PDDocument.load(file)) {
            document.getClass();
            if (!document.isEncrypted()) {
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);

                PDFTextStripper tStripper = new PDFTextStripper();
                String pdfFileInText = tStripper.getText(document);
              
                String lines[] = pdfFileInText.split("\\r?\\n");
                ArrayList<String> linesList = new ArrayList<>(Arrays.asList(lines));
                
                for(String line : linesList) {
                	StringTokenizer tokensFromLine = new StringTokenizer(line,",;:'\"{}[]()<>?/*\\<%>|1234567890.+-_=~!@#$^&` \b\n\t\r\f");
        			
                    while(tokensFromLine.hasMoreTokens()) {
           				String token = tokensFromLine.nextToken().toLowerCase().intern();
           				if(stopWords.contains(token)) {
           					continue;
           				}
           				else {	
           					Map<String, Integer> value = tokens.get(token.intern());
           					if(tokens.containsKey(token.intern()) && value != null) {
           						
           						synchronized(value) {
           							if(value.containsKey(file.getPath().intern())){
           								if(file.getPath() != null && value.get(file.getPath().intern()) != null ) {
           									value.put(file.getPath().intern(), value.get(file.getPath().intern())+1);
           									file.incrementTokenCount(token.intern());
           								}
           							}
           							else if(file.getPath() != null){
           								value.put(file.getPath().intern(),1);
           								file.addNewToken(token.intern());
           							}
           						}
           						
           						synchronized(tokens) {
           							if(value != null)
           							tokens.replace(token.intern(), value);
           						}
           					}
           					else {
           						value = new HashMap<>();
           						
           						synchronized(value) {
           							value.put(file.getPath().intern(), 1);
           							file.addNewToken(token.intern());
           						}
           						
           						synchronized(tokens) {
           							tokens.put(token.intern(),value);
           						}
           					}
           				}
           			}
                }
            }
        }
	}

	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		keys.put(key, dir);
	}
	
	private void registerAll() {
		Path dir = null;
		List<String> deletedPaths = new ArrayList<>();
		
		for (Map.Entry<String, ExtendedFile> entry : directoriesToMonitor.entrySet()) {
			dir = entry.getValue().toPath();
			if(dir != null ) {
				WatchKey key;
				try {
					key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
					keys.put(key, dir);
				} catch (IOException e) {
					deletedPaths.add(dir.toString());
					System.out.println("ERROR: Failed to register " + dir.toString());
				}
			}
		}
		
		removeDeletedDirectories(deletedPaths);
	}

	private void removeDeletedDirectories(List<String> deletedPaths) {
		ExtendedFile deletedDirectory = null;
		for(String path: deletedPaths) {
			try {
				deletedDirectory = new ExtendedFile(path);
				removeFromParent(deletedDirectory);
				removeFromTree(deletedDirectory);
			} catch (IOException e) {
				System.out.println("ERROR: Failed to remove " + path);
			}			
		}
	}

	public Map<String,ExtendedFile> getDirectoryList() {
		Map<String, ExtendedFile> directoryList = new HashMap<>();
		for(String directoryPath : majorDirectories) {
			directoryList.put(directoryPath,directoriesToMonitor.get(directoryPath));
		}
		
		return directoryList;
	}

	public LinkedHashSet<ExtendedFileAttributes> getAttributesToPublish() {
		return attributesToPublish;
	}

	public Map<String, ExtendedFile> getFilesAndSubDirectory(String path) {
		ExtendedFile directory = null;
		synchronized(this) {
			directory = directoriesToMonitor.get(path);
		}
		Map<String, ExtendedFile> filesMap = new LinkedHashMap<>();
		
		if(directory != null && directory.getFilesAndSubDirectories()!= null) {
			List<ExtendedFile> filesList = null;
			filesList = new ArrayList<>(directory.getFilesAndSubDirectories().values());
			
			if(filesList != null) {
				Collections.sort(filesList, FileTypeComparator.getInstance());
			
				for(ExtendedFile theFile: filesList) {
					filesMap.put(theFile.getPath(), theFile);
				}	
				return filesMap;
			}
		}
		return filesMap;
	}

	public Map<String, String> browseFilesAndSubDirectory(String path) {
		if(path != null){
			Map<String, String> subDirectoriesMap = new HashMap<>();
			File theDirectory = new File(path);
			File[] subDirectories = theDirectory.listFiles();
			
			if(subDirectories != null){
				for(File theSubDirectory : subDirectories){
					if(theSubDirectory!=null  && theSubDirectory.isDirectory()){
						subDirectoriesMap.put(theSubDirectory.getPath(), theSubDirectory.getName());
					}
				}
				return subDirectoriesMap;
			}
		}
		return null;
	}
	
	public Map<String, Integer> getTokenStatistics(String alphabet) {
		Map<String,Integer> tokenStatistics = new TreeMap<>();
		int value = 0;
		if(!alphabet.trim().equals("all")){
			synchronized(tokens) {
				Set<String> resultTokens = tokens
										   .keySet()
										   .stream()
										   .filter(s -> s.startsWith(alphabet))
										   .collect(Collectors.toSet());
				
				for(String key : resultTokens){
					value = new ArrayList<>(tokens.get(key.intern()).values()).stream().mapToInt(Integer::intValue).sum();
					tokenStatistics.put(key, value);
				}
			}
		}
		else{
			synchronized(tokens) {
				for(String key : tokens.keySet()){
					value = new ArrayList<>(tokens.get(key.intern()).values()).stream().mapToInt(Integer::intValue).sum();
					tokenStatistics.put(key, value);
				}
			}
		}
		return tokenStatistics;
	}

	public Map<String, Integer> getTokenDetails(String parent, String filePath) {
		ExtendedFile parentDirectory = directoriesToMonitor.get(parent);
		
		if(parentDirectory != null) {
		ExtendedFile childFile = parentDirectory.getChild(filePath);
			
			if(childFile != null) {
					return childFile.getTokens() 
									.entrySet() 
									.stream() 
									.sorted(Collections.reverseOrder(Map.Entry.comparingByValue())) 
									.collect( toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
			}
		}
		return null;
	}

	public Map<String, Integer> getTokenFiles(String token) {
		Map<String,Integer> tokenMap = null;
		
		synchronized(tokens) {
			tokenMap = tokens.get(token);
		}
		
		if(tokenMap != null) {
			return tokenMap.entrySet()
							.stream()
							.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
							.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		}
		return null;
	}

	public boolean setIndexDirectory(ExtendedFile directoryToStoreIndex) throws IOException {
    	Properties p=new Properties();  
    	p.setProperty("path",directoryToStoreIndex.getPath().intern());   
        p.store(new FileWriter("info.properties"),"Path where index is stored");  
		
		this.directoryToStoreIndex = directoryToStoreIndex;
		System.out.println("SUCCESS: info.properties created successfully.");
		return true;
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}
	
	@Override
	public void run() {
		processEvents();
	}
	
	public void processEvents() {
		System.out.println("INFO: Starting watcher service.");
		for (;;) {
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey not recognized!!");
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				@SuppressWarnings("rawtypes")
				WatchEvent.Kind kind = event.kind();

				if (kind == OVERFLOW) {
					continue;
				}

				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);
				ExtendedFile newFileOrDirectory;
				
				try {
					newFileOrDirectory = new ExtendedFile(child.toString());
					//System.out.println(kind+" : "+newFileOrDirectory.getPath());
					
					if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
						insertInTree(newFileOrDirectory);

					} else if (kind == ENTRY_DELETE) {
						removeFromParent(newFileOrDirectory);
						removeFromTree(newFileOrDirectory);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}

			boolean valid = key.reset();
			if (!valid) {
				keys.remove(key);

				if (keys.isEmpty()) {
					break;
				}
			}
		}
	}

	private void insertInTree(ExtendedFile newFileOrDirectory) throws IOException {
		if(newFileOrDirectory.isDirectory()) {
			if(directoriesToMonitor.containsKey(newFileOrDirectory.getPath())) {
				//System.out.println("ALREADY PRESENT: "+ newFileOrDirectory.getPath());
				return;
			}
			else {
				System.out.println("NEW Directory OCCURED: "+ newFileOrDirectory.getPath());
				ServerEndPoint.notifyAllUsers(new NotificationModel("DIRECTORY_CREATE","New directory occured: "+ newFileOrDirectory.getPath(), new Date().getTime()));
				long totalFiles = calculateTotalFiles(newFileOrDirectory);
				listFilesAndSubDirectory(newFileOrDirectory, 0L, totalFiles, false , newFileOrDirectory);
				
				if( newFileOrDirectory.getParent() != null && directoriesToMonitor.containsKey(newFileOrDirectory.getParent())) {
					synchronized(directoriesToMonitor){
						directoriesToMonitor.get(newFileOrDirectory.getParent()).incFolderSize(newFileOrDirectory.getSize());
					}
				}
			}
		}
		else {
			ExtendedFile parent = directoriesToMonitor.get(newFileOrDirectory.getParent()); 
	
			if(parent!=null && directoriesToMonitor.containsKey(newFileOrDirectory.getParent()) && parent.getFilesAndSubDirectories().containsKey(newFileOrDirectory.getPath())){
				//System.out.println("ALREADY PRESENT FILE: " + newFileOrDirectory.getPath());
				Map<String, ExtendedFile> children = parent.getFilesAndSubDirectories();
				ExtendedFile child = null;
				if(children != null){
					child = children.get(newFileOrDirectory.getPath());
					if(child != null){
						parent.decFolderSize(child.getSize());
						parent.incFolderSize(newFileOrDirectory.getSize());
					}
				}
				
				removeTokens(parent.getFilesAndSubDirectories().get(newFileOrDirectory.getPath()));
				readTokensFromFile(newFileOrDirectory);
				parent.replaceFilesAndSubDirectories(newFileOrDirectory.getPath(), newFileOrDirectory);
			}
			else if(parent!=null && directoriesToMonitor.containsKey(newFileOrDirectory.getParent())){
				System.out.println("NEW OCCURED FILE: "+newFileOrDirectory.getPath());
				ServerEndPoint.notifyAllUsers(new NotificationModel("FILE_CREATE","New File occured: "+ newFileOrDirectory.getPath(), new Date().getTime()));
				
				readTokensFromFile(newFileOrDirectory);
				parent.addToFilesAndSubDirectories(newFileOrDirectory.getPath(), newFileOrDirectory);
				parent.incCountOfFiles();
				parent.incFolderSize(newFileOrDirectory.getSize());
			}
			else{
				System.out.println("ERROR: Parent Not Found.");
			}
		}
	}

	synchronized private void removeFromParent(ExtendedFile newFileOrDirectory) {
		if(directoriesToMonitor.containsKey(newFileOrDirectory.getPath())) {
			ExtendedFile parent = directoriesToMonitor.get(directoriesToMonitor.get(newFileOrDirectory.getPath()).getParent());
			if(parent != null) {
				Map<String, ExtendedFile> children = parent.getFilesAndSubDirectories();
				if(children != null) {
					if(children.containsKey(newFileOrDirectory.getPath())){
						parent.decFolderSize(children.get(newFileOrDirectory.getPath()).getSize());
						children.remove(newFileOrDirectory.getPath());
						parent.decCountOfFolders();
					}
				}
			}
		}
	}

	private void removeFromTree(ExtendedFile newFileOrDirectory) {
		if(directoriesToMonitor.containsKey(newFileOrDirectory.getPath())) {
			
			synchronized(majorDirectories) {
				if(majorDirectories.contains(newFileOrDirectory.getPath())) {
					majorDirectories.remove(newFileOrDirectory.getPath());
				}
			}
			
			Map<String, ExtendedFile> children = directoriesToMonitor.get(newFileOrDirectory.getPath()).getFilesAndSubDirectories();
			if(children != null && children.values() != null) {
				ArrayList<ExtendedFile> child = new ArrayList<>(children.values());
				for(int i=0; i< child.size(); i++) {
					removeFromTree(child.get(i));
				}
			}
			
			synchronized (directoriesToMonitor) {
				directoriesToMonitor.remove(newFileOrDirectory.getPath());
			}
			
			System.out.println("DELETED DIRECTORY: "+newFileOrDirectory.getPath());
			ServerEndPoint.notifyAllUsers(new NotificationModel("DIRECTORY_DELETE","Directory deleted: " + newFileOrDirectory.getPath(), new Date().getTime()));
		}
		else {
			ExtendedFile parent = directoriesToMonitor.get(newFileOrDirectory.getParent());
		
			if(parent != null) {
				Map<String , ExtendedFile> filesAndSubdirectory = parent.getFilesAndSubDirectories();
				ExtendedFile deletedFile = null;
				
				if(filesAndSubdirectory != null)
				deletedFile = filesAndSubdirectory.get(newFileOrDirectory.getPath());
				
				if(deletedFile != null) {
					removeTokens(deletedFile);
					filesAndSubdirectory.remove(newFileOrDirectory.getPath());
					parent.decCountOfFiles();
					parent.decFolderSize(deletedFile.getSize());
				}
				
				System.out.println("DELETED FILE:"+newFileOrDirectory.getPath());
				ServerEndPoint.notifyAllUsers(new NotificationModel("FILE_DELETE","File deleted: " + newFileOrDirectory.getPath(), new Date().getTime()));
			}
		}
	}

	private void removeTokens(ExtendedFile theFile) {
		if(theFile != null ) {
			Map<String , Integer> fileTokens = theFile.getTokens();
			if(fileTokens != null) {
				for (String token : fileTokens.keySet()) {
					if(tokens.get(token) != null ) {
						tokens.get(token).remove(theFile.getPath());
					
						if(tokens.get(token).size() == 0) {
							synchronized(tokens) {
								tokens.remove(token);
							}
						}
					}
				}
				theFile.setTokens(null);
			}
		}
	}

	synchronized public Set<String> getTokenSuggestions(String searchQuery) {
		
		Set<String> results = null;
		
		if(searchQuery != null)
		results = tokens.keySet()
                		.stream()
                        .filter(s -> s.contains(searchQuery))
                        .collect(Collectors.toSet());
		return results;
	}

	synchronized public Map<String, ExtendedFile> getSearchResults(String searchQuery) {
		
		Map<String, Integer> filePaths = tokens.get(searchQuery);
		if(filePaths != null) {
			filePaths = filePaths
						.entrySet() 
						.stream() 
						.sorted(Collections.reverseOrder(Map.Entry.comparingByValue())) 
						.collect( toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
			
			Map<String, ExtendedFile> searchResults = new LinkedHashMap<>(filePaths.size());
			ExtendedFile parent = null;
			ExtendedFile resultFile = null;
			
			for(String filePath : filePaths.keySet()) {
				parent = directoriesToMonitor.get(filePath.substring(0,filePath.lastIndexOf('\\')));
				if(parent != null) {
					resultFile = parent.getFilesAndSubDirectories().get(filePath);
					if(resultFile !=null) {
						searchResults.put(resultFile.getPath(), resultFile);
					}
				}
			}
			return searchResults;
		}
		return null;
		
	}

	public ArrayList<String> getFileContent(String filePath) {
		ArrayList<String> content = new ArrayList<>();
		try {
			ExtendedFile fileToRead = new ExtendedFile(filePath);
			if(fileToRead.getExtension().equals("pdf")) {
				return readPDFFile(fileToRead);
			}
			else if(fileToRead.getExtension().equals("docx")) {
				return readDocFiles(fileToRead);
			}
			else if(fileToRead.getExtension().equals("pptx")) {
				return readPPTFiles(fileToRead);
			}
	        FileInputStream input = new FileInputStream(fileToRead);
	        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
	        decoder.onMalformedInput(CodingErrorAction.REPLACE);
	        InputStreamReader reader = new InputStreamReader(input, decoder);
	        BufferedReader bufferedReader = new BufferedReader( reader );
	        String line = null;
	        
	        do {
	            line = bufferedReader.readLine();
	           if(line !=null && line.contains(decoder.replacement())) {
	            	bufferedReader.close();
	            	System.out.println("ERROR: File is not readable. "+ filePath);
	            	return null;
	           }
	           else if(line !=null && !line.contains(decoder.replacement() )) {
	        	   content.add(line);
	           }
	           
		    }while( line != null ) ;
	        bufferedReader.close();
	        
	        if(content.size() > 0)
	        	return content;
	        else
	        	return null;
	      }
		  catch (FileNotFoundException e) {
	        System.out.println("ERROR: File is not readable. REASON: File not found "+ filePath);
	    } catch(MalformedInputException ex) {
	    	System.out.println("ERROR: File is not readable. "+ filePath);
	    } catch( IOException e ) {
	    	System.out.println("ERROR: File is not readable. "+ filePath);
	    } catch (InvalidFormatException e) {
			System.out.println("ERROR: File is not readable. REASON: File format invalid." + filePath);
		}
		return null;
	}

	private ArrayList<String> readPPTFiles(ExtendedFile fileToRead) throws FileNotFoundException, IOException {
		XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(fileToRead));
		List<XSLFSlide> slides = ppt.getSlides();
		ArrayList<String> lines = new ArrayList<>();
		CharSequence text = null;
		for(int i=0;i<slides.size();i++) {
			XSLFSlide slide = slides.get(i);
			List<DrawingParagraph> data = slide.getCommonSlideData().getText();
	
			for(int j=0;j<data.size();j++) {
				DrawingParagraph para = data.get(j);
				text = para.getText();
				
				if(text != null)
				lines.add(text.toString());
			}
		}
		ppt.close();
		return lines;
	}

	private ArrayList<String> readDocFiles(ExtendedFile fileToRead) throws InvalidFormatException, IOException {
		FileInputStream fis = new FileInputStream(fileToRead);
		XWPFDocument xdoc = new XWPFDocument(OPCPackage.open(fis));
		XWPFWordExtractor extractor = new XWPFWordExtractor(xdoc);
		String lines[] = extractor.getText().split("\\r?\\n");
		extractor.close();
		xdoc.close();
		fis.close();
		return new ArrayList<>(Arrays.asList(lines));
		
	}

	private ArrayList<String> readPDFFile(ExtendedFile fileToRead) throws InvalidPasswordException, IOException {
		try (PDDocument document = PDDocument.load(fileToRead)) {
            document.getClass();
            if (!document.isEncrypted()) {
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);

                PDFTextStripper tStripper = new PDFTextStripper();
                String pdfFileInText = tStripper.getText(document);
              
                String lines[] = pdfFileInText.split("\\r?\\n");
                return new ArrayList<>(Arrays.asList(lines));
            }
        }
		return null;
	}

	public Map<String, ExtendedFile> searchFiles(String attribute, String searchQuery, String searchLocation) {
		Map<String, ExtendedFile> result = null;
		if(attribute.equals(ExtendedFileAttributes.FILE_NAME.getCustomName())){
			result = searchByName(searchQuery, searchLocation);
			return result;
		}
		else if(attribute.equals(ExtendedFileAttributes.LAST_MODIFIED_DATE.getCustomName())){
			result = searchByDateModified(searchQuery, searchLocation);
			return result;
		}
		else if(attribute.equals(ExtendedFileAttributes.FILE_EXTENSION.getCustomName())){
			result = searchByExtension(searchQuery, searchLocation);
			return result;
		}
		else if(attribute.equals(ExtendedFileAttributes.CREATION_DATE.getCustomName())){
			result = searchByCreationDate(searchQuery, searchLocation);
			return result;
		}
		return null;
	}

	private Map<String, ExtendedFile> searchByCreationDate(String searchQuery, String searchLocation) {
		if(searchQuery != null){
			Date date = null;
			Date nextDate = null;
			try {
				date = new SimpleDateFormat("MM/dd/yyyy").parse(searchQuery);
				
				Calendar c = Calendar.getInstance();
				c.setTime(date);
				c.add(Calendar.DATE, 1); 
				nextDate = c.getTime();
				
				searchQuery = searchQuery.trim();
				Map<String, ExtendedFile> searchResults = new HashMap<>();
		 		Map<String, ExtendedFile> filesAndSubDirectory = null;
		 		ExtendedFile theDirectory = null;
		 		ExtendedFile theChild = null;
		 		
				if(directoriesToMonitor != null) {
					for (Map.Entry<String, ExtendedFile> entry : directoriesToMonitor.entrySet()) {
						theDirectory = entry.getValue();
						
						if(searchLocation.trim().equals("All Monitored Directories") || theDirectory.getPath().startsWith(searchLocation)) {
							if(theDirectory != null && theDirectory.getCreationDate() >= date.getTime() && theDirectory.getCreationDate() <= nextDate.getTime()){
								searchResults.put(entry.getKey(), theDirectory);
							}
							
							if(theDirectory != null )
							filesAndSubDirectory = theDirectory.getFilesAndSubDirectories();
							
							if(filesAndSubDirectory != null){
								for(Map.Entry<String, ExtendedFile> subEntry : filesAndSubDirectory.entrySet()) {
									theChild = subEntry.getValue();
									if(theChild != null && theChild.getCreationDate() >= date.getTime() && theChild.getCreationDate() <= nextDate.getTime()){
										searchResults.put(subEntry.getKey(), theChild);
									}
								}
							}
						}
			        }
			 		return searchResults
					 		.entrySet()
							.stream()
							.sorted(Map.Entry.comparingByValue(FileTypeComparator.getInstance()))
							.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		 		}			
			} catch (ParseException e) {
				System.out.println("ERROR: Invalid date passed. Date: "+searchQuery);
			}
		}
		else {
			return null;
		}
		return null;

	}

	private Map<String, ExtendedFile> searchByExtension(String searchQuery, String searchLocation) {
		if(searchQuery != null){
			searchQuery = searchQuery.trim();
			Map<String, ExtendedFile> searchResults = new HashMap<>();
	 		Map<String, ExtendedFile> filesAndSubDirectory = null;
	 		ExtendedFile theDirectory = null;
	 		ExtendedFile theChild = null;
	 		
			if(directoriesToMonitor != null) {
				for (Map.Entry<String, ExtendedFile> entry : directoriesToMonitor.entrySet()) {
					theDirectory = entry.getValue();
					
					if(searchLocation.trim().equals("All Monitored Directories") || theDirectory.getPath().startsWith(searchLocation)) {
						if(theDirectory != null && theDirectory.getExtension().equalsIgnoreCase(searchQuery)){
							searchResults.put(entry.getKey(), theDirectory);
						}
						
						if(theDirectory != null)
						filesAndSubDirectory = theDirectory.getFilesAndSubDirectories();
						
						if(filesAndSubDirectory != null){
							for(Map.Entry<String, ExtendedFile> subEntry : filesAndSubDirectory.entrySet()) {
								theChild = subEntry.getValue();
								if(theChild != null && theChild.getExtension() !=  null && theChild.getExtension().equalsIgnoreCase(searchQuery)){
									searchResults.put(subEntry.getKey(), theChild);
								}
							}
						}
					}
		        }
		 		return searchResults
				 		.entrySet()
						.stream()
						.sorted(Map.Entry.comparingByValue(FileTypeComparator.getInstance()))
						.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
	 		}	
		}
		else {
			return null;
		}
		return null;
	}

	private Map<String, ExtendedFile> searchByDateModified(String searchQuery, String searchLocation) {
		if(searchQuery != null){
			Date date = null;
			Date nextDate = null;
			try {
				date = new SimpleDateFormat("MM/dd/yyyy").parse(searchQuery);
				
				Calendar c = Calendar.getInstance();
				c.setTime(date);
				c.add(Calendar.DATE, 1); 
				nextDate = c.getTime();
				
				searchQuery = searchQuery.trim();
				Map<String, ExtendedFile> searchResults = new HashMap<>();
		 		Map<String, ExtendedFile> filesAndSubDirectory = null;
		 		ExtendedFile theDirectory = null;
		 		ExtendedFile theChild = null;
		 		
				if(directoriesToMonitor != null) {
					for (Map.Entry<String, ExtendedFile> entry : directoriesToMonitor.entrySet()) {
						theDirectory = entry.getValue();
						
						if(searchLocation.trim().equals("All Monitored Directories") || theDirectory.getPath().startsWith(searchLocation)){
							if(theDirectory != null && theDirectory.getLastModifiedDate() >= date.getTime() && theDirectory.getLastModifiedDate() <= nextDate.getTime()){
								searchResults.put(entry.getKey(), theDirectory);
							}
							
							if(theDirectory != null)
							filesAndSubDirectory = theDirectory.getFilesAndSubDirectories();
							
							if(filesAndSubDirectory != null){
								for(Map.Entry<String, ExtendedFile> subEntry : filesAndSubDirectory.entrySet()) {
									theChild = subEntry.getValue();
									if(theChild !=  null && theChild.getLastModifiedDate() >= date.getTime() && theChild.getLastModifiedDate() <= nextDate.getTime()){
										searchResults.put(subEntry.getKey(), theChild);
									}
								}
							}
						}
			        }
			 		return searchResults
					 		.entrySet()
							.stream()
							.sorted(Map.Entry.comparingByValue(FileTypeComparator.getInstance()))
							.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
		 		}
				
				
			} catch (ParseException e) {
				System.out.println("ERROR: Invalid date passed. Date: "+searchQuery);
			}
		}
		else {
			return null;
		}
		return null;
	}

	private Map<String, ExtendedFile> searchByName(String searchQuery, String searchLocation) {
		if(searchQuery != null) {
			searchQuery = searchQuery.trim();
			Map<String, ExtendedFile> searchResults = new HashMap<>();
	 		Map<String, ExtendedFile> filesAndSubDirectory = null;
	 		ExtendedFile theDirectory = null;
	 		ExtendedFile theChild = null;
	 		
	 		if(directoriesToMonitor != null){
				for (Map.Entry<String, ExtendedFile> entry : directoriesToMonitor.entrySet()) {
					theDirectory = entry.getValue();
					
					if(searchLocation.trim().equals("All Monitored Directories") || theDirectory.getPath().startsWith(searchLocation)){
						if(theDirectory != null && theDirectory.getName().contains(searchQuery)){
							searchResults.put(entry.getKey(), theDirectory);
						}
						
						if(theDirectory != null)
						filesAndSubDirectory = theDirectory.getFilesAndSubDirectories();
						
						if(filesAndSubDirectory != null){
							for(Map.Entry<String, ExtendedFile> subEntry : filesAndSubDirectory.entrySet()) {
								theChild = subEntry.getValue();
								if(theChild != null && theChild.getName() != null && theChild.getName().contains(searchQuery) && !theChild.isDirectory()){
									searchResults.put(subEntry.getKey(), theChild);
								}
							}
						}
					}
		        }
		 		return searchResults
				 		.entrySet()
						.stream()
						.sorted(Map.Entry.comparingByValue(FileTypeComparator.getInstance()))
						.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
	 		}
	 		else{
	 			return null;
	 		}
		}
		else {
			return null;
		}
	}

	public SuccessResponse addStopWords(String wordList) {
		StringTokenizer stopWordsTokenizer  = new StringTokenizer(wordList, ",\n\t ");
		String word = null;
		SuccessResponse response = new SuccessResponse();
		int count = 0;
		
		con = (Connection) context.getAttribute("datacon");
		if(con == null){
			response.setFlag(false);
			response.setMsg("ERROR: Cannot add stop words. Reason: Not able to connect to database.");
			return response;
		}
		
		while(stopWordsTokenizer.hasMoreTokens()){
			word = stopWordsTokenizer.nextToken().toLowerCase();
			
			if(stopWords.contains(word)){
				continue;
			}
			
			if(con != null) {
				String query = "insert into stopwords(word) values(?)"; 
				
				try {
					thePreparedStatement = con.prepareStatement(query);
					thePreparedStatement.setString(1, word);
					
					if(thePreparedStatement.executeUpdate() > 0){
						stopWords.add(word);
						count++;
					}
				} catch (SQLException e) {
					System.out.println("ERROR: Unable to add stop words.");
				}
			}
		}
		if(count > 0){
			response.setFlag(true);
			response.setMsg("SUCCESS: "+ count + " Stop words added successfully.");
		}
		else{
			response.setFlag(false);
			response.setMsg("ERROR: No stop words added.");
		}
		
		return response;
	}

	public Set<String> getStopWords() {
		return new TreeSet<>(stopWords);
	}

	public long getFileCount(String path) {
		synchronized (directoriesToMonitor) {
			if(directoriesToMonitor.containsKey(path)){
				ExtendedFile theDirectory = directoriesToMonitor.get(path);
				Counter theFileCounter = new Counter();
				try {
					Files.walkFileTree(theDirectory.toPath(), theFileCounter);
					return theFileCounter.getFilesCount();
				} catch (IOException e) {
					System.out.println("ERROR: Counting of files cannot be done.");
					return 0;
				}
			}
		}
		return 0;
		
		
	}

	public Integer getFolderCount(String path) {
		synchronized (directoriesToMonitor) {
			if(directoriesToMonitor.containsKey(path)){
				ExtendedFile theDirectory = directoriesToMonitor.get(path);
				return theDirectory.getCountOfFolders();
			}
		}
		return 0;
	}

	public Long getFolderSize(String path) {
		synchronized (directoriesToMonitor) {
			if(directoriesToMonitor.containsKey(path)){
				ExtendedFile theDirectory = directoriesToMonitor.get(path);
				return theDirectory.getSize();
			}
		}
		return 0L;
	}
	
	public Map<String, List<ExtendedFile>> getFilesByExtension(String path){
		ExtendedFile theDirectory = null;
		ExtendedFile childFile = null;
		String extension = null;
		Map<String, ExtendedFile> children = null;
		Map<String, List<ExtendedFile>> filesByExtension = new HashMap<>();
		Map<String, List<ExtendedFile>> subFilesByExtension = null;
		List<ExtendedFile> filesList = null;
		List<ExtendedFile> subFilesList = null;
		
		if(directoriesToMonitor.containsKey(path)) {
			theDirectory = directoriesToMonitor.get(path);
			if(theDirectory != null) {
				children = theDirectory.getFilesAndSubDirectories();
				
				if(children != null) {
					for(Map.Entry<String, ExtendedFile> child : children.entrySet()) {
						childFile = child.getValue();
				
						if(childFile!= null && childFile.isDirectory()) {
							subFilesByExtension = getFilesByExtension(childFile.getPath());
							
							if(subFilesByExtension != null) {
								for(Map.Entry<String, List<ExtendedFile>> eachSubFileEntry : subFilesByExtension.entrySet()) {
									extension = eachSubFileEntry.getKey();
									subFilesList = eachSubFileEntry.getValue();
									
									if(extension!= null && subFilesList != null && filesByExtension.containsKey(extension)) {
										filesList = filesByExtension.get(extension);
										filesList.addAll(subFilesList);
										
										filesByExtension.replace(extension, filesList);
										
									}
									else if(extension != null && subFilesList!=null && !filesByExtension.containsKey(extension)) {
										filesList = new ArrayList<>();
										filesList.addAll(subFilesList);
										
										filesByExtension.put(extension, filesList);
									}
								}
							}
						}
						else if(childFile != null && !childFile.isDirectory()) {
							extension = childFile.getExtension();
							
							if(extension != null && filesByExtension.containsKey(extension)) {
								filesList = filesByExtension.get(extension);
								
								if(filesList != null) {
									filesList.add(childFile);
									filesByExtension.replace(extension, filesList);
								}
							}
							else if(extension != null && !filesByExtension.containsKey(extension)) {
								filesList = new ArrayList<>();
								filesList.add(childFile);
								
								filesByExtension.put(extension, filesList);
							}
						}
					}
					
					Set<String> extensions = filesByExtension.keySet();
					for(String eachExtension : extensions){
						filesList = filesByExtension.get(eachExtension);
						Collections.sort(filesList, Collections.reverseOrder(FileSizeComparator.getInstance()));
						filesByExtension.replace(eachExtension, filesList);
					}
					
					return filesByExtension.entrySet().stream()
					        			   .sorted(Collections.reverseOrder(comparingInt(e->e.getValue().size())))
					        			   .collect(toMap(
					        					   Map.Entry::getKey, 
					        					   Map.Entry::getValue,
					        					   (a,b) -> {throw new AssertionError();},
					        					   LinkedHashMap::new
					        				));
				}
			}
		}
		return null;
	}
	
	public Map<String, Integer> getFolderTokens(String path){
		ExtendedFile theDirectory = directoriesToMonitor.get(path);
		Map<String, ExtendedFile> children = null;
		Map<String, Integer> folderTokens = new HashMap<>();
		Map<String, Integer> eachFileTokens = null;
		
		if(theDirectory != null){
			children = theDirectory.getFilesAndSubDirectories();
			
			if(children != null){
				for(Map.Entry<String, ExtendedFile> eachChild : children.entrySet()){
					if(eachChild.getKey() != null && eachChild.getValue() != null){
						if(eachChild.getValue().isDirectory()){
							eachFileTokens = getFolderTokens(eachChild.getValue().getPath());
						}
						else if(!eachChild.getValue().isDirectory()){
							eachFileTokens = eachChild.getValue().getTokens();
						}	
							
						if(eachFileTokens != null){
							for(Map.Entry<String, Integer> eachToken : eachFileTokens.entrySet()){
								if(folderTokens.containsKey(eachToken.getKey())){
									folderTokens.replace(eachToken.getKey(), folderTokens.get(eachToken.getKey()) + eachToken.getValue());
								}
								else{
									folderTokens.put(eachToken.getKey(), eachToken.getValue());
								}
							}
						}
					}
				}
				return folderTokens
						.entrySet()
						.stream()
						.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
						.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
			}
		}
		return null;
	}

	public void downloadAll(String filePaths, HttpServletRequest request, HttpServletResponse response) {
		
		List<String> pathList = null;
		ServletOutputStream out = null;
		ZipOutputStream zos = null;
		String zipName = null;
		
		try {
			pathList = new ObjectMapper().readValue(filePaths, new TypeReference<List<String>>(){});
			out = response.getOutputStream();
			zos = new ZipOutputStream(new BufferedOutputStream(out));
		} catch (IOException e1) {
			System.out.println("ERROR: JSON Parsing failed.");
		}
		
		if(pathList.size()==1){
			zipName = directoriesToMonitor.get(pathList.get(0)).getName()+".zip";
		}
		else{
			zipName = "directoryMonitor.zip";
		}
		response.setContentType("Content-type: text/zip");
		response.setHeader("Content-Disposition", "attachment; filename="+zipName);

		List<File> files = new ArrayList<>();
		for(String path : pathList) {
			if(directoriesToMonitor.containsKey(path)) {
				try {
					zipDir(directoriesToMonitor.get(path).getPath(), directoriesToMonitor.get(path).getName(), zos);
				} catch (IOException e) {
					System.out.println("ERROR: Cannot ZIP Folder "+directoriesToMonitor.get(path).getName());
				}
			}
			else {
				files.add(new File(path));
			}
		}
		
		try{
			for (File file : files) {
				zos.putNextEntry(new ZipEntry(file.getName()));
				FileInputStream fis = null;

				try {
					fis = new FileInputStream(file);
				} catch (FileNotFoundException fnfe) {
					zos.write(("ERROR: Cannot find file " + file.getName()).getBytes());
					zos.closeEntry();
					continue;
				}

				BufferedInputStream fif = new BufferedInputStream(fis);
				
				int data = 0;
				while ((data = fif.read()) != -1) {
					zos.write(data);
				}
				fif.close();
				zos.closeEntry();
			}
			zos.close();
		}
		catch (IOException e) {
			System.out.println("ERROR: Downloading Failed.");
		}
	}
	
	public void zipDir(String dirName, String nameZipFile, ZipOutputStream zos) throws IOException {
        ZipOutputStream zip = zos; 
        addFolderToZip("", dirName, zip);
    }

    private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws IOException {
        File folder = new File(srcFolder);
        if (folder.list().length == 0) {
            addFileToZip(path , srcFolder, zip, true);
        }
        else {
            for (String fileName : folder.list()) {
                if (path.equals("")) {
                    addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip, false);
                } 
                else {
                     addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip, false);
                }
            }
        }
    }

    private void addFileToZip(String path, String srcFile, ZipOutputStream zip, boolean flag) throws IOException {
        File folder = new File(srcFile);
        if (flag) {
            zip.putNextEntry(new ZipEntry(path + "/" +folder.getName() + "/"));
        }
        else {
            if (folder.isDirectory()) {
                addFolderToZip(path, srcFile, zip);
            }
            else {
                int len=0;
                FileInputStream in = new FileInputStream(srcFile);
                BufferedInputStream fif = new BufferedInputStream(in);
                
                zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
                while ((len = fif.read()) != -1) {
                    zip.write(len);
                }
                in.close();
				fif.close();
				zip.closeEntry();
            }
        }
    }
    
    public List<String> getRootDirectories() {
		File[] rootDirectories = File.listRoots();
		List<String> rootList = new ArrayList<>();
        
        for(int i=0 ; i < rootDirectories.length ; i++) {
			rootList.add(rootDirectories[i].getPath());
        }
        return rootList;
	}
}

class FileCounter implements FileVisitor<Path>{
	
	private long filesCount;
	
	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
	   return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
	    filesCount++;
	    return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		filesCount++;
	   return FileVisitResult.CONTINUE;
	}
	
	
	public long getFilesCount() {
	    return filesCount;
	}
}

class Counter implements FileVisitor<Path>{
	
	private long filesCount;
	private long foldersCount;
	
	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		foldersCount++;
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
	    filesCount++;
	    return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		filesCount++;
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
	   return FileVisitResult.CONTINUE;
	}
	
	public long getFilesCount() {
	    return filesCount;
	}
	
	public long getFolderssCount() {
	    return foldersCount;
	}
}