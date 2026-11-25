package dev.sp.devices.frameworkhelp;

import bitwig.obf.mjr; // a translation table (one per resource bundle); beI(key, args) returns its text or null
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bitwig looks a help text up as mjr.beI(key, args) on its translation tables: null when the table has no such key, and
 * then the caller logs "Localized text for key ... not found" and shows the key itself. This mod wraps that return: for a
 * key no table has, it answers from the *.help.properties files in Framework: Nitro Devices' folders.
 */
public final class HelpHooks {
    private HelpHooks() {}

    static final Bml.Mod MOD = Bml.mod("framework-device-help");
    private static final Pattern FOLDERS = Pattern.compile("\"folders\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final String SUFFIX = ".help.properties";

    private static final Object LOCK = new Object();
    private static Map<String, String> texts = Map.of();
    private static String stamp = null;   // the help files' paths, sizes and times at the last load
    private static long checked = 0;      // System.nanoTime() of the last stamp check

    /** mjr.beI(String, Object[]): Bitwig's text for the key, or null. */
    @Hook(id = "framework-device-help-text", target = mjr.class, method = "beI(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
          at = At.WRAP_RETURN, allReturns = true)
    public static String text(@Return String found, @Arg(0) String key) {
        if (found != null || key == null) return found;
        try {
            return current().get(key);
        } catch (Throwable t) {
            MOD.log("help lookup of " + key + " failed", t);
            return null;
        }
    }

    private static Map<String, String> current() {
        synchronized (LOCK) {
            long now = System.nanoTime();
            if (stamp == null || now - checked > 1_000_000_000L) {
                checked = now;
                List<Path> files = helpFiles();
                StringBuilder sb = new StringBuilder();
                for (Path f : files) {
                    try {
                        sb.append(f).append('|').append(Files.size(f)).append('|').append(Files.getLastModifiedTime(f).toMillis()).append('\n');
                    } catch (IOException e) {
                        sb.append(f).append("|?\n");
                    }
                }
                String s = sb.toString();
                if (!s.equals(stamp)) {
                    stamp = s;
                    texts = load(files);
                }
            }
            return texts;
        }
    }

    private static Map<String, String> load(List<Path> files) {
        Map<String, String> map = new HashMap<>();
        for (Path f : files) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                MOD.log("skipping " + f + ": " + e);
                continue;
            }
            for (String k : p.stringPropertyNames()) map.putIfAbsent(k, p.getProperty(k));
        }
        MOD.log("loaded " + files.size() + " help files: " + map.size() + " keys");
        return Map.copyOf(map);
    }

    /** The framework's config file: next to this mod's own data folder, else where Patchbay keeps it on macOS. */
    private static Path configFile() {
        try {
            Path own = MOD.resource("data"); // <data>/mods/framework-device-help/data
            Path sibling = own.getParent().getParent().resolve("custom-device-registrar").resolve("config.json");
            if (Files.isRegularFile(sibling)) return sibling;
        } catch (Throwable t) {
            MOD.log("no data resource: " + t);
        }
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "BML", "mods",
                       "custom-device-registrar", "config.json");
    }

    private static List<String> folders() throws IOException {
        Path config = configFile();
        List<String> out = new ArrayList<>();
        if (!Files.isRegularFile(config)) return out;
        Matcher list = FOLDERS.matcher(Files.readString(config, StandardCharsets.UTF_8));
        if (!list.find()) return out;
        Matcher s = STRING.matcher(list.group(1));
        while (s.find()) out.add(s.group(1).replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\"));
        return out;
    }

    /** Every *.help.properties under the folders, as the framework scans them: folder order, then path. */
    private static List<Path> helpFiles() {
        List<Path> all = new ArrayList<>();
        List<String> folders;
        try {
            folders = folders();
        } catch (Throwable t) {
            MOD.log("can't read the framework config", t);
            return all;
        }
        for (String folder : folders) {
            Path root = Path.of(folder);
            if (!Files.isDirectory(root)) continue;
            List<Path> found = new ArrayList<>();
            try {
                Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 32, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.equals(root) || dir.getFileName() == null ? "" : dir.getFileName().toString();
                        return name.startsWith(".") || name.equals("auto-backups") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && file.getFileName().toString().endsWith(SUFFIX)) found.add(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | RuntimeException e) {
                MOD.log("scan of " + folder + " failed: " + e);
            }
            found.sort(null);
            all.addAll(found);
        }
        return all;
    }
}
