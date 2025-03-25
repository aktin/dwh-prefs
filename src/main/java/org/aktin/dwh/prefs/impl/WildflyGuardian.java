package org.aktin.dwh.prefs.impl;

import org.jboss.as.cli.*;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

/*
 * This class acts as the interface to the JBoss CLI of wildfly. It creates backup files, performs rollbacks and restarts wildfly
 * */
public class WildflyGuardian {

    private Path activePath;    // path to the current properties file
    private Path backupPath;    // path to the backup file, may point to nowhere when initialising this class

    public WildflyGuardian(String backupPath, String activePath) {
        this.backupPath = Paths.get(backupPath);
        this.activePath = Paths.get(activePath);
    }

    /**
     * This method restarts wildfly by connecting to the local JBoss CLI and executing a shutdown command.
     * A management user profile in wildfly configs and its credentials are required for this method
     * @return errormessage - A String that is given to the endpoint, containing the shutdown status
     */
    public String restartWildflyService() {
        String[] cred = this.getCredentials();
        if (cred.length == 1) {
            return "error: "+cred[0];
        }
        String user = cred[0];
        String pw = cred[1];
        String controller = "127.0.0.1";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream s = new PrintStream(outputStream);

            // Create CLI context
            CommandContextConfiguration conf = new CommandContextConfiguration.Builder()
                    .setController(controller).setUsername(user).setPassword(pw.toCharArray()).build();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(conf);
            ctx.captureOutput(s);
            ctx.connectController();

            // Execute a CLI command
            String command = ":shutdown(restart=true)";
            ctx.handle(command);

            s.flush();  // flush CLI response
            ctx.terminateSession();
            return "success: message: " + outputStream.toString();
        } catch (CommandLineException e) {
            return "error:commandlineexception: could not connect to jboss CLI:"+ e.getMessage();
        }
    }

    /**
     * Gets the credentials for the management user role
     * @return credentials
     */
    private String[] getCredentials() {
        File f = new File("/opt/wildfly/standalone/configuration/management_user_credentials.txt");
        try {
            Scanner s = new Scanner(f);
            String line = s.nextLine();
            return line.split(":");
        } catch (IOException e) {
            return new String[]{e.getMessage()};
        }
    }

    public void createBackup() throws IOException {
        try {
            Files.copy(this.getActivePath(), this.getBackupPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("error:Could not create backup of properties file");
        }
    }

    public void rollbackPropertiesFile() throws IOException {
        if (!Files.exists(this.getBackupPath())) {
            throw new FileNotFoundException("error:Backup file not found for rollback");
        }
        Files.move(this.getBackupPath(), this.getActivePath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public Path getActivePath() {
        return activePath;
    }
}
