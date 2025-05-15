package itmo.rshd.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class MongoDumpService {

    private static final Logger log = LoggerFactory.getLogger(MongoDumpService.class);
    private static final String DB_NAME = "ZOV";
    private static final String BACKUP_PARENT_DIR_PATH_STR = "./backup";

    // New data sources
    private static final String USER_MOVEMENT_DB_SOURCE_PATH_STR = "F:\\git\\user-activity-simulator\\user_movement_db";
    private static final String FROSTDB_DATA_SOURCE_PATH_STR = "F:\\git\\user-report-db\\frostdb_data";

    private static final String ZOV_TARGET_SUBDIR = DB_NAME;
    private static final String USER_MOVEMENT_DB_TARGET_SUBDIR = "user_movement_db";
    private static final String FROSTDB_DATA_TARGET_SUBDIR = "frostdb_data";

    public void performFullLocalBackupPreparation() {
        Path backupParentDirPath = Paths.get(BACKUP_PARENT_DIR_PATH_STR);

        try {
            Files.createDirectories(backupParentDirPath);
            log.info("Ensured backup parent directory exists: {}", backupParentDirPath);
        } catch (IOException e) {
            log.error("Failed to create backup parent directory: {}. Aborting backup preparation.", backupParentDirPath, e);
            return;
        }

        // 1. Handle ZOV mongodump
        Path zovSpecificBackupPath = backupParentDirPath.resolve(ZOV_TARGET_SUBDIR);
        prepareDirectory(zovSpecificBackupPath);
        executeMongoDump(backupParentDirPath.toString()); // mongodump --out expects parent, it creates DB_NAME subdir

        // 2. Handle user_movement_db copy
        Path userMovementSourcePath = Paths.get(USER_MOVEMENT_DB_SOURCE_PATH_STR);
        Path userMovementTargetPath = backupParentDirPath.resolve(USER_MOVEMENT_DB_TARGET_SUBDIR);
        prepareDirectory(userMovementTargetPath);
        copySourceToTarget(userMovementSourcePath, userMovementTargetPath, USER_MOVEMENT_DB_TARGET_SUBDIR);

        // 3. Handle frostdb_data copy
        Path frostDbSourcePath = Paths.get(FROSTDB_DATA_SOURCE_PATH_STR);
        Path frostDbTargetPath = backupParentDirPath.resolve(FROSTDB_DATA_TARGET_SUBDIR);
        prepareDirectory(frostDbTargetPath);
        copySourceToTarget(frostDbSourcePath, frostDbTargetPath, FROSTDB_DATA_TARGET_SUBDIR);

        log.info("Local backup data preparation finished.");
    }

    private void prepareDirectory(Path dirPath) {
        log.info("Preparing directory: {}. Cleaning if exists.", dirPath);
        deleteDirectoryRecursively(dirPath);
        try {
            Files.createDirectories(dirPath);
            log.info("Successfully created directory: {}", dirPath);
        } catch (IOException e) {
            log.error("Failed to create directory: {}", dirPath, e);
            // Depending on desired strictness, might throw an exception or return a status
        }
    }

    private void executeMongoDump(String backupOutDirectory) {
        try {
            String command = String.format("mongodump --db %s --out %s", DB_NAME, backupOutDirectory);
            log.info("Executing mongodump command: {}", command);

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
                log.info("Mongodump completed successfully for database {} into {}.", DB_NAME, backupOutDirectory);
                if (!stdOutput.isEmpty()) log.info("Mongodump stdout: {}", stdOutput);
                if (!stdError.isEmpty()) log.warn("Mongodump stderr (exit code 0): {}", stdError);
            } else {
                log.error("Mongodump failed for '{}' with exit code {}. Stdout: [{}], Stderr: [{}]", command, exitCode, stdOutput, stdError);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error during mongodump process for database {}", DB_NAME, e);
            Thread.currentThread().interrupt();
        }
    }

    private void copySourceToTarget(Path sourcePath, Path targetPath, String targetName) {
        if (!Files.exists(sourcePath)) {
            log.warn("Source path for {} does not exist, skipping copy: {}", targetName, sourcePath);
            return;
        }
        try {
            log.info("Attempting to copy from {} to {}", sourcePath, targetPath);
            copyDirectoryRecursively(sourcePath, targetPath);
            log.info("Successfully copied {} to {}", sourcePath, targetPath);
        } catch (IOException e) {
            log.error("Failed to copy {} from {} to {}. Error: {}", targetName, sourcePath, targetPath, e.getMessage(), e);
        }
    }

    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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
                                log.warn("Failed to delete file/directory during recursive delete: {}", file.getAbsolutePath());
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
} 