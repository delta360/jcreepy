
package org.fusesource.hawtjni.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;


public class Library {
    static final String SLASH = System.getProperty("file.separator");
    private final String name;
    private final String version;
    private final ClassLoader classLoader;
    private boolean loaded;

    public Library(String name) {
        this(name, null, null);
    }

    public Library(String name, Class<?> clazz) {
        this(name, Library.version(clazz), clazz.getClassLoader());
    }

    public Library(String name, String version) {
        this(name, version, null);
    }

    public Library(String name, String version, ClassLoader classLoader) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        this.name = name;
        this.version = version;
        this.classLoader = classLoader;
    }

    private static String version(Class<?> clazz) {
        try {
            return clazz.getPackage().getImplementationVersion();
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static String getOperatingSystem() {
        String name = System.getProperty("os.name").toLowerCase().trim();
        if (name.startsWith("linux")) {
            return "linux";
        }
        if (name.startsWith("mac os x")) {
            return "osx";
        }
        if (name.startsWith("win")) {
            return "windows";
        }
        return name.replaceAll("\\W+", "_");
    }

    public static String getPlatform() {
        return Library.getOperatingSystem() + Library.getBitModel();
    }

    public static int getBitModel() {
        String prop = System.getProperty("sun.arch.data.model");
        if (prop == null) {
            prop = System.getProperty("com.ibm.vm.bitmode");
        }
        if (prop != null) {
            return Integer.parseInt(prop);
        }
        return -1;
    }

    public synchronized void load() {
        if (this.loaded) {
            return;
        }
        this.doLoad();
        this.loaded = true;
    }

    private void doLoad() {
        String version = System.getProperty("library." + this.name + ".version");
        if (version == null) {
            version = this.version;
        }
        ArrayList<String> errors = new ArrayList<String>();
        String customPath = System.getProperty("library." + this.name + ".path");
        if (customPath != null) {
            if (version != null && this.load(errors, this.file(customPath, this.map(this.name + "-" + version)))) {
                return;
            }
            if (this.load(errors, this.file(customPath, this.map(this.name)))) {
                return;
            }
        }
        if (version != null && this.load(errors, this.name + Library.getBitModel() + "-" + version)) {
            return;
        }
        if (version != null && this.load(errors, this.name + "-" + version)) {
            return;
        }
        if (this.load(errors, this.name)) {
            return;
        }
        if (this.classLoader != null) {
            if (this.exractAndLoad(errors, version, customPath, this.getPlatformSpecifcResourcePath())) {
                return;
            }
            if (this.exractAndLoad(errors, version, customPath, this.getOperatingSystemSpecifcResourcePath())) {
                return;
            }
            if (this.exractAndLoad(errors, version, customPath, this.getResorucePath())) {
                return;
            }
        }
        throw new UnsatisfiedLinkError("Could not load library. Reasons: " + errors.toString());
    }

    public final String getOperatingSystemSpecifcResourcePath() {
        return this.getPlatformSpecifcResourcePath(Library.getOperatingSystem());
    }

    public final String getPlatformSpecifcResourcePath() {
        return this.getPlatformSpecifcResourcePath(Library.getPlatform());
    }

    public final String getPlatformSpecifcResourcePath(String platform) {
        return "META-INF/native/" + platform + "/" + this.map(this.name);
    }

    public final String getResorucePath() {
        return "META-INF/native/" + this.map(this.name);
    }

    public final String getLibraryFileName() {
        return this.map(this.name);
    }

    private boolean exractAndLoad(ArrayList<String> errors, String version, String customPath, String resourcePath) {
        URL resource = this.classLoader.getResource(resourcePath);
        if (resource != null) {
            File target;
            String libName = this.name + "-" + Library.getBitModel();
            if (version != null) {
                libName = libName + "-" + version;
            }
            if (customPath != null && this.extract(errors, resource, target = this.file(customPath, this.map(libName))) && this.load(errors, target)) {
                return true;
            }
            customPath = System.getProperty("java.io.tmpdir");
            target = this.file(customPath, this.map(libName));
            if (this.extract(errors, resource, target) && this.load(errors, target)) {
                return true;
            }
        }
        return false;
    }

    private /* varargs */ File file(String ... paths) {
        File rc = null;
        for (String path : paths) {
            rc = rc == null ? new File(path) : new File(rc, path);
        }
        return rc;
    }

    private String map(String libName) {
        String ext;
        if ((libName = System.mapLibraryName(libName)).endsWith(ext = ".dylib")) {
            libName = libName.substring(0, libName.length() - ext.length()) + ".jnilib";
        }
        return libName;
    }

    private boolean extract(ArrayList<String> errors, URL source, File target) {
        FileOutputStream os = null;
        InputStream is = null;
        boolean extracting = false;
        try {
            if ((!target.exists() || this.isStale(source, target)) && (is = source.openStream()) != null) {
                int read;
                byte[] buffer = new byte[4096];
                os = new FileOutputStream(target);
                extracting = true;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.close();
                is.close();
                this.chmod("755", target);
            }
        }
        catch (Throwable e) {
            try {
                if (os != null) {
                    os.close();
                }
            }
            catch (IOException e1) {
                // empty catch block
            }
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException e1) {
                // empty catch block
            }
            if (extracting && target.exists()) {
                target.delete();
            }
            errors.add(e.getMessage());
            return false;
        }
        return true;
    }

    private boolean isStale(URL source, File target) {
        if (source.getProtocol().equals("jar")) {
            try {
                String[] parts = source.getFile().split(Pattern.quote("!"));
                source = new URL(parts[0]);
            }
            catch (MalformedURLException e) {
                return false;
            }
        }
        File sourceFile = null;
        if (source.getProtocol().equals("file")) {
            sourceFile = new File(source.getFile());
        }
        if (sourceFile != null && sourceFile.exists() && sourceFile.lastModified() > target.lastModified()) {
            return true;
        }
        return false;
    }

    private void chmod(String permision, File path) {
        if (Library.getPlatform().startsWith("windows")) {
            return;
        }
        try {
            Runtime.getRuntime().exec(new String[]{"chmod", permision, path.getCanonicalPath()}).waitFor();
        }
        catch (Throwable e) {
            // empty catch block
        }
    }

    private boolean load(ArrayList<String> errors, File lib) {
        try {
            System.load(lib.getPath());
            return true;
        }
        catch (UnsatisfiedLinkError e) {
            errors.add(e.getMessage());
            return false;
        }
    }

    private boolean load(ArrayList<String> errors, String lib) {
        try {
            System.loadLibrary(lib);
            return true;
        }
        catch (UnsatisfiedLinkError e) {
            errors.add(e.getMessage());
            return false;
        }
    }
}

