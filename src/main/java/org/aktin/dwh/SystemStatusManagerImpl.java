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
        versions.put("wildfly", getWildflyVersion());
        versions.put("postgres", getLinuxPackageVersion("postgresql"));
        versions.put("apache2", getLinuxPackageVersion("apache2"));
        versions.put("dwh-j2ee", getDwhVersion());
        versions.put("aktin-notaufnahme-i2b2", getLinuxPackageVersion("aktin-notaufnahme-i2b2"));
        versions.put("aktin-notaufnahme-dwh", getLinuxPackageVersion("aktin-notaufnahme-dwh"));
        versions.put("aktin-notaufnahme-updateagent", getLinuxPackageVersion("aktin-notaufnahme-updateagent"));
        brokerResourceManager.putMyResourceProperties("versions", versions);
    }

    /**
     * Retrieves the installed version of a specified Linux package using apt.
     *
     * @param aptPackage the name of the apt package.
     * @return the installed version, "[not installed]" if not found, or "[error]" on error.
     */
    @Override
    public String getLinuxPackageVersion(String aptPackage) {
        try {
            String version = getAptPackageVersion(aptPackage);
            if (version == null)
               return "[not installed]";
            return version;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Error while retrieving version of linux package %s", aptPackage), e);
            return "[error]";
        }
    }

    /**
     * Retrieves installed versions for a list of Linux packages using apt.
     * <p>
     * Returns a map where each key is a package name and the value is the installed version.
     * If a package is not installed, its version is set to "[not installed]".
     * If an error occurs during retrieval, its version is set to "[error]".
     *
     * @param listPackages list of apt package names.
     * @return a map of package names to their installed version.
     */
    @Override
    public Map<String, String> getLinuxPackagesVersion(List<String> listPackages) {
        Map<String, String> packageVersions = new HashMap<>();
        try {
            packageVersions = getAptPackagesVersion(listPackages);
            for (String aptPackage : listPackages) {
                if (!packageVersions.containsKey(aptPackage))
                    packageVersions.put(aptPackage, "[not installed]");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Error while retrieving the versions of linux packages %s", listPackages), e);
            for (String aptPackage : listPackages) {
                packageVersions.put(aptPackage, "[error]");
            }
        }
        return packageVersions;
    }

    /**
     * Executes "apt list {aptPackage}" in a bash shell to retrieve the installed version of the specified Linux package.
     *
     * @param aptPackage the name of the Linux package.
     * @return the installed version, or null if not installed.
     * @throws IOException if the bash command returns an invalid output.
     */
    private String getAptPackageVersion(String aptPackage) throws IOException {
        String output = runBashCommand("apt list " + aptPackage);
        if (output == null)
            throw new IOException("No output from bash command");
        String version = extractAptVersionFromString(output, aptPackage);
        if (version.isEmpty())
            return null;
        return version;
    }

    /**
     * Executes "apt list {package1} {package2} ..." in a bash shell and collects the installed versions.
     *
     * @param packages a list of Linux package names.
     * @return a map where each key is a package name and the value is its installed version.
     * @throws IOException if the bash command returns an invalid output.
     */
    private Map<String, String> getAptPackagesVersion(List<String> packages) throws IOException {
        String output = runBashCommand("apt list " + String.join(" ", packages));
        if (output == null)
            throw new IOException("No output from bash command");
        Map<String, String> versions = new HashMap<>();
        packages.forEach(aptPackage -> {
            String version = extractAptVersionFromString(output, aptPackage);
            versions.put(aptPackage, version);
        });
        return versions;
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

    private String getOsVersion() {
        Path path = Paths.get("/etc/issue.net");
        if (!Files.exists(path)) {
            LOGGER.warning("OS version file not found at /etc/issue.net");
            return "[error]";
        }
        try {
            return Files.readAllLines(path).stream()
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read OS version from file", e);
            return "[error]";
        }
    }

    private String getKernelVersion() {
        String command = String.join(" ", "uname", "-r");
        return runBashCommand(command);
    }

    private String getJavaVersion() {
        return String.join("/", System.getProperty("java.vendor"), System.getProperty("java.version"));
    }

    private String getWildflyVersion() {
        String version = System.getProperty("jboss.product.version");
        if (version == null) {
            LOGGER.log(Level.WARNING, "WildFly version property not found.");
            version = "[undefined]";
        }
        return version;
    }

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
