package com.behamotten.events;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.common.MinecraftForge;

/**
 * Root mod class that registers global listeners and ensures the mod id is declared once.
 */
@Mod(BehamottenEventsMod.MOD_ID)
public final class BehamottenEventsMod {
    public static final String MOD_ID = "behamotten";

    public BehamottenEventsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.register(new EventCommandRegistrar());
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        // Currently unused but kept to demonstrate where future common setup can be placed.
    }
}
