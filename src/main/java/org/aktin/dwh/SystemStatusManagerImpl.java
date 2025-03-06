package org.aktin.dwh;

import javax.annotation.PostConstruct;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of dwh-api:SystemStatusManager. After creation, collects versions of diverse AKTIN DWH components and
 * uploads them to the AKTIN Broker as a resource. Implements getLinuxPackageVersion() and getLinuxPackagesVersion() to
 * allow injecting classes to retrieve versions of necessary linux packages (like python or rscript)
 */

@Startup
@Singleton
public class SystemStatusManagerImpl implements SystemStatusManager {

    private static final Logger LOGGER = Logger.getLogger(SystemStatusManager.class.getName());

    @Inject
    private BrokerResourceManager brokerResourceManager;

    @PostConstruct
    public void uploadComponentVersions() {
        Properties versions = new Properties();
        versions.put("os", getOsVersion());
        versions.put("kernel", getKernelVersion());
        versions.put("java", getJavaVersion());
        versions.put("j2ee-impl", getApplicationServerVersion());
        versions.put("postgres", getPostgresVersion());
        versions.put("apache2", getApacheVersion());
        versions.put("dwh-j2ee", getDwhVersion());
        brokerResourceManager.putMyResourceProperties("versions", versions);
    }

    /**
     * Returns the corresponding installed version of a given linux pacakge
     * (Only apt package manager is supported)
     *
     * @param aptPackage name of apt package
     * @return corresponding version or [not installed] if not installed or [error] on thrown exception
     */
    @Override
    public String getLinuxPackageVersion(String aptPackage) {
        String version;
        try {
            version = getAptPackageVersion(aptPackage);
            if (version == null)
                version = "[not installed]";
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Error while retrieving version of linux package %s", aptPackage), e);
            version = "[error]";
        }
        return version;
    }

    /**
     * Runs "apt list {package}" in a bash shell to retrieve the installed version of a given linux package
     *
     * @param aptPackage name of linux package
     * @return corresponding version or null if not installed
     * @throws IOException if bash command returns an invalid output
     */
    private String getAptPackageVersion(String aptPackage) throws IOException {
        String version;
        String command = String.join(" ", "apt", "list", aptPackage);
        String output_command = runBashCommand(command);
        if (output_command == null)
            throw new IOException();
        version = extractAptVersionFromString(output_command, aptPackage);
        if (version.isEmpty())
            version = null;
        return version;
    }

    /**
     * Retrieve the corresponding installed versions of a given list of linux packages and collect them in a
     * map
     *
     * @param list_packages list of linux package names
     * @return Map with {package name, installed version}. Value of map is set to "[not installed]" if package
     * is not installed or "[error]" if an exception was thrown during version collection
     */
    @Override
    public Map<String, String> getLinuxPackagesVersion(List<String> list_packages) {
        Map<String, String> map_versions = new HashMap<>();
        try {
            map_versions = getAptPackagesVersion(list_packages);
            for (String aptPackage : list_packages) {
                if (!map_versions.containsKey(aptPackage))
                    map_versions.put(aptPackage, "[not installed]");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Error while retrieving the versions of linux packages %s", list_packages), e);
            for (String aptPackage : list_packages) {
                map_versions.put(aptPackage, "[error]");
            }
        }
        return map_versions;
    }

    /**
     * Runs "apt list {package1} {package2} {etc.}" in a bash shell on a given list of linux package names
     * and collects the package name with corresponding version in a map
     *
     * @param list_packages list of linux package names
     * @return Map with {package name, installed version}
     * @throws IOException if bash command returns an invalid output
     */
    private Map<String, String> getAptPackagesVersion(List<String> list_packages) throws IOException {
        Map<String, String> map_versions = new HashMap<>();
        String packages = String.join(" ", list_packages);
        String command = String.join(" ", "apt", "list", packages);
        String output_command = runBashCommand(command);
        if (output_command == null)
            throw new IOException();
        list_packages.forEach(aptPackage -> {
            String version = extractAptVersionFromString(output_command, aptPackage);
            map_versions.put(aptPackage, version);
        });
        return map_versions;
    }

    /**
     * Run a given bash command in a java Process. Grab the output via InputStream and return it as converted string
     *
     * @param command bash command to run
     * @return console output of bash command as string
     */
    private String runBashCommand(String command) {
        String result = "";
        ProcessBuilder processBuilder = new ProcessBuilder("/usr/bin/bash", "-c", command);
        Process process = null;
        try {
            process = processBuilder.start();
            try (InputStream output = process.getInputStream(); InputStream error = process.getErrorStream()) {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    output.close();
                    error.close();
                    process.destroy();
                    process.waitFor();
                    LOGGER.log(Level.WARNING, String.format("Timeout while running command %s", command));
                }
                if (process.exitValue() == 0) {
                    result = convertStreamToString(output);
                } else
                    throw new IOException(convertStreamToString(error));
            }
        } catch (IOException | InterruptedException e) {
            if (process != null && process.isAlive())
                process.destroy();
            LOGGER.log(Level.WARNING, String.format("Unable to run command %s", command), e);
        }
        return result;
    }

    private String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            builder.append(line).append("\n");
        return builder.toString();
    }

    /**
     * Extract the version of a given linux package from a string using regex.
     *
     * @param string console output of an "apt list" command
     * @param aptPackage package to retrieve version from
     * @return version of corresponding {aptPackage}
     */
    private String extractAptVersionFromString(String string, String aptPackage) {
        Pattern pattern = Pattern.compile(String.format("%s.*\\s(\\d\\S*)\\s.*\\[", aptPackage));
        Matcher matcher = pattern.matcher(string);
        String result = "";
        while (matcher.find())
            result = matcher.group(1);
        return result;
    }

    /**
     * get the version of running operating system by reading /etc/issue.net
     */
    private String getOsVersion() {
        try {
            Path path = Paths.get("/etc/issue.net");
            if (Files.exists(path)) {
                return Files.readAllLines(path).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("\n"));
            } else
                throw new FileNotFoundException();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read os version from file", e);
        }
        return "[error]";
    }

    /**
     * get the kernel version by running "uname -r"
     */
    private String getKernelVersion() {
        String command = String.join(" ", "uname", "-r");
        return runBashCommand(command);
    }

    /**
     * get the java version from java system properties
     */
    private String getJavaVersion() {
        return String.join("/", System.getProperty("java.vendor"), System.getProperty("java.version"));
    }

    private String getApplicationServerVersion() {
        return Objects.toString(javax.ejb.TimerService.class.getPackage().getImplementationVersion());
    }

    /**
     * get the installed version of linux package postgresql-12
     */
    private String getPostgresVersion() {
        return getLinuxPackageVersion("postgresql-12");
    }

    /**
     * get the installed version of linux package apache2
     */
    private String getApacheVersion() {
        return getLinuxPackageVersion("apache2");
    }

    /**
     * get the running version of dwh-j2ee
     */
    private String getDwhVersion() {
        String version = "";
        try {
            version = (String) (new InitialContext().lookup("java:app/AppName"));
        } catch (NamingException e) {
            LOGGER.log(Level.WARNING, "Unable to get ear version via java:app/AppName");
        }
        if (version.isEmpty())
            version = "[undefined]";
        return version;
    }
}
