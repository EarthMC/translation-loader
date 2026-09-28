package net.earthmc.translation.test_plugin;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.earthmc.translation.loader.TranslationLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class TestPlugin extends JavaPlugin implements Listener {
    private final TranslationLoader translationLoader = TranslationLoader.setup(this, "translations", "messages") // Set up the loader to look for files in the translations folder with a base name of messages.
        // useOverrideSystem enables functionality where translation files will be copied to the /plugins/name/lang/reference folder for viewing,
        // and files from the /plugins/name/lang/override folder will be loaded on top of the base translations in the jar.
        .useOverrideSystem();

    @Override
    public void onEnable() {
        // load() loads or reloads all translations into the global translator, requires the plugin to be enabled.
        this.translationLoader.load();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("reloadlang", (source, args) -> {
                // Example of reloading, uses the same method.
                this.translationLoader.load();
                source.getSender().sendPlainMessage("reloaded");
            });

            event.registrar().register("debuglang", (source, args) -> {
                source.getSender().sendPlainMessage(StreamSupport.stream(GlobalTranslator.translator().sources().spliterator(), false)
                    .map(translator -> translator.name().asString()).collect(Collectors.joining(", ")));
            });
        });

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void on(PlayerJumpEvent event) {
        event.getPlayer().sendMessage(Component.translatable("bad"));
    }
}
