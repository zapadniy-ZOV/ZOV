package itmo.rshd.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    @Value("${backup.bucket.name}")
    private String bucketName;

    private final String backupSourceDir = "./backup";

    public void createAndUploadBackup() {
        Path archivePath = null; 
        String archiveName = null;
        try {
            Path backupDir = Paths.get(backupSourceDir);
            if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
                log.error("Backup source directory '{}' does not exist or is not a directory.", backupSourceDir);
                Files.createDirectories(backupDir);
                log.info("Backup source directory '{}' created.", backupSourceDir);
            }

            String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            archiveName = "DB_" + currentDateTime + ".zip";
            archivePath = Paths.get(archiveName); 

            zipDirectory(backupSourceDir, archiveName);
            log.info("Successfully created backup archive: {}", archiveName);

            uploadToS3(archivePath.toString(), bucketName, archiveName);
            log.info("Successfully uploaded backup archive {} to S3 bucket {}", archiveName, bucketName);

            if (archivePath != null) {
                Files.deleteIfExists(archivePath);
                log.info("Successfully deleted local backup archive: {}", archiveName);
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error during backup process", e);
            if (archivePath != null) { 
                try {
                    Files.deleteIfExists(archivePath);
                    log.info("Deleted local backup archive {} due to an error during the backup process.", archiveName != null ? archiveName : archivePath.getFileName().toString());
                } catch (IOException ex) {
                    log.error("Failed to delete local backup archive {} after an error.", archiveName != null ? archiveName : archivePath.getFileName().toString(), ex);
                }
            }
            Thread.currentThread().interrupt(); 
        }
    }

    private void zipDirectory(String sourceDirName, String zipFileName) throws IOException {
        Path sourceDir = Paths.get(sourceDirName);
        try (FileOutputStream fos = new FileOutputStream(zipFileName);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            log.error("Error while zipping file: {}", path, e);
                            throw new RuntimeException("Error during zipping", e);
                        }
                    });
        }  catch (RuntimeException e) {
            if (e.getCause() instanceof IOException iOException) {
                throw iOException;
            }
            throw e;
        }
    }

    private void uploadToS3(String localFilePath, String s3BucketName, String s3KeyName) throws IOException, InterruptedException {
        String command = String.format("aws s3 cp \"%s\" \"s3://%s/%s\"", localFilePath, s3BucketName, s3KeyName);
        log.info("Executing S3 upload command: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("cmd.exe", "/c", command); // For Windows

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            log.info("File uploaded successfully to S3.");
        } else {
            String errorMessage = new String(process.getErrorStream().readAllBytes());
            log.error("S3 upload failed with exit code {}. Error: {}", exitCode, errorMessage);
            throw new IOException("S3 upload failed: " + errorMessage);
        }
    }
} 