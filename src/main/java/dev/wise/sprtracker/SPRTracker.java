package dev.wise.sprtracker;

import dev.wise.sprtracker.command.TrackerCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SPRTracker extends JavaPlugin {

    private TrackerConfig trackerConfig;
    private PrefsStore prefs;
    private WebhookClient webhook;
    private AlertService alerts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        trackerConfig = new TrackerConfig(this);
        prefs = new PrefsStore(this);
        webhook = new WebhookClient(this, trackerConfig);
        alerts = new AlertService(this, trackerConfig, prefs, webhook);
        webhook.start();

        getServer().getPluginManager().registerEvents(new AlertListener(this, trackerConfig, alerts), this);

        TrackerCommand executor = new TrackerCommand(this, trackerConfig, prefs, alerts, webhook);
        PluginCommand command = getCommand("tracker");
        if (command == null) {
            getLogger().severe("command tracker missing from plugin.yml");
        } else {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("up. totem/crystal/kill alerts.");
    }

    @Override
    public void onDisable() {
        if (webhook != null) {
            webhook.stop();
        }
        if (prefs != null) {
            prefs.saveAll();
        }
    }

    public void reloadTracker() {
        trackerConfig.reload();
        prefs.reload();
        webhook.reload();
        alerts.reload();
    }

    public TrackerConfig trackerConfig() {
        return trackerConfig;
    }

    public PrefsStore prefs() {
        return prefs;
    }

    public WebhookClient webhook() {
        return webhook;
    }

    public AlertService alerts() {
        return alerts;
    }
}
