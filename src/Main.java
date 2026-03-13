import java.io.IOException;
import java.io.ObjectInputFilter.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss");
    private static final int DEFAULT_LOOKBACK_DAYS = 366;


    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);

        if (config.endDate.isBefore(config.startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        if (config.maxCommitsPerDay < config.minCommitsPerDay) {
            throw new IllegalArgumentException("max commits per day must be >= min commits per day.");
        }

        Path activityPath = Path.of(config.activityFilePath);
        if (activityPath.getParent() != null) {
            Files.createDirectories(activityPath.getParent());
        }
        Random random = new Random();
        int totalCommits = 0;

        System.out.printf("Starting commit generation from %s to %s%n", config.startDate,
                config.endDate);
        if (args.length == 0) {
            System.out.printf("No date args provided, using default last %d days (inclusive).%n",
                    DEFAULT_LOOKBACK_DAYS);
        }
        System.out.printf("Activity file: %s%n", activityPath);
        if (config.dryRun) {
            System.out.println("Dry run mode is ON (no git commands will be executed).");
        }

        for (LocalDate date = config.startDate; !date.isAfter(config.endDate);
             date = date.plusDays(1)) {
            int commitsForDay = config.minCommitsPerDay
                    + random.nextInt(config.maxCommitsPerDay - config.minCommitsPerDay + 1);

            for (int i = 1; i <= commitsForDay; i++) {
                LocalTime randomTime = LocalTime.of(
                        9 + random.nextInt(10),
                        random.nextInt(60),
                        random.nextInt(60)
                );

                LocalDateTime dateTime = LocalDateTime.of(date, randomTime);
                String dateTimeText = dateTime.format(DATE_TIME_FORMAT);
                String line =
                        "Commit made on " + date.format(DATE_FORMAT) + " - #" + i + " (" + dateTimeText + ")\n";

                if (config.dryRun) {
                    System.out.printf("[DRY-RUN] %s", line);
                    continue;
                }

                Files.writeString(
                        activityPath,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );

                runGit(List.of("git", "add", config.activityFilePath), Map.of());
                runGit(
                        List.of("git", "commit", "-m", "Commit " + i + " on " + date.format(DATE_FORMAT)),
                        Map.of(
                                "GIT_AUTHOR_DATE", dateTimeText,
                                "GIT_COMMITTER_DATE", dateTimeText
                        )
                );

                totalCommits++;
            }

            System.out.printf("Processed %s -> %d commits%n", date, commitsForDay);
        }

        System.out.printf("Done. Created %d commits.%n", totalCommits);

        if (!config.dryRun && config.pushAfterCommit) {
            runGit(List.of("git", "push", "-u", "origin", config.branchName), Map.of());
            System.out.printf("Pushed to origin/%s%n", config.branchName);
        }
    }

    private static void runGit(List<String> command, Map<String, String> extraEnv)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putAll(extraEnv);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Command failed (" + String.join(" ", command) + ")\n" + output
            );
        }

    }

    private static class Config {

        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int minCommitsPerDay;
        private final int maxCommitsPerDay;
        private final String activityFilePath;
        private final boolean pushAfterCommit;
        private final String branchName;
        private final boolean dryRun;

        private Config(
                LocalDate startDate,
                LocalDate endDate,
                int minCommitsPerDay,
                int maxCommitsPerDay,
                String activityFilePath,
                boolean pushAfterCommit,
                String branchName,
                boolean dryRun
        ) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.minCommitsPerDay = minCommitsPerDay;
            this.maxCommitsPerDay = maxCommitsPerDay;
            this.activityFilePath = activityFilePath;
            this.pushAfterCommit = pushAfterCommit;
            this.branchName = branchName;
            this.dryRun = dryRun;
        }


        private static Config fromArgs(String[] args) {
            LocalDate startDate = LocalDate.now().minusDays(DEFAULT_LOOKBACK_DAYS - 1L);
            LocalDate endDate = LocalDate.now();
            int min = 4;
            int max = 11;
            String file = "src/main/java/activity.txt";
            boolean push = false;
            String branch = "main";
            boolean dryRun = false;

            for (String arg : args) {
                if (arg.startsWith("--start=")) {
                    startDate = LocalDate.parse(arg.substring("--start=".length()), DATE_FORMAT);
                } else if (arg.startsWith("--end=")) {
                    endDate = LocalDate.parse(arg.substring("--end=".length()), DATE_FORMAT);
                } else if (arg.startsWith("--min=")) {
                    min = Integer.parseInt(arg.substring("--min=".length()));
                } else if (arg.startsWith("--max=")) {
                    max = Integer.parseInt(arg.substring("--max=".length()));
                } else if (arg.startsWith("--file=")) {
                    file = arg.substring("--file=".length());
                } else if (arg.equals("--push")) {
                    push = true;
                } else if (arg.startsWith("--branch=")) {
                    branch = arg.substring("--branch=".length());
                } else if (arg.equals("--dry-run")) {
                    dryRun = true;
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new Config(startDate, endDate, min, max, file, push, branch, dryRun);
        }
    }
}