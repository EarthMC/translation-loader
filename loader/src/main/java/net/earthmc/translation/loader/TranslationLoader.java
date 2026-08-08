package net.earthmc.translation.loader;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.plugin.java.JavaPlugin;
import org.intellij.lang.annotations.Subst;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
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
import java.util.function.Consumer;
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

            useFileSystem(root.toURI(), fileSystem -> {
                try (final Stream<Path> stream  = Files.list(fileSystem.getRootDirectories().iterator().next().resolve("/" + folder))) {
                    stream.map(path -> path.getFileName().toString())
                        .filter(fileName -> fileName.startsWith(bundleName) && fileName.endsWith(".properties"))
                        .map(fileName -> fileName.substring((bundleName).length(), fileName.lastIndexOf(".")))
                        .map(locale -> {
                            if (locale.isEmpty()) {
                                return Locale.US; // US specifically is the default fallback lang
                            }

                            return Locale.forLanguageTag(locale.substring(1).replace("_", "-"));
                        })
                        .filter(locale -> !locale.getLanguage().isEmpty())
                        .forEach(locales::add);
                } catch (IOException e) {
                    plugin.getSLF4JLogger().warn("Failed to read resource bundle jars from the plugin jar", e);
                }
            });
        } catch (URISyntaxException | IOException e) {
            plugin.getSLF4JLogger().warn("Failed to read resource bundle jars from the plugin jar", e);
        }

        return locales;
    }

    private static void useFileSystem(final URI uri, Consumer<FileSystem> fileSystemUser) throws IOException {
        // Workaround an issue where a filesystem stays open indefinitely when using the datapack discovery API
        try {
            fileSystemUser.accept(FileSystems.getFileSystem(uri));
        } catch (FileSystemNotFoundException e) {
            try (final FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                fileSystemUser.accept(fs);
            }
        }
    }
}
