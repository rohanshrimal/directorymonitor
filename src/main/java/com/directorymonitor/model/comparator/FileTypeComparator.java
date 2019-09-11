package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileTypeComparator implements Comparator<ExtendedFile> {
	private static FileTypeComparator fileTypeComparator = null;

	private FileTypeComparator() {

	}

	public static FileTypeComparator getInstance() {
		if (fileTypeComparator == null)
			fileTypeComparator = new FileTypeComparator();

		return fileTypeComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		if(file1.isDirectory() && file2.isDirectory() && file1.getName() != null && file2.getName() != null)
			return file1.getName().compareToIgnoreCase(file2.getName());
		
		else if( ! file1.isDirectory() && !file2.isDirectory() && file1.getName() != null && file2.getName() != null) {
			return file1.getName().compareToIgnoreCase(file2.getName());
		}
		
		else {
			if(file1.isDirectory()) {
				return 1;
			}
			else {
				return -1;
			}
		}
	}
}