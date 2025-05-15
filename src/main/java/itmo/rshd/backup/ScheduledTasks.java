package itmo.rshd.backup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private final BackupService backupService;
    private final MongoDumpService mongoDumpService;

    @Autowired
    public ScheduledTasks(BackupService backupService, MongoDumpService mongoDumpService) {
        this.backupService = backupService;
        this.mongoDumpService = mongoDumpService;
    }
    @Scheduled(cron = "0 1/2 * * * ?")
    public void scheduleS3UploadTask() {
        backupService.createAndUploadBackup();
    }

    @Scheduled(cron = "0 */2 * * * ?")
    public void scheduleMongoDumpTask() {
        mongoDumpService.performMongoDump();
    }
} 