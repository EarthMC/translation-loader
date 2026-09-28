package net.earthmc.translation.loader;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.translation.Translator;
import org.bukkit.plugin.java.JavaPlugin;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.Contract;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A library for loading embedded translation files for serverside translations.
 */
public class TranslationLoader {
    private final JavaPlugin plugin;
    private final String folder;
    private final String bundleName;

    private int reloadCount;
    private boolean firstLoad = true;

    private Path overrideSystemRoot;
    private TranslationStore<?> loadedStore; // The translation store currently loaded into the global translator

    private TranslationLoader(JavaPlugin plugin, String folder, String bundleName) {
        this.plugin = plugin;
        this.folder = folder;
        this.bundleName = bundleName;
    }

    /**
     * Creates a new {@link TranslationLoader} instance with the given options.
     *
     * @param plugin Your plugin's instance.
     * @param folder The folder within the resources folder that contains the translation files.
     * @param bundleName The base name of the resource bundle for the translation.
     * @return A new {@link TranslationLoader} instance.
     */
    @Contract(pure = true)
    public static TranslationLoader setup(final JavaPlugin plugin, final String folder, final String bundleName) {
        return new TranslationLoader(plugin, folder, bundleName);
    }

    /**
     * Enables using the translation override system, using the default path of 'lang' to store reference and override files.
     * @return {@code this} for chaining
     */
    public TranslationLoader useOverrideSystem() {
        this.overrideSystemRoot = plugin.getDataPath().resolve("lang");
        return this;
    }

    /**
     * Enables using the translation override system, using the given path to store reference and override files.
     * @param langFolder The root language folder, typically located inside the plugin's data folder.
     * @return {@code this} for chaining
     */
    public TranslationLoader useOverrideSystem(final Path langFolder) {
        this.overrideSystemRoot = langFolder;
        return this;
    }

    /**
     * Loads or reloads the language files, and optionally the override files if enabled.
     *
     * @return {@code this} for chaining
     */
    public TranslationLoader load() {
        final @Subst("namespace") String namespace = plugin.namespace();
        final @Subst("0") int reloadCount = this.reloadCount++;

        final MiniMessageTranslationStore store = MiniMessageTranslationStore.create(Key.key(namespace, "translations/" + reloadCount));

        if (this.overrideSystemRoot == null) {
            loadViaBundle(store);
        } else {
            try {
                loadWithOverrides(store);
            } catch (IOException e) {
                plugin.getSLF4JLogger().warn("An exception occurred while {}loading translations", firstLoad ? "" : "re", e);
            }
        }

        firstLoad = false;

        final Translator oldStore = this.loadedStore;
        this.loadedStore = store;

        GlobalTranslator.translator().addSource(store);

        if (oldStore != null) {
            GlobalTranslator.translator().removeSource(oldStore);
        }

        return this;
    }

    private void loadViaBundle(final MiniMessageTranslationStore store) {
        for (final Locale locale : readLocalesFromJar()) {
            try {
                final ResourceBundle bundle = ResourceBundle.getBundle(folder + "." + bundleName, locale, plugin.getClass().getClassLoader());

                store.registerAll(locale, bundle, false);
            } catch (MissingResourceException ignored) {}
        }
    }

    private void loadWithOverrides(final MiniMessageTranslationStore store) throws IOException {
        final Path overrideFolder = overrideSystemRoot.resolve("override");

        final Map<Locale, Map<String, String>> translations = new HashMap<>();
        final Collection<Path> languageFiles = readLanguageFilesFromJar();

        if (firstLoad) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                final Path referenceFolder = overrideSystemRoot.resolve("reference");

                try {
                    Files.createDirectories(referenceFolder);

                    for (final Path file : languageFiles) {
                        try (final InputStream is = plugin.getClass().getResourceAsStream("/" + folder + "/" + file.getFileName())) {
                            if (is != null) {
                                Files.copy(is, referenceFolder.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                } catch (IOException ignored) {}
            });
        }

        for (final Path file : languageFiles) {
            // The filesystem for these files will likely not exist so only the file name can be relied upon
            final Locale locale = parseLocale(file);
            if (translations.containsKey(locale)) {
                continue;
            }

            try (final InputStream is = plugin.getClass().getResourceAsStream("/" + folder + "/" + file.getFileName())) {
                if (is != null) {
                    final Properties properties = new Properties();
                    properties.load(is);

                    final Map<String, String> language = HashMap.newHashMap(properties.size());
                    translations.put(locale, language);
                    merge(properties, language);
                }
            }
        }

        Files.createDirectories(overrideFolder);

        try (final Stream<Path> overrideFilesStream = Files.list(overrideFolder)) {
            for (final Path overrideFile : overrideFilesStream.filter(Files::isRegularFile).toList()) {
                final Locale locale = parseLocale(overrideFile);

                final Map<String, String> language = translations.computeIfAbsent(locale, k -> new HashMap<>());

                try (final BufferedReader reader = Files.newBufferedReader(overrideFile)) {
                    final Properties properties = new Properties();
                    properties.load(reader);
                    merge(properties, language);
                }
            }
        }

        for (final Map.Entry<Locale, Map<String, String>> entry : translations.entrySet()) {
            store.registerAll(entry.getKey(), entry.getValue());
        }
    }

    private void merge(final Properties properties, final Map<String, String> into) {
        for (final String key : properties.stringPropertyNames()) {
            final String value = properties.getProperty(key);
            if (value != null) {
                into.put(key, value);
            }
        }
    }

    private Collection<Path> readLanguageFilesFromJar() {
        final Set<Path> files = new HashSet<>();

        try {
            URL root = plugin.getClass().getResource("");
            if (root == null) return files;

            useFileSystem(root.toURI(), fileSystem -> {
                try (final Stream<Path> stream  = Files.list(fileSystem.getRootDirectories().iterator().next().resolve("/" + folder))) {
                    stream
                        .filter(path -> {
                            final String fileName = path.getFileName().toString();
                            return fileName.startsWith(bundleName) && fileName.endsWith(".properties");
                        })
                        .forEach(files::add);
                } catch (IOException e) {
                    plugin.getSLF4JLogger().warn("Failed to read resource bundle jars from the plugin jar", e);
                }
            });
        } catch (URISyntaxException | IOException e) {
            plugin.getSLF4JLogger().warn("Failed to read resource bundle jars from the plugin jar", e);
        }

        return files;
    }

    private Collection<Locale> readLocalesFromJar() {
        return readLanguageFilesFromJar().stream()
            .map(this::parseLocale)
            .filter(locale -> !locale.getLanguage().isEmpty())
            .collect(Collectors.toSet());
    }

    private Locale parseLocale(final Path file) {
        String fileName = file.getFileName().toString();
        fileName = fileName.substring(bundleName.length(), fileName.lastIndexOf('.'));

        if (fileName.isEmpty()) {
            return Locale.US;
        }

        return Locale.forLanguageTag(fileName.substring(1).replace("_", "-"));
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

    /**
     * Unregisters the translations loaded by this loader from the global translator if necessary.
     */
    public void unload() {
        if (this.loadedStore != null) {
            GlobalTranslator.translator().removeSource(this.loadedStore);
            this.loadedStore = null;
        }
    }
}
