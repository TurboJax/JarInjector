## Configs
There are three configs for this maven plugin.  
- `sourceJar`: **REQUIRED** The path to the jar file you'd like to patch.
- `patchedJar`: **Optional** The name of the output jar file.  By default, it adds "-Patched" to the end of the sourceJar config.

## Usage
Add `maven.turbojax.org` as a repository as shown below.
```
<repositories>
    <repository>
        <id>turbojax-releases</id>
        <url>https://maven.turbojax.org/releases</url>
    </repository>
    ...
</repositories>
```

In your pom.xml, add `jarinjector` as a plugin as shown below.
```
<plugins>
    <plugin>
        <groupId>org.turbojax</groupId>
        <artifactId>jarinjector</artifactId>
        <version>1.1</version>
        <configuration>
            <sourceJar>CHANGEME</sourceJar>
        </configuration>
    </plugin>
</plugins>
```

If you'd like to run the injector automatically when you run `mvn install`, add the following to your pom.xml.
```
<executions>
    <execution>
        <phase>install</phase>
        <goals>
            <goal>inject</goal>
        </goals>
    </execution>
</executions>
```

## Gradle
If you want to use Gradle instead of Maven, here is a gradle task to put in build.gradle that does what this plugin does.
```
ext {
    sourceJar = "changeme"
    patchedJar = "changeme"
}

task inject(type: Zip) {
    delete "unpacked"
    duplicatesStrategy = "include"

    from zipTree("build/libs/${rootProject.name}-${version}.jar")
    into "unpacked"
    
    from zipTree("${sourceJar}")
    into "unpacked"

    from "unpacked/"
    include "*"
    include "*/*"
    archiveFileName = "$patchedJar"
    destinationDirectory = layout.buildDirectory

    delete "unpacked/"
}
```
