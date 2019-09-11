package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileExtensionComparator implements Comparator<ExtendedFile> {
	private static FileExtensionComparator fileExtensionComparator = null;

	private FileExtensionComparator() {

	}

	public static FileExtensionComparator getInstance() {
		if (fileExtensionComparator == null)
			fileExtensionComparator = new FileExtensionComparator();

		return fileExtensionComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		return ((file1.getFileExtension()).compareToIgnoreCase(file2.getFileExtension()));
	}
}