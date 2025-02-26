package org.aktin.dwh.prefs.impl;

/*
* This class interacts with the wildfly service, stops/starts it and checks status.
* It also rolls the update back, when new preferences lead to errors.
* */

import org.jboss.as.cli.*;
import org.jboss.as.cli.impl.CommandContextConfiguration;

import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.UnexpectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class WildflyGuardian {

    private Path backupPath;
    private Path activePath;
    private int timeout = 15000; //Timeout for starting Wildfly until resources are reachable

    public WildflyGuardian(String backupPath, String activePath) {
        this.backupPath = Paths.get(backupPath);
        this.activePath = Paths.get(activePath);
    }


    private String wildflyStatusCode() throws IOException, InterruptedException {
        Process wildfly_status = Runtime.getRuntime().exec("curl -I http://localhost/aktin/admin");
        InputStream inputStream = wildfly_status.getInputStream();
        Scanner scanner = new Scanner(inputStream);
        String statusCode = "";

        // Read the first line containing the status code
        if (scanner.hasNextLine()) {
            String firstLine = scanner.nextLine(); // Example: "HTTP/1.1 200 OK"
            String[] parts = firstLine.split(" ");
            if (parts.length > 1) {
                statusCode = parts[1]; // Extract the status code
            }
        } else {
            throw new ConnectException("Wildfly response empty");
        }
        scanner.close();
        wildfly_status.waitFor();
        return statusCode;
    }

//    private boolean waitForWildflyReady() throws IOException, InterruptedException {
//        int startTime = (int) System.currentTimeMillis();
//        while (System.currentTimeMillis()-startTime < this.timeout) {
//            String status = wildflyStatusCode();
//            if(status.startsWith("2") || status.startsWith("3")) {
//                return true;
//            } else {
//                Thread.sleep(1000);
//            }
//        }
//        return false;
//    }

    public String restartWildflyService() {
        String user = "";
        String pw = "";//TODO delete
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

            // Execute a CLI command (e.g., check server state)
            String command = ":shutdown(restart=true)";
            ctx.handle(command);
            s.flush();
            // Close the context
            ctx.terminateSession();
            return "success: message: " + outputStream.toString();
        } catch (CommandLineException e) {
            return "commandlineexception: probems connecting to jboss CLI:"+ e.getMessage();
        }
    }

    public void rollbackPropertiesFile() throws IOException {
        if (!Files.exists(this.getBackupPath())) {
            throw new FileNotFoundException("Backup file not found for rollback");
        }
        Files.move(this.getBackupPath(), this.getActivePath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public void createBackup() throws IOException {
        try {
            Files.copy(this.getActivePath(), this.getBackupPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("Could not create backup of properties file");
        }
    }

    public void start() throws IOException {
        String filePath = Objects.requireNonNull(WildflyGuardian.class.getResource("WildflyGuardian.class")).toString();
        ProcessBuilder pb = new ProcessBuilder("java", "cp", filePath);
        pb.environment().put("BACKUP_PATH", this.getBackupPath().toString());
        pb.environment().put("ACTIVE_PATH", this.getActivePath().toString());
        pb.start();
    }



    public static void main(String[] args) throws IOException, InterruptedException {
        String backupPath = System.getenv("BACKUP_PATH");
        String activePath = System.getenv("ACTIVE_PATH");
        if (backupPath.isEmpty() || activePath.isEmpty()) {
            throw new NullPointerException("Environment variables are missing");
        }
        WildflyGuardian guardian = new WildflyGuardian(backupPath, activePath);
//        guardian.createBackup();
        guardian.restartWildflyService();


//        try {
//            guardian.restartWildflyService();
//        } catch (IOException | InterruptedException e) {
//            guardian.rollbackPropertiesFile();
//        }

    }

    public Path getBackupPath() {
        return backupPath;
    }

    public Path getActivePath() {
        return activePath;
    }

    public int getTimeout() {
        return timeout;
    }
}
