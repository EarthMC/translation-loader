package net.earthmc.translation.loader;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.plugin.java.JavaPlugin;
import org.intellij.lang.annotations.Subst;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Stream;

public class TranslationLoader {
    private TranslationLoader() {}

    public static void load(final JavaPlugin plugin, final String folder, final String bundleName) {
        final @Subst("namespace") String namespace = plugin.namespace();
        final MiniMessageTranslationStore store = MiniMessageTranslationStore.create(Key.key(namespace, "translations"));

        for (final Locale locale : readLocalesFromJar(plugin, folder, bundleName)) {
            try {
                final ResourceBundle bundle = ResourceBundle.getBundle(folder + "." + bundleName, locale, plugin.getClass().getClassLoader());

                store.registerAll(locale, bundle, false);
            } catch (MissingResourceException ignored) {}
        }

        GlobalTranslator.translator().addSource(store);
    }

    private static Collection<Locale> readLocalesFromJar(final JavaPlugin plugin, final String folder, final String bundleName) {
        final Set<Locale> locales = new HashSet<>();

        try {
            URL root = plugin.getClass().getResource("");
            if (root == null) return locales;

            try (FileSystem fs = FileSystems.newFileSystem(root.toURI(), Collections.emptyMap()); Stream<Path> stream  = Files.list(fs.getRootDirectories().iterator().next().resolve("/" + folder))) {
                stream.map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.startsWith(bundleName) && fileName.endsWith(".properties"))
                    .map(fileName -> fileName.substring((bundleName).length(), fileName.lastIndexOf(".")))
                    .map(locale -> {
                        if (locale.isEmpty()) {
                            return Locale.ENGLISH;
                        }

                        return Locale.forLanguageTag(locale.substring(1).replace("_", "-"));
                    })
                    .filter(locale -> !locale.getLanguage().isEmpty())
                    .forEach(locales::add);
            }
        } catch (URISyntaxException | IOException e) {
            plugin.getSLF4JLogger().warn("Failed to read resource bundle jars from the plugin jar", e);
        }

        return locales;
    }
}
