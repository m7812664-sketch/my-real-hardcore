package com.example.realhardcore;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Mod(RealHardcoreMod.MODID)
public class RealHardcoreMod {
    public static final String MODID = "examplemod";

    public RealHardcoreMod() {
        MinecraftForge.EVENT_BUS.register(this);
        
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(ClientHandler.class);
        }
    }

    /**
     * ЛОГИКА 1: ЗАПРЕТ ТОТЕМОВ И СООБЩЕНИЕ В ЧАТ ЧЕРЕЗ ФАЙЛ ПЕРЕВОДА
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            Level level = player.level();
            
            if (!level.isClientSide() && level.getLevelData().isHardcore()) {
                boolean hadTotem = false;

                if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    player.getMainHandItem().shrink(1);
                    hadTotem = true;
                }
                if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                    player.getOffhandItem().shrink(1);
                    hadTotem = true;
                }

                if (hadTotem) {
                    String playerName = player.getGameProfile().getName();
                    // Используем безопасный метод translatable, который берет русский текст из ru_ru.json
                    Component customDeathMessage = Component.translatable("chat.realhardcore.totem_fail", playerName)
                            .withStyle(ChatFormatting.RED);
                    
                    if (level.getServer() != null) {
                        level.getServer().getPlayerList().broadcastSystemMessage(customDeathMessage, false);
                    }
                }
            }
        }
    }

    /**
     * ЛОГИКА 2 и 3: РАБОТА С КЛИЕНТСКИМ ИНТЕРФЕЙСОМ
     */
    @OnlyIn(Dist.CLIENT)
    public static class ClientHandler {

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.getLevelData().isHardcore()) return;

            // 2. Блокировка кнопки читов в LAN-меню
            if (event.getScreen() instanceof ShareToLanScreen) {
                List<?> listeners = event.getListeners();
                for (Object listener : listeners) {
                    if (listener instanceof Button) {
                        Button button = (Button) listener;
                        String msg = button.getMessage().getString().toLowerCase();
                        if (msg.contains("читы") || msg.contains("cheat")) {
                            button.active = false;
                        }
                    }
                }
            }

            // 3. Изменение экрана смерти
            if (event.getScreen() instanceof DeathScreen) {
                Button titleButton = null;
                List<?> listeners = event.getListeners();
                
                for (Object listener : listeners) {
                    if (listener instanceof Button) {
                        Button btn = (Button) listener;
                        String msg = btn.getMessage().getString().toLowerCase();
                        if (msg.contains("меню") || msg.contains("title") || msg.contains("leave")) {
                            titleButton = btn;
                            break;
                        }
                    }
                }

                if (titleButton != null) {
                    event.removeListener(titleButton);
                    
                    int x = titleButton.getX();
                    int y = titleButton.getY();
                    int width = titleButton.getWidth();
                    int height = titleButton.getHeight();

                    Button.OnPress pressAction = new Button.OnPress() {
                        @Override
                        public void onPress(Button button) {
                            deleteCurrentWorldAndLeave(mc);
                        }
                    };

                    // Текст кнопки подгружается из локализации и окрашивается
                    Component btnText = Component.translatable("gui.realhardcore.delete_world")
                            .withStyle(ChatFormatting.RED);

                    event.addListener(Button.builder(btnText, pressAction)
                            .bounds(x, y, width, height)
                            .build());
                }
            }
        }

        private static void deleteCurrentWorldAndLeave(Minecraft mc) {
            if (mc.getSingleplayerServer() != null) {
                File savesDir = new File(mc.gameDirectory, "saves");
                File worldFolder = new File(savesDir, mc.getSingleplayerServer().storageSource.getLevelId());

                if (mc.level != null) {
                    mc.level.disconnect();
                }
                mc.clearLevel();
                mc.setScreen(null);

                try {
                    if (worldFolder.exists()) {
                        Files.walk(worldFolder.toPath())
                             .sorted(Comparator.reverseOrder())
                             .map(Path::toFile)
                             .forEach(File::delete);
                        
                        System.out.println("[RealHardcore] World directory successfully deleted.");
                    }
                } catch (IOException e) {
                    System.err.println("[RealHardcore] Failed to delete world: " + e.getMessage());
                }
            }
        }
    }
}
