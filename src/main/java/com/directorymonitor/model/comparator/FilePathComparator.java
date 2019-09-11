package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FilePathComparator implements Comparator<ExtendedFile> {
	private static FilePathComparator filePathComparator = null;

	private FilePathComparator() {

	}

	public static FilePathComparator getInstance() {
		if (filePathComparator == null)
			filePathComparator = new FilePathComparator();
		
		return filePathComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		return ((file1.getPath()).compareToIgnoreCase(file2.getPath()));
	}
}