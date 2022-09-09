package com.custom.postprocessing.scheduler;

import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_TEMP_BACKUP_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_VALUE;
import static com.custom.postprocessing.constant.PostProcessingConstant.BACKSLASH_ASCII;
import static com.custom.postprocessing.constant.PostProcessingConstant.BANNER_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.BANNER_PAGE;
import static com.custom.postprocessing.constant.PostProcessingConstant.EMPTY_SPACE;
import static com.custom.postprocessing.constant.PostProcessingConstant.FILE_SEPARATION;
import static com.custom.postprocessing.constant.PostProcessingConstant.LICENSE_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.LOG_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.LOG_FILE;
import static com.custom.postprocessing.constant.PostProcessingConstant.OUTPUT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PCL_EXTENSION;
import static com.custom.postprocessing.constant.PostProcessingConstant.PDF_EXTENSION;
import static com.custom.postprocessing.constant.PostProcessingConstant.PRINT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PRINT_SUB_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PROCESS_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ROOT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.SPACE_VALUE;
import static com.custom.postprocessing.constant.PostProcessingConstant.TRANSIT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.XML_EXTENSION;
import static com.custom.postprocessing.constant.PostProcessingConstant.XML_TYPE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.aspose.pdf.License;
import com.aspose.pdf.facades.PdfFileEditor;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.custom.postprocessing.constant.PostProcessingConstant;
import com.custom.postprocessing.util.EmailUtility;
import com.custom.postprocessing.util.PostProcessUtil;
import com.custom.postprocessing.util.ZipUtility;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

/**
 * @author kumar.charanswain
 *
 */

@Service
public class PostProcessingScheduler {

	public static final Logger logger = LoggerFactory.getLogger(PostProcessingScheduler.class);

	@Value("${blob-account-name-key}")
	private String connectionNameKey;

	@Value("${blob-container-name}")
	private String containerName;

	@Value("#{'${state-allow-type}'.split(',')}")
	private List<String> stateAllowType;

	@Value("#{'${page-type}'.split(',')}")
	private List<String> pageTypeList;

	@Value("${sheet-number-type}")
	private String sheetNbrType;

	@Value("${pcl-evaluation-copies}")
	private boolean pclEvaluationCopies;

	@Value("${empty-file-name}")
	private String blankPdfFileName;

	@Value("${memory-size}")
	private long memorySize;

	@Value("${archive-only}")
	private String archiveOnly;

	@Value("${print-archive}")
	private String printArchive;

	@Value("${document-tag}")
	private String documentTag;

	@Value("${totalsheet-tag}")
	private String totalSheetTag;

	@Value("${file-name-tag}")
	private String fileNameTag;

	@Value("${archive-temp-directory}")
	private String archiveTempDirectory;

	@Value("${license-file-name}")
	private String licenseFileName;

	@Autowired
	EmailUtility emailUtility;

	@Value("${selfAddressed-type}")
	private String selfAddressedType;

	@Value("${cc-recipeint-type}")
	private String ccRecipeintType;

	@Value("${primary-type}")
	private String primaryType;

	@Autowired
	private PostProcessUtil postProcessUtil;

	List<String> pclFileList = new LinkedList<>();

	List<String> fileList = new LinkedList<>();

	@Scheduled(cron = "${cron-job-print-interval}")
	public void postProcessing() {
		String message = smartCommPostProcessing();
		logger.info(message);
	}

	public String smartCommPostProcessing() {
		String currentDateTime = currentDateTimeStamp();
		String currentDate = currentDate();
		String statusMessage = "SmartComm PostProcessing successfully";
		logger.info("postprocessing started");
		try {
			deletePreviousLogFile();
			final CloudBlobContainer container = containerinfo();
			Map<String, String> archiveMap = new ConcurrentHashMap<String, String>();
			moveSourceToTargetDirectory(OUTPUT_DIRECTORY + ARCHIVE_DIRECTORY, OUTPUT_DIRECTORY + PRINT_DIRECTORY, true,
					"print");
			moveSourceToTargetDirectory(OUTPUT_DIRECTORY + PRINT_DIRECTORY,
					OUTPUT_DIRECTORY + ARCHIVE_TEMP_BACKUP_DIRECTORY + "temp-archive_" + currentDateTime + "/", false,
					"temp-archive");
			String transitTargetDirectory = OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/" + currentDate + "-"
					+ PROCESS_DIRECTORY + "/" + currentDateTime + PRINT_SUB_DIRECTORY + "/";
			archiveMap = moveFileToTargetDirectory(OUTPUT_DIRECTORY + PRINT_DIRECTORY, transitTargetDirectory,
					container, currentDate, currentDateTime, archiveMap);

			if (archiveMap.containsKey("nonarchive")) {
				CloudBlobDirectory printDirectory = getDirectoryName(container, OUTPUT_DIRECTORY,
						TRANSIT_DIRECTORY + "/" + currentDate() + "-" + PROCESS_DIRECTORY + "/" + currentDateTime
								+ PRINT_SUB_DIRECTORY + "/");
				statusMessage = processMetaDataInputFile(printDirectory, currentDateTime, currentDate);
				logger.info(statusMessage);
			}

			if (archiveMap.size() == 0) {
				statusMessage = "no file for postprocessing";
			}

			String targetDirectory = OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/" + currentDate + "/";
			zipFileTransferToArchive(OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/" + currentDate + "-" + PROCESS_DIRECTORY
					+ "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY, targetDirectory, currentDate, currentDateTime);

			String logFile = LOG_FILE;
			File logFileName = new File(logFile + ".log");
			File updateLogFile = new File(logFile + "_" + currentDate + ".log");
			if (!(updateLogFile.exists())) {
				Files.copy(logFileName.toPath(), updateLogFile.toPath());
			}
			copyFileToTargetDirectory(updateLogFile.toString(), ROOT_DIRECTORY, LOG_DIRECTORY);
			logFileName.delete();
			updateLogFile.delete();
			if (fileList.size() > 0) {
				for (String file : fileList) {
					File deleteFile = new File(file);
					if (deleteFile.exists()) {
						deleteFile.delete();
					}
				}
			}
		} catch (Exception exception) {
			logger.info("Exception smartComPostProcessing() " + exception.getMessage());
			statusMessage = "error in copy file to blob directory";
		}
		logger.info("postprocessing ended");
		return statusMessage;
	}

	private void moveSourceToTargetDirectory(String sourceDirectory, String targetDirectory, boolean deleteFile,
			String destFolder) {
		BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
		Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(sourceDirectory);
		int count = 0;
		for (BlobItem blobItem : listBlobs) {
			String fileName = getFileName(blobItem.getName());
			fileName = findActualFileName(fileName);
			logger.info("move to " + destFolder + " : " + fileName);
			BlobClient dstBlobClient = blobContainerClient.getBlobClient(targetDirectory + fileName);
			BlobClient srcBlobClient = blobContainerClient.getBlobClient(blobItem.getName());
			String updateSrcUrl = srcBlobClient.getBlobUrl();
			if (srcBlobClient.getBlobUrl().contains(BACKSLASH_ASCII)) {
				updateSrcUrl = srcBlobClient.getBlobUrl().replace(BACKSLASH_ASCII, FILE_SEPARATION);
			}
			dstBlobClient.beginCopy(updateSrcUrl, null);
			count++;
			if (deleteFile) {
				srcBlobClient.delete();
			}
		}
		logger.info("total number of file " + "inside " + destFolder + " is " + count);
	}

	private Map<String, String> moveFileToTargetDirectory(String sourceDirectory, String targetDirectory,
			CloudBlobContainer container, String currentDate, String currentDateTime, Map<String, String> archiveMap) {
		int count = 0;
		try {
			BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
			Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(sourceDirectory);
			CloudBlobDirectory transitDirectory = getDirectoryName(container, OUTPUT_DIRECTORY, PRINT_DIRECTORY);
			for (BlobItem blobItem : listBlobs) {
				String fileName = getFileName(blobItem.getName());
				fileName = findActualFileName(fileName);
				count++;
				logger.info("process file:" + fileName);
				CloudBlockBlob intialFileDownload = transitDirectory.getBlockBlobReference(fileName);
				intialFileDownload.downloadToFile(fileName);
				BlobClient dstBlobClient = blobContainerClient.getBlobClient(targetDirectory + fileName);
				BlobClient srcBlobClient = blobContainerClient.getBlobClient(blobItem.getName());
				String updateSrcUrl = srcBlobClient.getBlobUrl();
				if (srcBlobClient.getBlobUrl().contains(BACKSLASH_ASCII)) {
					updateSrcUrl = srcBlobClient.getBlobUrl().replace(BACKSLASH_ASCII, FILE_SEPARATION);
				}
				if (fileName.contains(archiveOnly)) {
					File archiveOnlyFile = new File(fileName);
					removeArchiveTotalSheetFileElement(archiveOnlyFile, true, currentDate, currentDateTime);
					srcBlobClient.delete();
					archiveMap.put("archive", "true");
				} else if (fileName.contains(printArchive) && !(fileName.contains(ccRecipeintType))) {
					dstBlobClient.beginCopy(updateSrcUrl, null);
					File printArchiveFile = new File(fileName);
					removeArchiveTotalSheetFileElement(printArchiveFile, true, currentDate, currentDateTime);
					archiveMap.put("nonarchive", "true");
					fileList.add(fileName);
					srcBlobClient.delete();
				} else if (fileName.contains(ccRecipeintType)) {
					if (fileName.contains(printArchive)) {
						archiveMap.put("nonarchive", "true");
						String fileNameNoExt = FilenameUtils.removeExtension(fileName);
						String fileNameSplit[] = fileNameNoExt.split("_");
						int ccNumber = 0;
						if (fileNameSplit.length >= 1) {
							ccNumber = Integer.parseInt(fileNameSplit[fileNameSplit.length - 1]);
						}
						primaryRecipeintOperation(fileName, ccNumber, currentDate, currentDateTime);
					}
					srcBlobClient.delete();
				}
			}
			logger.info("total number of file for processing:" + count);
		} catch (Exception exception) {
			logger.info("Exception moveFileToTargetDirectory() :" + exception.getMessage());
		}
		return archiveMap;
	}

	public String processMetaDataInputFile(CloudBlobDirectory transitDirectory, String currentDateTime,
			String currentDate) {
		ConcurrentHashMap<String, List<String>> postProcessMap = new ConcurrentHashMap<>();
		String statusMessage = "SmartComm PostProcessing completed successfully";
		try {
			Iterable<ListBlobItem> blobList = transitDirectory.listBlobs();
			for (ListBlobItem blobItem : blobList) {
				String fileName = getFileNameFromBlobURI(blobItem.getUri()).replace(SPACE_VALUE, EMPTY_SPACE);
				boolean stateType = checkStateType(fileName);
				if (stateType) {
					if (stateType && !(fileName.contains(ccRecipeintType))) {
						if (StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), XML_TYPE)) {
							continue;
						}
						String fileNameNoExt = FilenameUtils.removeExtension(fileName);
						String[] stateAndSheetNameList = StringUtils.split(fileNameNoExt, "_");
						String stateAndSheetName = stateAndSheetNameList.length > 0
								? stateAndSheetNameList[stateAndSheetNameList.length - 1]
								: "";
						prepareMap(postProcessMap, stateAndSheetName, fileName);
					} else if (fileName.contains(ccRecipeintType) && !(fileName.contains(primaryType))) {
						if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(fileName))) {
							continue;
						}
						prepareMap(postProcessMap, getSheetNumber(fileName, blobItem),
								StringUtils.replace(fileName, XML_EXTENSION, PDF_EXTENSION));
					} else if (fileName.contains(primaryType)) {
						if (PostProcessingConstant.XML_TYPE.equals(FilenameUtils.getExtension(fileName))) {
							continue;
						}
						String fileNameNoExt = FilenameUtils.removeExtension(fileName);
						String[] stateAndSheetNameList = fileNameNoExt.split("_ST_");
						if (stateAndSheetNameList.length >= 1) {
							String stateName = stateAndSheetNameList[stateAndSheetNameList.length - 1];
							String stateAndSheetName = stateName.substring(0, 2);
							prepareMap(postProcessMap, stateAndSheetName, fileName);
						}
					}
				} else if (checkPageType(fileName)) {
					if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(fileName))) {
						continue;
					}
					prepareMap(postProcessMap, getSheetNumber(fileName, blobItem),
							StringUtils.replace(fileName, XML_EXTENSION, PDF_EXTENSION));
				} else if (fileName.contains(selfAddressedType)) {
					if (fileName.contains(selfAddressedType) && !(fileName.contains(ccRecipeintType))) {
						if (StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), XML_TYPE)) {
							continue;
						}
						String fileNameNoExt = FilenameUtils.removeExtension(fileName);
						String[] stateAndSheetNameList = StringUtils.split(fileNameNoExt, "_");
						String stateAndSheetName = stateAndSheetNameList.length > 0
								? stateAndSheetNameList[stateAndSheetNameList.length - 1]
								: "";
						prepareMap(postProcessMap, stateAndSheetName, fileName);
					} else if (fileName.contains(ccRecipeintType) && !(fileName.contains(primaryType))) {
						if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(fileName))) {
							continue;
						}
						prepareMap(postProcessMap, getSheetNumber(fileName, blobItem),
								StringUtils.replace(fileName, XML_EXTENSION, PDF_EXTENSION));
					} else if (fileName.contains(primaryType)) {
						if (PostProcessingConstant.XML_TYPE.equals(FilenameUtils.getExtension(fileName))) {
							continue;
						}
						String stateAndSheetName = "SelfAddressed";
						prepareMap(postProcessMap, stateAndSheetName, fileName);
					}
				} else {
					logger.info("unable to process:invalid document type");
				}
			}
			if (postProcessMap.size() > 0) {
				statusMessage = mergePDF(postProcessMap, currentDateTime, currentDate);
			} else {
				statusMessage = "no file for postprocessing";
			}
		} catch (Exception exception) {
			logger.info("Exception processMetaDataInputFile()" + exception.getMessage());
		}
		return statusMessage;
	}

	private String getSheetNumber(String fileName, ListBlobItem blobItem) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// ET - added to mitigate vulnerability - Improper Restriction of XML External
			// Entity Reference CWE ID 611
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			File file = new File(fileName);
			CloudBlob cloudBlob = (CloudBlob) blobItem;
			cloudBlob.downloadToFile(file.getPath());
			Document document = builder.parse(file);
			document.getDocumentElement().normalize();
			Element root = document.getDocumentElement();

			if (Objects.isNull(root.getElementsByTagName(totalSheetTag).item(0))) {
				logger.info("xml file doesn't conains totalSheet element tag:" + fileName);
				file.delete();
				return PostProcessingConstant.ZEROPAGE;
			}

			int sheetNumber = Integer.parseInt(root.getElementsByTagName(totalSheetTag).item(0).getTextContent());
			if (sheetNumber <= 10) {
				file.delete();
				return String.valueOf(sheetNumber);
			}
			file.delete();
		} catch (Exception exception) {
			logger.info("Exception getSheetNumber()", exception.getMessage());
		}
		return PostProcessingConstant.MULTIPAGE;
	}

	public BlobContainerClient getBlobContainerClient(String connectionNameKey, String containerName) {
		BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionNameKey)
				.buildClient();
		return blobServiceClient.getBlobContainerClient(containerName);
	}

	// post merge PDF
	public String mergePDF(ConcurrentHashMap<String, List<String>> postProcessMap, String currentDateTime,
			String currentDate) throws IOException {
		String statusMessage = "SmartComm PostProcessing completed successfully";
		List<String> fileNameList = new LinkedList<>();
		List<String> claimNbrSortedList = new LinkedList<>();
		CloudBlobContainer container = containerinfo();
		ConcurrentHashMap<String, List<String>> updatePostProcessMap = new ConcurrentHashMap<>();
		MemoryUsageSetting memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly(memorySize);
		for (String fileType : postProcessMap.keySet()) {
			try {
				PDFMergerUtility pdfMerger = new PDFMergerUtility();
				fileNameList = postProcessMap.get(fileType);
				String bannerFileName = getBannerPage(fileType);
				logger.info("banner file is:" + bannerFileName);
				File bannerFile = new File(bannerFileName);
				String blankPage = getEmptyPage();
				pdfMerger.addSource(bannerFileName);
				pdfMerger.addSource(blankPage);
				CloudBlobDirectory transitDirectory = getDirectoryName(container,
						OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate() + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + PRINT_SUB_DIRECTORY + "/");
				try {
					for (String fileName : fileNameList) {
						File file = new File(fileName);
						logger.info("process file for pcl is:" + fileName);
						CloudBlockBlob blob = transitDirectory.getBlockBlobReference(fileName);
						blob.downloadToFile(file.getAbsolutePath());
						String claimNbr = fileName.substring(14, fileName.length());
						File claimNbrFileName = new File(claimNbr);
						file.renameTo(claimNbrFileName);
						claimNbrSortedList.add(claimNbrFileName.toString());
						Collections.sort(claimNbrSortedList);
					}
					for (String fileName : claimNbrSortedList) {
						File file = new File(fileName);
						pdfMerger.addSource(file.getPath());
					}
					fileType = postProcessUtil.getFileType(fileType);
					String currentDateTimeStamp = currentDateTimeStamp();
					String mergePdfFile = fileType + "_" + currentDateTimeStamp + PDF_EXTENSION;
					pdfMerger.setDestinationFileName(mergePdfFile);

					pdfMerger.mergeDocuments(memoryUsageSetting);

					statusMessage = convertPDFToPCL(mergePdfFile, container);
					updatePostProcessMap.put(fileType, fileNameList);
					bannerFile.delete();
					// new File(mergePdfFile).delete();
					new File(blankPage).delete();
					deleteFiles(fileNameList);
					deleteFiles(claimNbrSortedList);

				} catch (StorageException storageException) {
					logger.info("invalid file or may be banner file is missing");
					statusMessage = "invalid file or may be banner file is missing";
					if (fileNameList.size() > 0) {
						deleteFiles(fileNameList);
					}
					continue;
				} catch (Exception exception) {
					statusMessage = exception.getMessage();
					if (fileNameList.size() > 0) {
						deleteFiles(fileNameList);
					}
					logger.info("Exception mergePDF()" + exception.getMessage());
				}
			} catch (StorageException storageException) {
				logger.info("invalid file or may be banner file is missing");
				statusMessage = "invalid file or may be banner file is missing";
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				continue;
			} catch (Exception exception) {
				statusMessage = exception.getMessage();
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				logger.info("Exception mergePDF()" + exception.getMessage());
			}
		}

		if (postProcessMap.size() > 0) {
			emailUtility.emailProcess(pclFileList, currentDate,
					"PCL Creation process is completed successfully " + currentDate);
		}

		deleteFiles(pclFileList);

		File licenseFile = new File(licenseFileName);
		licenseFile.delete();
		return statusMessage;
	}

	// post processing PDF to PCL conversion
	public String convertPDFToPCL(String mergePdfFile, CloudBlobContainer container) throws IOException {
		String outputPclFile = FilenameUtils.removeExtension(mergePdfFile) + PCL_EXTENSION;
		String statusMessage = "SmartComm PostProcessing completed successfully";
		try {
			CloudBlobDirectory transitDirectory = getDirectoryName(container, ROOT_DIRECTORY, LICENSE_DIRECTORY);
			CloudBlockBlob blob = transitDirectory.getBlockBlobReference(licenseFileName);
			String licenseFiles[] = blob.getName().split("/");
			String licenseFileName = licenseFiles[licenseFiles.length - 1];
			blob.downloadToFile(new File(licenseFileName).getAbsolutePath());
			License license = new License();
			license.setLicense(licenseFileName);
			statusMessage = pclFileCreation(mergePdfFile, outputPclFile);
		} catch (Exception exception) {
			statusMessage = "The license has expired:no need to print pcl file with evaluation copies";
		}
		if (pclEvaluationCopies) {
			statusMessage = "The license has expired:print pcl file with evaluation copies";
			pclFileCreation(mergePdfFile, outputPclFile);
		}
		return statusMessage;
	}

	public void copyFileToTargetDirectory(String fileName, String rootDirectory, String targetDirectory) {
		try {
			CloudBlobContainer container = containerinfo();
			CloudBlobDirectory processDirectory = getDirectoryName(container, rootDirectory, targetDirectory);
			File outputFileName = new File(fileName);
			if (outputFileName.exists()) {
				CloudBlockBlob processSubDirectoryBlob = processDirectory.getBlockBlobReference(fileName);
				final FileInputStream inputStream = new FileInputStream(outputFileName);
				processSubDirectoryBlob.upload(inputStream, outputFileName.length());
				inputStream.close();
			}

		} catch (Exception exception) {
			logger.info("Exception copyFileToTargetDirectory() " + exception.getMessage());
		}
	}

	public boolean checkStateType(String fileName) {
		for (String state : stateAllowType) {
			if (fileName.contains(state)) {
				return true;
			}
		}
		return false;
	}

	public boolean checkPageType(String fileName) {
		for (String pageType : pageTypeList) {
			if (fileName.contains(pageType)) {
				return true;
			}
		}
		return false;
	}

	public void deleteFiles(List<String> fileNameList) {
		for (String fileName : fileNameList) {
			File file = new File(fileName);
			if (file.exists()) {
				file.delete();
			}
		}
	}

	public void prepareMap(ConcurrentHashMap<String, List<String>> postProcessMap, String key, String fileName) {
		if (postProcessMap.containsKey(key)) {
			List<String> existingFileNameList = postProcessMap.get(key);
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		} else {
			List<String> existingFileNameList = new ArrayList<>();
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		}
	}

	public String getBannerPage(String key)
			throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		CloudBlobContainer container = containerinfo();
		CloudBlobDirectory transitDirectory = getDirectoryName(container, ROOT_DIRECTORY, BANNER_DIRECTORY);
		String bannerFileName = BANNER_PAGE + key + PDF_EXTENSION;
		CloudBlockBlob blob = transitDirectory.getBlockBlobReference(bannerFileName);
		File source = new File(bannerFileName);
		blob.downloadToFile(source.getAbsolutePath());
		return bannerFileName;
	}

	public String getEmptyPage() throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		CloudBlobContainer container = containerinfo();
		CloudBlobDirectory transitDirectory = getDirectoryName(container, ROOT_DIRECTORY, BANNER_DIRECTORY);
		String blankPage = blankPdfFileName + PDF_EXTENSION;
		CloudBlockBlob blob = transitDirectory.getBlockBlobReference(blankPage);
		File source = new File(blankPage);
		blob.downloadToFile(source.getAbsolutePath());
		return blankPage;
	}

	public CloudBlobContainer containerinfo() {
		CloudBlobContainer container = null;
		try {
			CloudStorageAccount account = CloudStorageAccount.parse(connectionNameKey);
			CloudBlobClient serviceClient = account.createCloudBlobClient();
			container = serviceClient.getContainerReference(containerName);
		} catch (Exception exception) {
			logger.info("Exception containerinfo() " + exception.getMessage());
		}
		return container;
	}

	public String currentDate() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		return dateFormat.format(date);
	}

	public String currentDateTimeStamp() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
		return dateFormat.format(date);
	}

	public CloudBlobDirectory getDirectoryName(CloudBlobContainer container, String directoryName,
			String subDirectoryName) throws URISyntaxException {
		CloudBlobDirectory cloudBlobDirectory = container.getDirectoryReference(directoryName);
		if (StringUtils.isBlank(subDirectoryName)) {
			return cloudBlobDirectory;
		}
		return cloudBlobDirectory.getDirectoryReference(subDirectoryName);
	}

	private String getFileNameFromBlobURI(URI blobUri) {
		final String[] fileNameList = blobUri.toString().split("/");
		Optional<String> fileName = Optional.empty();
		if (fileNameList.length > 1)
			fileName = Optional.ofNullable(fileNameList[fileNameList.length - 1]);
		return fileName.get();
	}

	public void deletePreviousLogFile() {
		LocalDate date = LocalDate.now();
		LocalDate previousDate = date.minusDays(1);
		File previousDayLogFile = new File("smartcompostprocessing_backup" + "_" + previousDate + ".0" + ".log");
		if (previousDayLogFile.exists()) {
			previousDayLogFile.delete();
		}
	}

	public void zipFileTransferToArchive(String rootDirectoryName, String targetDirectory, String currentDate,
			String currentDateTime) throws IOException {
		try {
			CloudBlobContainer container = containerinfo();
			BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
			CloudBlobDirectory transitDirectory = getDirectoryName(container, rootDirectoryName, "");
			Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(rootDirectoryName);
			List<String> files = new LinkedList<String>();

			String archiveZipFileName = currentDateTime + "-" + ARCHIVE_VALUE + ".zip";
			for (BlobItem blobItem : listBlobs) {
				String fileNames[] = StringUtils.split(blobItem.getName(), "/");
				String fileName = fileNames[fileNames.length - 1];
				File file = new File(fileName);
				/*
				 * CloudBlockBlob blob = transitDirectory.getBlockBlobReference(fileName);
				 * blob.downloadToFile(file.getPath());
				 */
				files.add(fileName);
			}
			logger.info("Total number of files archived is " + files.size());

			if (files.size() > 0) {
				ZipUtility zipUtility = new ZipUtility();
				List<String> archiveFileLIst = zipUtility.zipProcessing(files, currentDateTime, targetDirectory);
				copyFileToTargetDirectory(archiveZipFileName, "", targetDirectory);
				deleteFiles(files);
				new File(archiveZipFileName).delete();
			}
			File prcoessComplete = processCompleteFile();
			copyFileToTargetDirectory(prcoessComplete.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY, currentDate);
			if (prcoessComplete.exists()) {
				prcoessComplete.delete();
			}

		} catch (Exception exception) {
			logger.info("zipFileTransferToArchive() exception:" + exception.getMessage());
		}
	}

	public String getFileName(String blobName) {
		return blobName.replace(OUTPUT_DIRECTORY, "");
	}

	public String findActualFileName(String fileName) {
		String updateFileName = "";
		if (StringUtils.isNoneEmpty(fileName)) {
			String fileNames[] = fileName.split("/");
			updateFileName = fileNames[fileNames.length - 1];
		}
		return updateFileName;
	}

	public String pclFileCreation(String mergePdfFile, String outputPclFile) {
		String statusMessage = "SmartComm PostProcessing completed successfully";
		try {
			PdfFileEditor fileEditor = new PdfFileEditor();
			final InputStream stream = new FileInputStream(mergePdfFile);
			final InputStream[] streamList = new InputStream[] { stream };
			final OutputStream outStream = new FileOutputStream(outputPclFile);
			fileEditor.concatenate(streamList, outStream);
			stream.close();
			outStream.close();
			fileEditor.setCloseConcatenatedStreams(true);
			String currentDate = currentDate();
			copyFileToTargetDirectory(outputPclFile, OUTPUT_DIRECTORY + TRANSIT_DIRECTORY, currentDate);
			logger.info("Genetaed pcl file is:" + outputPclFile);
			pclFileList.add(outputPclFile);
		} catch (Exception exception) {
			statusMessage = "error in pcl generate";
			logger.info("Exception pclFileCreation() " + exception.getMessage());
		}
		return statusMessage;
	}

	public void removeArchiveTotalSheetFileElement(File file, boolean renameFile, String currentDate,
			String currentDateTime) {
		try {
			if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(file.toString()))) {
				String[] splitFileName = file.toString().split("_");
				File updatePDFFile = new File(splitFileName[0] + ".pdf");
				file.renameTo(updatePDFFile);
				copyFileToTargetDirectory(updatePDFFile.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);
				// updatePDFFile.delete();

			} else if (PostProcessingConstant.XML_TYPE.equals(FilenameUtils.getExtension(file.toString()))) {
				/*
				 * File dest = new File("copy_" + file.toString()); if (!(dest.exists())) {
				 * Files.copy(file.toPath(), dest.toPath()); }
				 */

				Document document = xmlFileDocumentReader(file.toString());
				if (Objects.isNull(document)) {
					logger.info("error in read xml processing file ");
				}

				document.getDocumentElement().normalize();
				Element root = document.getDocumentElement();
				String claimNumber = file.toString();

				final Node node = document.getElementsByTagName(documentTag).item(0);
				final NodeList nodeList = node.getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++) {
					final Node documentNode = nodeList.item(i);
					if (documentNode.getNodeName().equals(totalSheetTag)) {
						node.removeChild(documentNode);
					}
					if (documentNode.getNodeName().equals(fileNameTag)) {
						claimNumber = root.getElementsByTagName(fileNameTag).item(0).getTextContent();
					}
				}
				document.normalize();
				TransformerFactory transferFactory = TransformerFactory.newInstance();
				Transformer transformerReference = transferFactory.newTransformer();
				transformerReference.setOutputProperty(OutputKeys.INDENT, "yes");
				// transformerReference.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
				// "2");

				File updateXMLFile = new File(claimNumber + ".xml");

				final DOMSource source = new DOMSource(document);
				final StreamResult streamResult = new StreamResult(file);
				transformerReference.transform(source, streamResult);
				file.renameTo(updateXMLFile);
				// fileList.add("copy_" + file.toString());
				copyFileToTargetDirectory(updateXMLFile.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);
				// updateXMLFile.delete();
			}

		} catch (TransformerException fileTransferException) {
			logger.info("Exception archiveFileRemoveElement() " + fileTransferException.getMessage());
		} catch (Exception exception) {
			logger.info("Exception archiveFileRemoveElement() " + exception.getMessage());
		}
	}

	public void splitCCRecipeintPDFFile(File fileName, int recipeintCount, String currentDate, String currentDateTime) {
		try {
			if (fileName.toString().contains("printArchive")) {
				PDDocument splitDocument = PDDocument.load(fileName);
				Splitter splitter = new Splitter();
				splitter.setSplitAtPage(2);
				List<PDDocument> Pages = splitter.split(splitDocument);
				Iterator<PDDocument> iterator = Pages.listIterator();
				int i = 1;
				int count = 0;
				List<String> pdfListFile = new LinkedList<>();
				String fileSplitName = FilenameUtils.removeExtension(fileName.toString());
				PDDocument pdDocument = null;
				while (iterator.hasNext()) {
					pdDocument = iterator.next();
					count++;
					pdDocument.save("split" + i++ + ".pdf");
					pdDocument.close();
				}
				splitDocument.close();
				MemoryUsageSetting memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly();
				PDFMergerUtility splitPdfMerger = new PDFMergerUtility();
				for (int a = recipeintCount + 1; a <= count; a++) {
					splitPdfMerger.addSource("split" + a + ".pdf");

				}
				splitPdfMerger.setDestinationFileName("primary" + ".pdf");
				splitPdfMerger.mergeDocuments(memoryUsageSetting);
				pdfListFile.add("primary" + ".pdf");
				File primaryCCRecipeint = new File("primary" + ".pdf");
				File updatePrimaryFileName = new File(fileSplitName + primaryType + ".pdf");
				primaryCCRecipeint.renameTo(updatePrimaryFileName);
				copyFileToTargetDirectory(updatePrimaryFileName.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + PRINT_DIRECTORY);

				for (int j = 1; j <= recipeintCount; j++) {
					PDFMergerUtility pdfMerger = new PDFMergerUtility();
					pdfMerger.addSource("split" + j + ".pdf");
					pdfMerger.addSource(updatePrimaryFileName.toString());
					pdfListFile.add("split" + j + ".pdf");
					pdfMerger.setDestinationFileName(fileSplitName + "_" + j + ".pdf");
					pdfMerger.mergeDocuments(memoryUsageSetting);
					copyFileToTargetDirectory(fileSplitName + "_" + j + ".pdf", "",
							OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/" + currentDate + "-" + PROCESS_DIRECTORY + "/"
									+ currentDateTime + PRINT_SUB_DIRECTORY + "/");
				}

				String dcnClaimNbr = findPrimaryFileName(updatePrimaryFileName.toString(), "_");
				File dcnClainNbrFileName = new File(dcnClaimNbr + PDF_EXTENSION);
				updatePrimaryFileName.renameTo(dcnClainNbrFileName);
				copyFileToTargetDirectory(dcnClainNbrFileName.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);

				for (int k = 1; k <= count; k++) {
					File deleteFile = new File("split" + k + ".pdf");
					if (deleteFile.exists()) {
						deleteFile.delete();
					}
				}
			} else if (fileName.toString().contains("archiveOnly")) {
				copyFileToTargetDirectory(fileName.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);
			}
			fileList.add(fileName.toString());
		} catch (Exception exception) {
			System.out.println("exception:" + exception.getMessage());
		}
	}

	public void primaryRecipeintOperation(String fileName, int ccNumberCount, String currentDate,
			String currentDateTime) {
		totalNumberOfPagesCount(fileName, ccNumberCount, currentDate, currentDateTime);
	}

	public void totalNumberOfPagesCount(String fileName, int ccNumberCount, String currentDate,
			String currentDateTime) {
		try {
			File file = new File(fileName);
			if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(fileName))) {
				splitCCRecipeintPDFFile(file, ccNumberCount, currentDate, currentDateTime);
			} else if (PostProcessingConstant.XML_TYPE.equals(FilenameUtils.getExtension(fileName))) {
				Document document = xmlFileDocumentReader(fileName);
				if (Objects.isNull(document)) {
					logger.info("error in read xml processing file ");
				}
				Element root = document.getDocumentElement();
				Integer sheetNumber = Integer
						.parseInt(root.getElementsByTagName(totalSheetTag).item(0).getTextContent());
				sheetNumber = sheetNumber - ccNumberCount;
				Integer numberOfPages = Integer
						.parseInt(root.getElementsByTagName("NumberOfPages").item(0).getTextContent());
				numberOfPages = numberOfPages - (ccNumberCount * 2);

				final Node node = document.getElementsByTagName(documentTag).item(0);
				final NodeList nodeList = node.getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++) {
					final Node documentNode = nodeList.item(i);
					if (documentNode.getNodeName().equals("NumberOfPages")) {
						documentNode.setTextContent(numberOfPages.toString());
					}
					if (documentNode.getNodeName().equals(totalSheetTag)) {
						documentNode.setTextContent(sheetNumber.toString());
					}
				}
				document.normalize();
				TransformerFactory transferFactory = TransformerFactory.newInstance();
				Transformer transformerReference = transferFactory.newTransformer();
				transformerReference.setOutputProperty(OutputKeys.INDENT, "yes");
				String updatePrimaryXmlName = FilenameUtils.removeExtension(fileName.toString());

				File updateXmlFile = new File(updatePrimaryXmlName + primaryType + ".xml");
				final DOMSource source = new DOMSource(document);
				final StreamResult streamResult = new StreamResult(fileName);
				transformerReference.transform(source, streamResult);
				File dest = new File("copy_" + updateXmlFile.toString());
				if (!(dest.exists())) {
					Files.copy(file.toPath(), dest.toPath());
				}
				file.renameTo(updateXmlFile);

				copyFileToTargetDirectory(updateXmlFile.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + PRINT_DIRECTORY);

				// File updateFile = removeArchiveFileElement(updateXmlFile, false);

				removeArchiveTotalSheetFileElement(updateXmlFile, false, currentDate, currentDateTime);

				String dcnClaimNbr = findPrimaryFileName("test", "_");
				File dcnClainNbrFileName = new File(dcnClaimNbr + XML_EXTENSION);
				updateXmlFile.renameTo(dcnClainNbrFileName);
				copyFileToTargetDirectory(dcnClainNbrFileName.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);
				splitCCRecipeintXmlFile(dest, sheetNumber, numberOfPages, ccNumberCount, currentDate, currentDateTime);
			}
		} catch (Exception exception) {
			logger.info("Exception totalNumberOfPagesCount() " + exception.getMessage());
		}
	}

	public void splitCCRecipeintXmlFile(File xmlFile, Integer sheetNumber, Integer numberOfPages, int ccNumberCount,
			String currentDate, String currentDateTime) {
		String fileName = "" + xmlFile.toString();
		try {
			if (xmlFile.toString().contains("printArchive")) {
				for (int i = 1; i <= ccNumberCount; i++) {
					String newFileName = FilenameUtils.removeExtension(xmlFile.toString());
					String fileNameList[] = newFileName.split("copy_");
					newFileName = fileNameList[fileNameList.length - 1] + "_" + i + ".xml";
					newFileName = newFileName.replace(primaryType, "");

					Document document = xmlFileDocumentReader(fileName);
					if (Objects.isNull(document)) {
						logger.info("error in read xml processing file ");
					}
					if (i == 1) {
						sheetNumber = sheetNumber + 1;
						numberOfPages = numberOfPages + 2;
					}
					final Node node = document.getElementsByTagName(documentTag).item(0);
					final NodeList nodeList = node.getChildNodes();
					for (int j = 0; j < nodeList.getLength(); j++) {
						final Node documentNode = nodeList.item(j);
						if (documentNode.getNodeName().equals("NumberOfPages")) {
							documentNode.setTextContent(numberOfPages.toString());
						}
						if (documentNode.getNodeName().equals(totalSheetTag)) {
							documentNode.setTextContent(sheetNumber.toString());
						}
					}
					document.normalize();
					TransformerFactory transferFactory = TransformerFactory.newInstance();
					Transformer transformerReference = transferFactory.newTransformer();
					transformerReference.setOutputProperty(OutputKeys.INDENT, "yes");
					// transformerReference.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
					// "2");

					File updateXmlFile = new File(newFileName);
					final DOMSource source = new DOMSource(document);
					final StreamResult streamResult = new StreamResult(updateXmlFile);
					transformerReference.transform(source, streamResult);
					xmlFile.renameTo(updateXmlFile);
					copyFileToTargetDirectory(updateXmlFile.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
							currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + PRINT_DIRECTORY);

					fileList.add(newFileName);
				}
			}
			fileList.add(fileName);
		} catch (Exception exception) {
			logger.info("Exception splitCCRecipeintXmlFile() " + exception.getMessage());
		}

	}

	public String findPrimaryFileName(String fileName, String splitType) {
		String updateFileName = "";
		if (StringUtils.isNoneEmpty(fileName)) {
			String fileNames[] = fileName.split(splitType);
			updateFileName = fileNames[0];
		}
		return updateFileName;
	}

	public File processCompleteFile() {
		File file = null;
		try {
			String documentFileName = "process-completed" + ".txt";
			file = new File(documentFileName);
			final FileOutputStream outputStream = new FileOutputStream(file);
			PrintWriter writer = new PrintWriter(outputStream);
			writer.println("process completed" + '\n');
			outputStream.close();
			writer.close();
		} catch (Exception exception) {
			logger.info("Exception addAttachment() :" + exception.getMessage());
		}
		return file;
	}

	public Document xmlFileDocumentReader(String fileName)
			throws ParserConfigurationException, SAXException, IOException {
		Document document = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// ET - added to mitigate vulnerability - Improper Restriction of XML External
			// Entity Reference CWE ID 611
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(new File(fileName));
			document.getDocumentElement().normalize();
		} catch (Exception documentException) {
			logger.info("Exception xmlFileDocumentReader() :" + documentException.getMessage());
		}
		return document;
	}
}