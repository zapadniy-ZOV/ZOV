package itmo.rshd.backup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Service
@Slf4j
public class JanusGraphBackupService {

    private static final String CASSANDRA_CONTAINER_NAME = "cassandra";
    private static final String JANUSGRAPH_KEYSPACE = "janusgraph";
    private static final String CASSANDRA_DATA_PATH_IN_CONTAINER = "/var/lib/cassandra/data/" + JANUSGRAPH_KEYSPACE;

    public void performCassandraSnapshotAndCopy(Path backupPreparationDir, String targetSubdirName) {
        Path cassandraBackupTargetHostDir = backupPreparationDir.resolve(targetSubdirName);
        prepareDirectory(cassandraBackupTargetHostDir);

        String snapshotName = "snapshot_jg_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        try {
            String snapshotCommand = String.format("nodetool snapshot %s -t %s", JANUSGRAPH_KEYSPACE, snapshotName);
            executeDockerCommand(CASSANDRA_CONTAINER_NAME, snapshotCommand, true);

            executeDockerCommand(null, String.format("docker cp %s:%s %s", CASSANDRA_CONTAINER_NAME, CASSANDRA_DATA_PATH_IN_CONTAINER, cassandraBackupTargetHostDir.resolve(JANUSGRAPH_KEYSPACE)), false);

            String clearSnapshotCommand = String.format("nodetool clearsnapshot %s -t %s", JANUSGRAPH_KEYSPACE, snapshotName);
            executeDockerCommand(CASSANDRA_CONTAINER_NAME, clearSnapshotCommand, true);

        } catch (IOException | InterruptedException e) {
            log.error("Error during Cassandra snapshot and copy process for keyspace {}: {}", JANUSGRAPH_KEYSPACE, e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    private void executeDockerCommand(String containerName, String command, boolean isExec) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String fullCommandString;
        String osName = System.getProperty("os.name").toLowerCase();

        if (isExec) {
            if (containerName == null) throw new IllegalArgumentException("Container name must be provided for docker exec.");
            fullCommandString = String.format("docker exec %s %s", containerName, command);
        } else {
            fullCommandString = command;
        }

        if (osName.contains("win")) {
            processBuilder.command("cmd.exe", "/c", fullCommandString);
        } else {
            processBuilder.command("sh", "-c", fullCommandString);
        }

        Process process = processBuilder.start();
        StringBuilder stdOutput = new StringBuilder();
        StringBuilder stdError = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdOutput.append(line).append(System.lineSeparator());
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdError.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        String outputLog = stdOutput.toString().trim();
        String errorLog = stdError.toString().trim();

        if (exitCode != 0) {
            if (!errorLog.isEmpty()) {
                log.error("Command stderr: {}", errorLog);
            }
            throw new IOException(String.format("Command '%s' failed with exit code %d. Stderr: %s", fullCommandString, exitCode, errorLog));
        }
    }

    private void prepareDirectory(Path dirPath) {
        deleteDirectoryRecursively(dirPath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("Failed to create directory: {}", dirPath, e);
            throw new RuntimeException("Failed to prepare directory: " + dirPath, e);
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
                                }
                            });
                        }
                } else {
                     Files.delete(path);
                }
            } catch (IOException e) {
                log.error("Error while trying to delete path: {}. It might be locked or in use.", path, e);
            }
        }
    }
}
