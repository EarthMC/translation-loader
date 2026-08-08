package net.earthmc.translation.test_plugin;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.earthmc.translation.loader.TranslationLoader;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class TestPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        TranslationLoader.load(this, "translations", "messages");
    }

    @EventHandler
    public void on(PlayerJumpEvent event) {
        event.getPlayer().sendMessage(Component.translatable("bad"));
    }
}
