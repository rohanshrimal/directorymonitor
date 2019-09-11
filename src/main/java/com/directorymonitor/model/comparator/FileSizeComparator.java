package com.directorymonitor.model.comparator;

import java.util.Comparator;

import com.directorymonitor.model.ExtendedFile;

public class FileSizeComparator implements Comparator<ExtendedFile> {
	private static FileSizeComparator fileSizeComparator = null;

	private FileSizeComparator() {

	}

	public static FileSizeComparator getInstance() {
		if (fileSizeComparator == null)
			fileSizeComparator = new FileSizeComparator();

		return fileSizeComparator;
	}

	public int compare(ExtendedFile file1, ExtendedFile file2) {
		long diff = file1.getSize() - file2.getSize();
		return (int) diff;
	}
}