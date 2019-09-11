package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileCreationDateComparator implements Comparator<ExtendedFile> {
	private static FileCreationDateComparator fileCreationDateComparator = null;

	private FileCreationDateComparator() {

	}

	public static FileCreationDateComparator getInstance() {
		if (fileCreationDateComparator == null)
			fileCreationDateComparator = new FileCreationDateComparator();

		return fileCreationDateComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		return (int)(file1.getCreationDate() - file2.getCreationDate());
	}
}