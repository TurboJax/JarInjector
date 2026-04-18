package org.turbojax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "inject")
public class Inject extends AbstractMojo {
    @Parameter(property = "run.sourceJar", required = true)
    private String sourceJarName;

    @Parameter(property = "run.patchedJar")
    private String patchedJarName;

    private final Log log = getLog();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Getting the project from the plugin context
        MavenProject project = (MavenProject) getPluginContext().get("project");

        // Getting the file from the toPatch parameter
        File sourceJar = new File(sourceJarName);

        // Handling if the file doesn't exist
        if (!sourceJar.exists()) {
            log.error("Cannot find the file at \"" + sourceJarName + "\".  Make sure that this is a path is relative to the base directory or absolute.");
            return;
        }

        log.info("Source jar found!");

        // Getting the built patch jar
        String patchFileName = "target/" + project.getArtifactId() + "-" + project.getVersion() + ".jar";
        File patchFile = new File(patchFileName);

        if (!patchFile.exists()) {
            log.error("Cannot find the patch file at \"" + patchFileName + "\".  Make sure that this is a path is relative to the base directory or absolute.");
            return;
        }

        log.info("Patch file found!");

        // Getting the target directory
        File targetDir = new File("target/" + patchFile.getName().replace(".jar", ""));

        // Extracting the source file to the target directory
        unzip(sourceJar, targetDir);

        // Extracting the patch file to the target directory
        unzip(patchFile, targetDir);

        // Zipping the contents of the target directory into a patched file
        try {
            // Getting what to name the patched file
            if (patchedJarName == null) {
                patchedJarName = sourceJar.getName().replace(".jar", "") + "-patched.jar";
            }

            // Create ZipOutputStream to write to the zip file
            ZipOutputStream patchedJar = new ZipOutputStream(new FileOutputStream("target/" + patchedJarName));

            // Looping over every path to zip
            Files.walk(targetDir.toPath()).forEach(filePath -> {
                // Skipping directories
                if (filePath.toFile().isDirectory()) return;

                try {
                    // Creating the ZipEntry for the file
                    ZipEntry entry = new ZipEntry(filePath.subpath(2, filePath.getNameCount()).toString());
                    patchedJar.putNextEntry(entry);

                    // Reading the file and writing it to ZipOutputStream
                    FileInputStream fileStream = new FileInputStream(filePath.toFile());
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fileStream.read(buffer)) > 0) {
                        patchedJar.write(buffer, 0, len);
                    }

                    patchedJar.closeEntry();
                    fileStream.close();
                } catch (IOException err) {
                    log.error("Could not create a ZipEntry for" + filePath.toString(), err);
                    System.exit(1);
                }
            });
            patchedJar.close();
        } catch (IOException err) {
            log.error("Could not zip up the contents of " + targetDir.toString(), err);
            return;
        }

        log.info("Successfully patched " + sourceJarName);

        // Removing unzipped folder
        try {
            Files.walk(targetDir.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException err) {
            log.warn("Error deleting unzipped directory: ", err);
        }

        log.info("Removed the temporary files.");
    }

    /**
     * Unzips a zip file into a folder
     * @param zipFile
     * @param dest
     */
    private void unzip(File zipFile, File dest) {
        // Creating output directory if it doesn't exist
        if (!dest.exists()) dest.mkdirs();

        // Buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            ZipInputStream zipStream = new ZipInputStream(new FileInputStream(zipFile));

            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                File outFile = new File(dest, entry.getName());

                // Skipping dirs
                if (entry.isDirectory()) continue;

                // Creating the parent dirs
                outFile.getParentFile().mkdirs();

                // Extracting the file
                FileOutputStream fos = new FileOutputStream(outFile);
                int len;
                while ((len = zipStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();

                // Moving to the next ZipEntry
                zipStream.closeEntry();
                entry = zipStream.getNextEntry();
            }
            
            // Closing zipstream
            zipStream.close();
            log.info("Unzipped " + zipFile.getName() + " to " + dest.getName());
        } catch (IOException err) {
            err.printStackTrace();
            log.error("Error while extracting " + zipFile.getName(), err);
        }
    }
}