package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileNameComparator implements Comparator<ExtendedFile> {
	private static FileNameComparator fileNameComparator = null;

	private FileNameComparator() {

	}

	public static FileNameComparator getInstance() {
		if (fileNameComparator == null)
			fileNameComparator = new FileNameComparator();

		return fileNameComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		return file1.getName().compareToIgnoreCase(file2.getName());
	}
}