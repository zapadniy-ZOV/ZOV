package itmo.rshd.backup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class BackupService {

    @Value("${backup.bucket.name}")
    private String bucketName;

    private static final String BACKUP_ROOT_DIR_PATH_STR = "./backup";
    // Define ALL managed subdirectories within ./backup
    private static final List<String> MANAGED_BACKUP_SUBDIRS = Arrays.asList(
        "ZOV", 
        "user_movement_db", 
        "frostdb_data",
        "janusgraph_export",         // Added for GraphML export
        "janusgraph_cassandra_data"  // Added for Cassandra data copy
    );
    private static final Path TEMPORARY_ARCHIVE_PARENT_DIR = Paths.get(".");

    public void createAndUploadBackup() {
        Path backupRootDir = Paths.get(BACKUP_ROOT_DIR_PATH_STR);

        log.info("Attempting to backup and upload data from: {}", backupRootDir);

        if (!Files.exists(backupRootDir) || !Files.isDirectory(backupRootDir)) {
            log.warn("Backup root directory {} does not exist or is not a directory. Skipping S3 upload.", backupRootDir);
            return;
        }

        try {
            if (isDirectoryEffectivelyEmptyForBackup(backupRootDir)) {
                log.warn("Backup root directory {} is effectively empty (no managed subdirectories found or they are all empty). Skipping S3 upload.", backupRootDir);
                return;
            }
        } catch (IOException e) {
            log.error("Failed to check if directory {} is effectively empty. Skipping S3 upload.", backupRootDir, e);
            return;
        }

        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String archiveName = "BACKUP_COMPOSITE_" + currentDateTime + ".zip";
        Path archivePath = TEMPORARY_ARCHIVE_PARENT_DIR.resolve(archiveName);

        try {
            log.info("Creating archive {} from all contents of {}", archivePath, backupRootDir);
            zipDirectory(backupRootDir, archivePath);
            log.info("Successfully created composite backup archive: {}", archivePath);

            log.info("Uploading archive {} to S3 bucket {}", archiveName, bucketName);
            uploadToS3(archivePath.toString(), bucketName, archiveName);
            log.info("Successfully uploaded composite backup archive {} to S3 bucket {}", archiveName, bucketName);

            Files.deleteIfExists(archivePath);
            log.info("Successfully deleted local temporary archive: {}", archivePath);

            log.info("Deleting managed subdirectories from {} after successful upload.", backupRootDir);
            for (String subdirName : MANAGED_BACKUP_SUBDIRS) {
                // Important: Resolve subdir against backupRootDir to get the correct path to delete.
                deleteDirectoryRecursively(backupRootDir.resolve(subdirName)); 
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error during backup and S3 upload process for {}. Error: {}", backupRootDir, e.getMessage(), e);
            if (Files.exists(archivePath)) {
                try {
                    Files.deleteIfExists(archivePath);
                    log.info("Deleted local temporary archive {} due to an error.", archivePath);
                } catch (IOException ex) {
                    log.error("Failed to delete local temporary archive {} after an error.", archivePath, ex);
                }
            }
            Thread.currentThread().interrupt();
        }
    }

    private void zipDirectory(Path sourceDir, Path zipFilePath) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            log.warn("Source directory for zipping does not exist or is not a directory: {}. Zip will be empty or not created.", sourceDir);
            try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                // Creates an empty zip file
            }
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(sourceDir)) { // Avoid adding the root dir itself as an entry if it's empty at top level
                        String entryName = sourceDir.relativize(dir).toString().replace("\\", "/") + "/";
                        if (!entryName.equals("/")) { // Ensure we don't add an empty root entry if sourceDir was like "."
                             zos.putNextEntry(new ZipEntry(entryName));
                             zos.closeEntry();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = sourceDir.relativize(file).toString().replace("\\", "/");
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
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
        // Simplified output reading as in JanusGraphBackupService for consistency, can be expanded if needed
        StringBuilder stdOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line; while ((line = reader.readLine()) != null) { stdOutput.append(line).append(System.lineSeparator()); }
        }
        StringBuilder stdError = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line; while ((line = reader.readLine()) != null) { stdError.append(line).append(System.lineSeparator()); }
        }
        int exitCode = process.waitFor();
        String outputLog = stdOutput.toString().trim();
        String errorLog = stdError.toString().trim();
        if (!outputLog.isEmpty()) log.info("AWS S3 cp stdout: {}", outputLog);

        if (exitCode == 0) {
            log.info("File uploaded successfully to S3.");
            if (!errorLog.isEmpty()) log.warn("AWS S3 cp stderr (though exit code 0): {}", errorLog);
        } else {
            log.error("S3 upload failed with exit code {}. For command: '{}'. Stderr: {}", exitCode, command, errorLog);
            throw new IOException("S3 upload failed. Exit code: " + exitCode + ". Stderr: " + errorLog);
        }
    }

    private void deleteDirectoryRecursively(Path path) {
         if (Files.exists(path)) {
            try {
                if (Files.isDirectory(path)) {
                     try (var walker = Files.walk(path)) {
                        walker.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(file -> {
                                if (!file.delete()) {
                                    log.warn("Failed to delete file/directory: {}", file.getAbsolutePath());
                                }
                            });
                    }
                    log.info("Successfully deleted directory and its contents: {}", path);
                } else { 
                     Files.delete(path);
                     log.info("Successfully deleted file (expected directory but found file): {}", path);
                }
            } catch (IOException e) {
                log.error("Error while trying to delete path: {}. It might be locked or in use.", path, e);
            }
        } else {
            log.info("Path to delete does not exist, skipping deletion: {}", path);
        }
    }

    private boolean isDirectoryEffectivelyEmptyForBackup(final Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return true; 
        }
        boolean foundManagedContent = false;
        for (String subdirName : MANAGED_BACKUP_SUBDIRS) {
            Path managedSubdirPath = directory.resolve(subdirName);
            if (Files.exists(managedSubdirPath) && Files.isDirectory(managedSubdirPath)) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(managedSubdirPath)) {
                    if (dirStream.iterator().hasNext()) {
                        foundManagedContent = true;
                        break;
                    }
                }
            }
        }
        if (!foundManagedContent) {
            log.info("Directory {} is considered effectively empty as no non-empty managed subdirectories were found.", directory);
        }
        return !foundManagedContent;
    }
} 