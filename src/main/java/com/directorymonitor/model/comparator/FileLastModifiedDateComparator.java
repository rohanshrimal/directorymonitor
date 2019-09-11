package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileLastModifiedDateComparator implements Comparator<ExtendedFile> {
	private static FileLastModifiedDateComparator fileLastModifiedDateComparator = null;

	private FileLastModifiedDateComparator() {

	}

	public static FileLastModifiedDateComparator getInstance() {
		if (fileLastModifiedDateComparator == null)
			fileLastModifiedDateComparator = new FileLastModifiedDateComparator();

		return fileLastModifiedDateComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		return (int)(file1.getLastModifiedDate() - file2.getLastModifiedDate());
	}
}