package itmo.rshd.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    @Value("${backup.bucket.name}")
    private String bucketName;

    private static final String DB_NAME = "ZOV";
    private static final String BACKUP_PARENT_DIR = "./backup";
    private static final Path temporaryArchiveParentDir = Paths.get("."); // Store .zip in project root temporarily

    public void createAndUploadBackup() {
        Path dbSpecificBackupPath = Paths.get(BACKUP_PARENT_DIR, DB_NAME); // e.g., ./backup/ZOV

        log.info("Attempting to backup and upload data from: {}", dbSpecificBackupPath);

        if (!Files.exists(dbSpecificBackupPath) || !Files.isDirectory(dbSpecificBackupPath)) {
            log.warn("Backup source directory {} does not exist or is not a directory. Skipping S3 upload.", dbSpecificBackupPath);
            return;
        }

        try {
            if (isDirEmpty(dbSpecificBackupPath)) {
                log.warn("Backup source directory {} is empty. Skipping S3 upload.", dbSpecificBackupPath);
                return;
            }
        } catch (IOException e) {
            log.error("Failed to check if directory {} is empty. Skipping S3 upload.", dbSpecificBackupPath, e);
            return;
        }

        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String archiveName = "DB_" + DB_NAME + "_" + currentDateTime + ".zip"; // e.g., DB_ZOV_20231027_103000.zip
        Path archivePath = temporaryArchiveParentDir.resolve(archiveName); // e.g., ./DB_ZOV_....zip

        try {
            log.info("Creating archive {} from {}", archivePath, dbSpecificBackupPath);
            zipDirectory(dbSpecificBackupPath, archivePath);
            log.info("Successfully created backup archive: {}", archivePath);

            log.info("Uploading archive {} to S3 bucket {}", archiveName, bucketName);
            uploadToS3(archivePath.toString(), bucketName, archiveName);
            log.info("Successfully uploaded backup archive {} to S3 bucket {}", archiveName, bucketName);

            // Cleanup after successful upload
            Files.deleteIfExists(archivePath);
            log.info("Successfully deleted local temporary archive: {}", archivePath);

            log.info("Deleting original backup data directory after successful upload: {}", dbSpecificBackupPath);
            deleteDirectoryRecursively(dbSpecificBackupPath); // Key step: delete ./backup/ZOV

        } catch (IOException | InterruptedException e) {
            log.error("Error during backup and S3 upload process for {}. Error: {}", dbSpecificBackupPath, e.getMessage(), e);
            // Attempt to delete the local temporary archive if it was created
            if (Files.exists(archivePath)) {
                try {
                    Files.deleteIfExists(archivePath);
                    log.info("Deleted local temporary archive {} due to an error.", archivePath);
                } catch (IOException ex) {
                    log.error("Failed to delete local temporary archive {} after an error.", archivePath, ex);
                }
            }
            // Do NOT delete dbSpecificBackupPath here, so it can be picked up by the next run.
            Thread.currentThread().interrupt();
        }
    }

    private void zipDirectory(Path sourceDir, Path zipFilePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourceDir)
                .filter(path -> !Files.isDirectory(path)) // only zip files
                .forEach(path -> {
                    try {
                        // Create relative path for zip entry to maintain folder structure within zip
                        String entryName = sourceDir.relativize(path).toString().replace("\\", "/");
                        if (entryName.isEmpty()) return; 

                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.error("Error while zipping path: {}", path, e);
                        // Wrap and throw to be caught by the outer try-catch in createAndUploadBackup
                        throw new RuntimeException("Error during zipping path: " + path, e);
                    }
                });
        } catch (RuntimeException e) {
            // Unwrap the IOException if it was wrapped for the lambda
            if (e.getCause() instanceof IOException iOException) {
                throw iOException;
            }
            throw e; // Re-throw if it's another RuntimeException
        }
    }

    private void uploadToS3(String localFilePath, String s3BucketName, String s3KeyName) throws IOException, InterruptedException {
        String command = String.format("aws s3 cp \"%s\" \"s3://%s/%s\"", localFilePath, s3BucketName, s3KeyName);
        log.info("Executing S3 upload command: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder();
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }

        Process process = processBuilder.start();
        
        String stdOutput = new String(process.getInputStream().readAllBytes());
        String stdError = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            log.info("File uploaded successfully to S3.");
            if (!stdOutput.isEmpty()) log.info("AWS S3 cp stdout: {}", stdOutput);
            if (!stdError.isEmpty()) log.warn("AWS S3 cp stderr (though exit code 0): {}", stdError);
        } else {
            log.error("S3 upload failed with exit code {}. For command: '{}'", exitCode, command);
            if (!stdOutput.isEmpty()) log.error("AWS S3 cp stdout: {}", stdOutput);
            if (!stdError.isEmpty()) log.error("AWS S3 cp stderr: {}", stdError);
            throw new IOException("S3 upload failed. Exit code: " + exitCode + ". Stderr: " + stdError + ". Stdout: " + stdOutput);
        }
    }

    private void deleteDirectoryRecursively(Path path) {
         if (Files.exists(path)) {
            try {
                if (Files.isDirectory(path)) {
                    Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                log.warn("Failed to delete file/directory: {}", file.getAbsolutePath());
                            }
                        });
                    log.info("Successfully deleted directory and its contents: {}", path);
                } else { 
                     Files.delete(path);
                     log.info("Successfully deleted file: {}", path);
                }
            } catch (IOException e) {
                log.error("Error while trying to delete path: {}", path, e);
            }
        } else {
            log.info("Path to delete does not exist, skipping deletion: {}", path);
        }
    }

    private boolean isDirEmpty(final Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            // This case should ideally be caught by the check before calling isDirEmpty
            return true; 
        }
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            return !dirStream.iterator().hasNext();
        }
    }
} 