package com.custom.postprocessing.util;

import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_VALUE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.custom.postprocessing.scheduler.PostProcessingScheduler;

@Component
public class ZipUtility {

	PostProcessingScheduler postProcessingScheduler = new PostProcessingScheduler();

	Logger logger = LoggerFactory.getLogger(ZipUtility.class);

	private static final int BUFFER_SIZE = 8192;

	public List<String> zipProcessing(List<String> listFiles, String currentDateTime, String targetDirectory)
			throws FileNotFoundException, IOException {
		int totalFileSize = listFiles.size();
		List<String> archiveFileList = new LinkedList<String>();
		int reminder = totalFileSize % 500;
		int divided = totalFileSize / 500;
		int iterateCount = divided;
		logger.info("Total number of files archived is " + totalFileSize);
		if (reminder != 0) {
			iterateCount = divided + 1;
		}
		int count = 0;
		int k = 0;
		int initialSize = 0;
		if (totalFileSize <= 500) {
			initialSize = totalFileSize;
		} else {
			initialSize = 500;
		}
		for (int i = 1; i <= iterateCount; i++) {
			String archiveZipFileName = currentDateTime + "_" + i + "-" + ARCHIVE_VALUE + ".zip";
			ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(archiveZipFileName));
			if (i > 1) {
				k = count + 1;
				initialSize = count + 500;
			}
			for (int j = k; j < initialSize; j++) {
				count++;
				System.out.println("count:" + count);
				File file = new File(listFiles.get(j));
				if (file.isDirectory()) {
					zipDirectory(file, file.getName(), zipOutputStream);
				} else {
					zipFile(file, zipOutputStream);
				}
			}
			archiveFileList.add(archiveZipFileName);
			//postProcessingScheduler.copyFileToTargetDirectory(archiveZipFileName, "", targetDirectory);
			//new File(archiveZipFileName).delete();
			zipOutputStream.flush();
			zipOutputStream.close();
			if (count == totalFileSize) {
				continue;
			}
		}
		
		return archiveFileList;
	}

	public void zipDirectory(File folder, String parentFolder, ZipOutputStream zipOutputStream)
			throws FileNotFoundException, IOException {
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				zipDirectory(file, parentFolder + "/" + file.getName(), zipOutputStream);
				continue;
			}
			FileInputStream fileInputStream = new FileInputStream(file);
			zipOutputStream.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
			BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
			long bytesRead = 0;
			byte[] bytesIn = new byte[BUFFER_SIZE];
			int read = 0;
			while ((read = bufferedInputStream.read(bytesIn)) != -1) {
				zipOutputStream.write(bytesIn, 0, read);
				bytesRead += read;
			}
			// logger.info("bytesRead:"+bytesRead);
			fileInputStream.close();
			zipOutputStream.closeEntry();
			bufferedInputStream.close();
		}
	}

	private void zipFile(File file, ZipOutputStream zipOutputStream) throws FileNotFoundException, IOException {
		zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
		FileInputStream fileInputStream = new FileInputStream(file);
		BufferedInputStream bufferInputStream = new BufferedInputStream(fileInputStream);
		long bytesRead = 0;
		byte[] bytesIn = new byte[BUFFER_SIZE];
		int read = 0;
		while ((read = bufferInputStream.read(bytesIn)) != -1) {
			zipOutputStream.write(bytesIn, 0, read);
			bytesRead += read;
		}
		// logger.info("bytesRead:"+bytesRead);
		fileInputStream.close();
		bufferInputStream.close();
		zipOutputStream.closeEntry();
	}
}