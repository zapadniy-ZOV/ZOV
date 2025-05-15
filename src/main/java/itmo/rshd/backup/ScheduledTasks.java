package itmo.rshd.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);
    private final BackupService backupService;

    public ScheduledTasks(BackupService backupService) {
        this.backupService = backupService;
    }

    // Cron expression for daily at 2 AM: "0 0 2 * * ?"
    // For testing, let's set it to run every 5 minutes: "0 */5 * * * ?"
    // The user can change this cron expression as needed.
    @Scheduled(cron = "0 */2 * * * ?") // Daily at 2 AM
    public void scheduleBackupTask() {
        logger.info("Starting scheduled backup process...");
        backupService.createAndUploadBackup();
        logger.info("Scheduled backup process finished.");
    }
} 