package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileLastAccessDateComparator implements Comparator<ExtendedFile> {
	private static FileLastAccessDateComparator fileLastAccessDateComparator = null;

	private FileLastAccessDateComparator() {

	}

	public static FileLastAccessDateComparator getInstance() {
		if (fileLastAccessDateComparator == null)
			fileLastAccessDateComparator = new FileLastAccessDateComparator();

		return fileLastAccessDateComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		return (int)(file1.getLastAccessDate() - file2.getLastAccessDate());
	}
}