package com.reiko.economy;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point. This class never loads on a dedicated server, so client code is safe here.
 */
@Mod(value = ReikoEconomy.MODID, dist = Dist.CLIENT)
public final class ReikoEconomyClient {
    public ReikoEconomyClient(ModContainer container) {
        // Adds a "Config" button for this mod on the Mods screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
