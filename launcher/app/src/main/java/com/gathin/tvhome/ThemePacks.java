package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Theme folders kept under {@code packs/} so Home can switch looks without ADB. A pack is a folder
 * with {@code config.json} and optional {@code wallpaper.jpg} / {@code wallpapers/}. Nothing runs
 * until at least one pack is present.
 */
final class ThemePacks {
    static final String DIR = "packs";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    static final class Pack {
        final String id;
        final String label;
        Pack(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private ThemePacks() {}

    static boolean validId(String id) {
        return id != null && NAME.matcher(id).matches();
    }

    static File packDir(File themeDir, String id) {
        if (themeDir == null || !validId(id)) return null;
        return new File(new File(themeDir, DIR), id);
    }

    static List<Pack> list(File themeDir) {
        if (themeDir == null) return Collections.emptyList();
        File root = new File(themeDir, DIR);
        File[] kids = root.listFiles();
        if (kids == null || kids.length == 0) return Collections.emptyList();
        List<Pack> out = new ArrayList<>();
        for (File kid : kids) {
            if (!kid.isDirectory() || !validId(kid.getName())) continue;
            File config = new File(kid, Config.FILE_NAME);
            if (!config.isFile()) continue;
            out.add(new Pack(kid.getName(), labelOf(kid)));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static String labelOf(File pack) {
        File label = new File(pack, "label.txt");
        if (label.isFile()) {
            try {
                String line = new String(Files.readAllBytes(label.toPath()), StandardCharsets.UTF_8)
                        .trim().split("\\r?\\n", 2)[0].trim();
                if (!line.isEmpty()) return line;
            } catch (IOException ignored) {
            }
        }
        String id = pack.getName().replace('-', ' ');
        if (id.isEmpty()) return id;
        return id.substring(0, 1).toUpperCase(Locale.US) + id.substring(1);
    }

    /** Copies the pack over the active theme files. The packs folder itself is left alone. */
    static boolean apply(File themeDir, String id) {
        File src = packDir(themeDir, id);
        if (themeDir == null || src == null) return false;
        File config = new File(src, Config.FILE_NAME);
        if (!config.isFile()) return false;
        try {
            copyFile(config, new File(themeDir, Config.FILE_NAME));
            File srcWall = new File(src, Config.WALLPAPER_NAME);
            File dstWall = new File(themeDir, Config.WALLPAPER_NAME);
            if (srcWall.isFile()) copyFile(srcWall, dstWall);
            else if (dstWall.isFile() && !dstWall.delete()) return false;
            File dstCol = new File(themeDir, ThemeFiles.WALLPAPERS);
            clearCollection(dstCol);
            File srcCol = new File(src, ThemeFiles.WALLPAPERS);
            File[] images = srcCol.listFiles((d, name) -> ThemeFiles.collectionImage(name)
                    || WallpaperCaptions.FILE_NAME.equals(name));
            if (images != null) {
                for (File image : images) copyFile(image, new File(dstCol, image.getName()));
            }
            Files.write(new File(themeDir, "theme_revision").toPath(),
                    Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void clearCollection(File dir) {
        File[] images = dir.listFiles((d, name) -> ThemeFiles.collectionImage(name)
                || WallpaperCaptions.FILE_NAME.equals(name));
        if (images == null) return;
        for (File image : images) image.delete();
    }

    private static void copyFile(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException(parent.getPath());
        }
        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
