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
    private TranslationLoader translationLoader;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.translationLoader = TranslationLoader.setup(this, "translations", "messages").useOverrideSystem().load();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("reloadlang", (source, args) -> {
                this.translationLoader.load();
                source.getSender().sendPlainMessage("reloaded");
            });

            event.registrar().register("debuglang", (source, args) -> {
                source.getSender().sendPlainMessage(StreamSupport.stream(GlobalTranslator.translator().sources().spliterator(), false)
                    .map(translator -> translator.name().asMinimalString()).collect(Collectors.joining(", ")));
            });
        });
    }

    @EventHandler
    public void on(PlayerJumpEvent event) {
        event.getPlayer().sendMessage(Component.translatable("bad"));
    }
}
