package itmo.rshd.backup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

@Service
@Slf4j
public class LocalDataBackupPreparationService {
    private static final String DB_NAME_MONGO = "ZOV";
    private static final String BACKUP_PARENT_DIR_PATH_STR = "./backup";

    // Paths for file system copy operations
    private static final String USER_MOVEMENT_DB_SOURCE_PATH_STR = "F:\\git\\user-activity-simulator\\user_movement_db";
    private static final String FROSTDB_DATA_SOURCE_PATH_STR = "F:\\git\\user-report-db\\frostdb_data";

    // Target subdirectory names within ./backup
    private static final String ZOV_MONGO_TARGET_SUBDIR = DB_NAME_MONGO; // e.g. ./backup/ZOV
    private static final String USER_MOVEMENT_DB_TARGET_SUBDIR = "user_movement_db";
    private static final String FROSTDB_DATA_TARGET_SUBDIR = "frostdb_data";
    private static final String JANUSGRAPH_CASSANDRA_TARGET_SUBDIR = "janusgraph_cassandra_data";

    private final JanusGraphBackupService janusGraphBackupService; // Injected

    @Autowired
    public LocalDataBackupPreparationService(JanusGraphBackupService janusGraphBackupService) {
        this.janusGraphBackupService = janusGraphBackupService;
    }

    public void performFullLocalBackupPreparation() {
        Path backupParentDirPath = Paths.get(BACKUP_PARENT_DIR_PATH_STR);

        try {
            Files.createDirectories(backupParentDirPath);
        } catch (IOException e) {
            log.error("CRITICAL: Failed to create backup parent directory: {}. Aborting all local backup preparation.", backupParentDirPath, e);
            return;
        }

        // 1. Handle ZOV mongodump
        Path zovMongoSpecificBackupPath = backupParentDirPath.resolve(ZOV_MONGO_TARGET_SUBDIR);
        prepareDirectory(zovMongoSpecificBackupPath); // Clears and recreates ./backup/ZOV
        executeMongoDump(backupParentDirPath.toString()); // mongodump --out expects parent, it creates DB_NAME_MONGO subdir

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

        // 5. Handle JanusGraph Cassandra Snapshot & Copy (Now step 4)
        log.info("Starting JanusGraph Cassandra snapshot and copy preparation...");
        janusGraphBackupService.performCassandraSnapshotAndCopy(backupParentDirPath, JANUSGRAPH_CASSANDRA_TARGET_SUBDIR);

        log.info("All local backup data preparation tasks finished.");
    }

    private void prepareDirectory(Path dirPath) {
        deleteDirectoryRecursively(dirPath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("Failed to create directory: {}. This backup component might be skipped or fail.", dirPath, e);
        }
    }

    private void executeMongoDump(String backupOutDirectoryForMongo) {
        try {
            // mongodump command will create a subdirectory named DB_NAME_MONGO inside backupOutDirectoryForMongo
            String command = String.format("mongodump --db %s --out %s", DB_NAME_MONGO, backupOutDirectoryForMongo);
            log.info("Executing mongodump command: {}", command);

            ProcessBuilder processBuilder = new ProcessBuilder();
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            Process process = processBuilder.start();
            // Capture output for logging (simplified, full stream reading in JanusGraphBackupService)
            String stdOutput = new String(process.getInputStream().readAllBytes());
            String stdError = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("Mongodump failed for command '{}' with exit code {}. Stdout: [{}], Stderr: [{}]", command, exitCode, stdOutput, stdError);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error during mongodump process for database {}. Error: {}", DB_NAME_MONGO, e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    private void copySourceToTarget(Path sourcePath, Path targetPath, String targetNameForLogging) {
        if (!Files.exists(sourcePath)) {
            log.warn("Source path for '{}' backup component does not exist, skipping copy: {}", targetNameForLogging, sourcePath);
            return;
        }
        if (!Files.isDirectory(sourcePath)) {
            log.warn("Source path for '{}' backup component is not a directory, skipping copy: {}", targetNameForLogging, sourcePath);
            return;
        }
        try {
            log.info("Attempting to copy '{}' from {} to {}", targetNameForLogging, sourcePath, targetPath);
            copyDirectoryRecursively(sourcePath, targetPath);
            log.info("Successfully copied '{}' from {} to {}", targetNameForLogging, sourcePath, targetPath);
        } catch (IOException e) {
            log.error("Failed to copy '{}' from {} to {}. Error: {}", targetNameForLogging, sourcePath, targetPath, e.getMessage(), e);
        }
    }

    // Helper methods for directory operations (can be moved to a utility class if widely used)
    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        // Ensures target directory is clean and ready before copying directly into it.
        // The 'prepareDirectory' call for the targetPath in the main method handles initial cleaning.
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir); // Create subdirectories in target as needed
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
                    try (var walker = Files.walk(path)) {
                         walker.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(file -> {
                                if (!file.delete()) {
                                    // log.warn("Failed to delete file/directory during recursive delete: {}", file.getAbsolutePath()); // Removed warning
                                }
                            });
                    }
                } else { // It was a file, not a directory
                     Files.delete(path);
                }
            } catch (IOException e) {
                log.error("Error while trying to delete path: {}. It might be locked or in use.", path, e);
            }
        }
    }
} 