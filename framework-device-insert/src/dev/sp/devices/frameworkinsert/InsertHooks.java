package dev.sp.devices.frameworkinsert;

import bitwig.obf.Qly; // a library root, as the device registry resolves paths against it
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.base.serial.file.Pv; // Bitwig's file format: reads a document's metadata
import com.bitwig.flt.app.gEH; // the application; gEH.beI(UUID) is its package-UUID -> file lookup
import com.bitwig.flt.file.format.FloatDocumentRelatedFileFormat; // the format devices are saved in
import com.bitwig.flt.file.format.bNN; // reads values out of a document's metadata
import com.bitwig.flt.packaging.core.SJF; // the native device registry

import java.io.File;
import java.io.IOException;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patchbay's "Framework: Nitro Devices" registers a device under a registry path such as
 * "devices/Foo.bwdevice", and points project recall at the file it scanned. Every other lookup
 * of a package file by UUID (inserting a device from the API, loading a device's preset) goes
 * through the application's gEH.beI(UUID), which joins the registry path to Bitwig's own
 * Library folder, where the file is not. This mod replaces that join: when the stock file does
 * not exist, it returns the file with that UUID from the framework's configured folders.
 */
public final class InsertHooks {
    private InsertHooks() {}

    static final Bml.Mod MOD = Bml.mod("framework-device-insert");
    private static final Pattern FOLDERS = Pattern.compile("\"folders\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Object LOCK = new Object();
    private static Map<UUID, File> files = Map.of();
    private static boolean scanned;

    /** Replaces SJF.beI(uuid, root) inside gEH.beI(UUID): the registry path joined to Bitwig's Library. */
    @Hook(id = "framework-device-insert-file", target = gEH.class, method = "beI(Ljava/util/UUID;)Ljava/io/File;",
          at = At.REPLACE_CALL, callOwner = SJF.class, call = "beI(Ljava/util/UUID;LQly;)Ljava/io/File;", occurrence = 1)
    public static File packageFile(UUID uuid, Qly root) {
        File stock = SJF.beI(uuid, root);
        if (stock != null && stock.isFile()) return stock;
        try {
            File found = lookup(uuid, false);
            if (found == null || !found.isFile()) found = lookup(uuid, true); // new or moved file: scan again
            if (found != null && found.isFile()) {
                MOD.log("uuid=" + uuid + " -> " + found);
                return found;
            }
            MOD.log("uuid=" + uuid + ": no file in Bitwig's Library or the framework's folders");
        } catch (Throwable t) {
            MOD.log("lookup of " + uuid + " failed", t);
        }
        return stock;
    }

    private static File lookup(UUID uuid, boolean rescan) {
        synchronized (LOCK) {
            if (rescan || !scanned) {
                files = scan();
                scanned = true;
            }
            return files.get(uuid);
        }
    }

    /** The framework's config file: next to this mod's own data folder, else where Patchbay keeps it on macOS. */
    private static Path configFile() {
        try {
            Path own = MOD.resource("data"); // <data>/mods/framework-device-insert/data
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
        if (!Files.isRegularFile(config)) {
            MOD.log("no framework config at " + config);
            return out;
        }
        Matcher list = FOLDERS.matcher(Files.readString(config, StandardCharsets.UTF_8));
        if (!list.find()) return out;
        Matcher s = STRING.matcher(list.group(1));
        while (s.find()) out.add(s.group(1).replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\"));
        return out;
    }

    /** UUID -> file for every device, modulator and module under the folders; the first file of a UUID wins, as in the framework. */
    private static Map<UUID, File> scan() {
        long start = System.nanoTime();
        Map<UUID, File> map = new HashMap<>();
        List<String> folders;
        try {
            folders = folders();
        } catch (Throwable t) {
            MOD.log("can't read the framework config", t);
            return map;
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
                        if (attrs.isRegularFile() && isPackage(file.getFileName().toString())) found.add(file);
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
            for (Path file : found) {
                try {
                    UUID uuid = readUuid(file.toFile());
                    if (uuid != null) map.putIfAbsent(uuid, file.toAbsolutePath().normalize().toFile());
                } catch (Throwable t) {
                    MOD.log("skipping " + file + ", can't read its UUID: " + t);
                }
            }
        }
        MOD.log("scanned " + folders.size() + " folders: " + map.size() + " files, " + (System.nanoTime() - start) / 1_000_000 + " ms");
        return Map.copyOf(map);
    }

    private static boolean isPackage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bwdevice") || lower.endsWith(".bwmodulator") || lower.endsWith(".bwmodule");
    }

    @SuppressWarnings("unchecked")
    private static UUID readUuid(File file) throws Exception {
        Pv format = FloatDocumentRelatedFileFormat.PJ3();
        return bNN.Anf((Map<Object, Object>) format.Gef(file));
    }
}
