package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemePacksTest {
    @Rule public final TemporaryFolder tmp = new TemporaryFolder();

    @Test public void emptyUntilAPackHasConfig() throws Exception {
        File root = tmp.newFolder("theme");
        assertTrue(ThemePacks.list(root).isEmpty());
        File pack = new File(new File(root, ThemePacks.DIR), "glass");
        assertTrue(pack.mkdirs());
        assertTrue(ThemePacks.list(root).isEmpty());
        Files.write(new File(pack, "config.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(pack, "label.txt").toPath(), "Glass".getBytes(StandardCharsets.UTF_8));
        List<ThemePacks.Pack> packs = ThemePacks.list(root);
        assertEquals(1, packs.size());
        assertEquals("glass", packs.get(0).id);
        assertEquals("Glass", packs.get(0).label);
    }

    @Test public void applyCopiesConfigAndWallpapers() throws Exception {
        File root = tmp.newFolder("theme");
        File pack = new File(new File(root, ThemePacks.DIR), "anime");
        File walls = new File(pack, "wallpapers");
        assertTrue(walls.mkdirs());
        Files.write(new File(pack, "config.json").toPath(),
                "{\"wallpaper\":true}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(walls, "01-rooftop-night.jpg").toPath(), new byte[]{1, 2, 3});
        Files.write(new File(root, "config.json").toPath(),
                "{\"old\":true}".getBytes(StandardCharsets.UTF_8));
        assertTrue(new File(root, "wallpapers").mkdirs());
        Files.write(new File(new File(root, "wallpapers"), "stale.jpg").toPath(), new byte[]{9});
        assertTrue(ThemePacks.apply(root, "anime"));
        String json = new String(Files.readAllBytes(new File(root, "config.json").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(json.contains("wallpaper"));
        assertFalse(new File(new File(root, "wallpapers"), "stale.jpg").isFile());
        assertTrue(new File(new File(root, "wallpapers"), "01-rooftop-night.jpg").isFile());
        assertTrue(new File(root, "theme_revision").isFile());
    }

    @Test public void applyRefusesABadId() {
        assertFalse(ThemePacks.apply(tmp.getRoot(), "../x"));
        assertFalse(ThemePacks.validId("a b"));
        assertTrue(ThemePacks.validId("aurora"));
    }
}
