package org.aktin.dwh.prefs.impl;

import org.jboss.as.cli.*;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * This class acts as the interface to the JBoss CLI of wildfly. It creates backup files, performs rollbacks and restarts wildfly
 * */
public class WildflyGuardian {

    private Path aktinPropertiesPath;    // path to the current properties file
    String backupFileDesc = "aktindwh_aktinproperties_v";
    String backupFileType = ".txt";
    Pattern backupNamePattern = Pattern.compile(this.getBackupFileDesc() + "(\\d+).*" + this.getBackupFileType());


    public WildflyGuardian(String aktinPropertiesPath) {
        this.aktinPropertiesPath = Paths.get(aktinPropertiesPath);
    }

    /**
     * This method restarts wildfly by connecting to the local JBoss CLI and executing a shutdown command.
     * A management user profile in wildfly configs and its credentials are required for this method
     * @return errormessage - A String that is given to the endpoint, containing the shutdown status
     */
    public String restartWildflyService() throws FileNotFoundException {
        String[] cred = this.getCredentials();
        String controller = "127.0.0.1";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream s = new PrintStream(outputStream);

            // Create CLI context
            CommandContextConfiguration conf = new CommandContextConfiguration.Builder()
                    .setController(controller).setUsername(cred[0]).setPassword(cred[1].toCharArray()).build();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(conf);
            ctx.captureOutput(s);
            ctx.connectController();

            // Execute a CLI command
            String command = ":shutdown(restart=true)";
            ctx.handle(command);

            s.flush();  // flush CLI response
            ctx.terminateSession();
            return "success: " + outputStream.toString();
        } catch (CommandLineException e) {
            return "commandlineexception: could not connect to jboss CLI:"+ e.getMessage();
        }
    }


    /**
     * Gets the credentials for the management user role
     * @return credentials
     */
    private String[] getCredentials() throws FileNotFoundException {
        File f = new File("/opt/wildfly/standalone/configuration/management_user_credentials.txt");
        Scanner s = new Scanner(f);
        String[] cred = s.nextLine().split(":");
        if (cred.length == 2) {
            return cred;
        } else {
            throw new IllegalArgumentException("unexpected credential format");
        }
    }

    public void createBackup() throws IOException {
        Path backup =  this.generateBackupPath();
        try {
            Files.copy(this.getAktinPropertiesPath(), backup);
        } catch (IOException e) {
            throw new IOException("Could not create backup of properties file at: "+backup);
        }
    }

    /**
     * Generates a Backup path by searching the latest backup version number and adding 1 to it, or if no backup
     * was found, set the version number to 1.
     * @return backupPath
     */
    private Path generateBackupPath() {
        StringBuilder backupName = new StringBuilder();
        backupName.append(backupFileDesc)
                .append(this.getLatestBackupVersion() + 1)
                .append("_")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd+HH:mm:ss")))
                .append(backupFileType);
        return Paths.get(String.valueOf(this.getAktinPropertiesPath().getParent()), backupName.toString());
    }

    /**
     * Searches all files in the Backup directory with prefixes and returns them.
     * Throws exception when returned object is empty or no file was found.
     * @return backupFiles
     */
    public File[] getBackupFiles() {
        File propertiesDir = new File(String.valueOf(this.getAktinPropertiesPath().getParent()));
        Pattern pattern = Pattern.compile(this.getBackupFileDesc() + "(\\d+).*" + this.getBackupFileType());
        File[] backupFiles = propertiesDir.listFiles((d, name) -> pattern.matcher(name).matches());	// Filter only backup files
        assert backupFiles != null;
        assert backupFiles.length > 0;
        return backupFiles;
    }

    /**
     * Searches all backup filenames for their version number and returns the highest number.
     * If no backup file was found, this method returns 0.
     * @return versionNum - highest version number
     */
    private int getLatestBackupVersion() {
        File[] files = this.getBackupFiles();
        int versionNum = 0;
        for (File f : files) {
            int fileVersion = this.extractBackupVersionFromName(f.getName());
            if (fileVersion > versionNum) {
                versionNum = fileVersion;
            }
        }
        return versionNum;
    }

    private int extractBackupVersionFromName(String filename) {
        Matcher matcher = this.getBackupNamePattern().matcher(filename);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new NullPointerException("could not extract version number from \"" + filename + "\"");
        }
    }

    /**
     * Replaces the current properties file with the last found backup and restarts wildfly. Throws assertion error when no backup was found.
     * @throws IOException
     */
    public void rollbackToLastVersion() throws IOException {
        HashMap<Integer, File> backupMap = new HashMap<>(); // contains backup files as values and their version as keys
        for (File f : this.getBackupFiles()) {
            backupMap.put(this.extractBackupVersionFromName(f.getName()), f);
        }

        Path latestBackup = backupMap.get(this.getLatestBackupVersion()).toPath();
        this.createBackup();    // Persists current properties
        Files.copy(latestBackup, this.getAktinPropertiesPath(), StandardCopyOption.REPLACE_EXISTING);
        this.restartWildflyService();
    }

    /**
     * Replaces the current properties file with specific backup file and restarts wildfly.
     * Throws FileNotFoundException if no file was found
     * @param path
     * @throws IOException
     */
    public void rollbackToSpecificVersion(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.copy(path, this.getAktinPropertiesPath(), StandardCopyOption.REPLACE_EXISTING);
            this.restartWildflyService();
        } else {
            throw new FileNotFoundException("backup file not found at: " + path);
        }
    }

    public Path getAktinPropertiesPath() {
        return aktinPropertiesPath;
    }

    public String getBackupFileDesc() {
        return this.backupFileDesc;
    }

    public String getBackupFileType() {
        return this.backupFileType;
    }

    public Pattern getBackupNamePattern() {
        return backupNamePattern;
    }
}
