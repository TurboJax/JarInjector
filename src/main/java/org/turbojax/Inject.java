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
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "inject")
public class Inject extends AbstractMojo {
    @Parameter(property = "run.pluginJar", required = true)
    private String pluginJar;

    @Parameter(property = "run.patchedPluginName")
    private String patchedPluginName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Getting the project from the plugin context
        MavenProject project = (MavenProject) getPluginContext().get("project");

        // Getting the plugin file from the localPlugin parameter
        File pluginFile = new File(pluginJar);

        // Handling if the plugin file doesn't exist
        if (!pluginFile.exists()) {
            getLog().error("Cannot find the plugin at \"" + pluginJar + "\".  Make sure that this is a path is relative to the base directory or absolute.");
            return;
        }

        getLog().info("Plugin found!");

        // Getting the built patch jar
        String patchFileName = "target/" + project.getArtifactId() + "-" + project.getVersion() + ".jar";
        File patchFile = new File(patchFileName);

        if (!patchFile.exists()) {
            getLog().error("Cannot find the patch file at \"" + patchFileName + "\".  Make sure that this is a path is relative to the base directory or absolute.");
            return;
        }

        getLog().info("Patch file found!");

        // Getting the target directory
        File targetDir = new File("target/" + patchFile.getName().replace(".jar", ""));

        // Extracting the plugin to the target directory
        unzip(pluginFile, targetDir);

        // Extracting the patch file to the target directory
        unzip(patchFile, targetDir);

        // Zipping the contents of the target directory into a patched plugin
        try {
            // Getting what to name the patched plugin.jar file
            if (patchedPluginName == null) {
                patchedPluginName = pluginFile.getName().replace(".jar", "") + "-patched.jar";
            }

            // Create ZipOutputStream to write to the zip file
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("target/" + patchedPluginName));

            // Looping over every path to zip
            Files.walk(targetDir.toPath()).forEach(filePath -> {
                // Skipping directories
                if (filePath.toFile().isDirectory()) return;

                try {
                    // Creating the ZipEntry for the file
                    ZipEntry ze = new ZipEntry(filePath.subpath(2, filePath.getNameCount()).toString());
                    zos.putNextEntry(ze);

                    // Reading the file and writing it to ZipOutputStream
                    FileInputStream fis = new FileInputStream(filePath.toFile());
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }

                    zos.closeEntry();
                    fis.close();
                } catch (IOException err) {
                    getLog().error("Could not create a ZipEntry for" + filePath.toString(), err);
                    System.exit(1);
                }
            });
            zos.close();
        } catch (IOException err) {
            getLog().error("Could not zip up the contents of " + targetDir.toString(), err);
            return;
        }

        getLog().info("Successfully patched the plugin.");

        // Removing unzipped folder
        try {
            Files.walk(targetDir.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException err) {
            getLog().warn("Error deleting unzipped plugin directory: ", err);
        }

        getLog().info("Removed the temporary files.");
    }

    /**
     * Unzips a zip file into a folder
     * @param zipFilePath
     * @param destDir
     */
    private static void unzip(File zipFilePath, File dir) {
        // create output directory if it doesn't exist
        if (!dir.exists()) dir.mkdirs();

        // buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            FileInputStream fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(dir, fileName);
                if (!ze.isDirectory()) {
                    // create directories for sub directories in zip
                    new File(newFile.getParent()).mkdirs();
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                // close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            // close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException err) {
            err.printStackTrace();
        }
    }
}