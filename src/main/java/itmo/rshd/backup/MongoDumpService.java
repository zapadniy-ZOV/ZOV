package itmo.rshd.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class MongoDumpService {

    private static final Logger log = LoggerFactory.getLogger(MongoDumpService.class);
    private static final String DB_NAME = "ZOV";
    private static final String BACKUP_PARENT_DIR = "./backup"; // mongodump will create DB_NAME subdir here

    public void performMongoDump() {
        Path backupParentPath = Paths.get(BACKUP_PARENT_DIR);
        Path dbSpecificBackupPath = backupParentPath.resolve(DB_NAME); // e.g., ./backup/ZOV

        try {
            Files.createDirectories(backupParentPath);
            log.info("Ensured backup parent directory exists: {}", backupParentPath);

            log.info("Attempting to delete previous dump directory: {}", dbSpecificBackupPath);
            deleteDirectoryRecursively(dbSpecificBackupPath);

            String command = String.format("mongodump --db %s --out %s", DB_NAME, BACKUP_PARENT_DIR);
            log.info("Executing mongodump command: {}", command);

            ProcessBuilder processBuilder = new ProcessBuilder();
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            Process process = processBuilder.start();
            
            // Capture output for logging
            String stdOutput = new String(process.getInputStream().readAllBytes());
            String stdError = new String(process.getErrorStream().readAllBytes());

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Mongodump completed successfully for database {} into {}.", DB_NAME, BACKUP_PARENT_DIR);
                if (!stdOutput.isEmpty()) log.info("Mongodump stdout: {}", stdOutput);
                if (!stdError.isEmpty()) log.warn("Mongodump stderr (though exit code 0): {}", stdError); // Some tools write info to stderr
            } else {
                log.error("Mongodump failed with exit code {}. For command: '{}'", exitCode, command);
                if (!stdOutput.isEmpty()) log.error("Mongodump stdout: {}", stdOutput);
                if (!stdError.isEmpty()) log.error("Mongodump stderr: {}", stdError);
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error during mongodump process for database {}", DB_NAME, e);
            Thread.currentThread().interrupt();
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
                } else { // It's a file
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