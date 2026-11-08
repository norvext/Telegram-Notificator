package com.norvext.minecraft.notificator;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TelegramNotificator.MODID, dist = Dist.DEDICATED_SERVER)
public class TelegramNotificator {
    public static final String MODID = "telegramnotificator";

    public TelegramNotificator() {
        var config = new Config();
        var notifications = new TelegramNotifications(config);
        var commands = new TelegramCommands(config, notifications);
        var events = NeoForge.EVENT_BUS;
        events.addListener(commands::registerCommands);
        events.addListener(notifications::onPlayerLoggedIn);
        events.addListener(notifications::onPlayerLoggedOut);
        events.addListener(notifications::onServerStarted);
        events.addListener(notifications::onServerStopping);
    }
}
