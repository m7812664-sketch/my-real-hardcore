package com.example.realhardcore;

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

@Mod(RealHardcoreMod.MODID)
public class RealHardcoreMod {
    public static final String MODID = "examplemod";

    public RealHardcoreMod() {
        MinecraftForge.EVENT_BUS.register(this);
        
        // Безопасное подключение клиентских интерфейсов
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register();
        }
    }

    /**
     * ЛОГИКА 1: ЗАПРЕТ ТОТЕМОВ И РУССКОЕ КАСТОМНОЕ СООБЩЕНИЕ В ЧАТ
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
                    Component customDeathMessage = Component.literal("§c" + playerName + " пытался спастись тотемом бессмертия от смерти");
                    
                    if (level.getServer() != null) {
                        level.getServer().getPlayerList().broadcastSystemMessage(customDeathMessage, false);
                    }
                }
            }
        }
    }

    /**
     * ЛОГИКА 2 и 3: ИНТЕРФЕЙС НА РУССКОМ (Изолировано только для Клиента)
     */
    @OnlyIn(Dist.CLIENT)
    public static class ClientEvents {
        
        public static void register() {
            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.getLevelData().isHardcore()) return;

            // Блокировка кнопки читов в LAN-меню
            if (event.getScreen() instanceof ShareToLanScreen) {
                event.getListeners().stream()
                        .filter(listener -> listener instanceof Button)
                        .map(listener -> (Button) listener)
                        .forEach(button -> {
                            String msg = button.getMessage().getString().toLowerCase();
                            if (msg.contains("читы") || msg.contains("cheat")) {
                                button.active = false;
                            }
                        });
            }

            // Изменение экрана смерти (Кнопка УДАЛИТЬ МИР)
            if (event.getScreen() instanceof DeathScreen) {
                Button titleButton = null;
                for (var listener : event.getListeners()) {
                    if (listener instanceof Button btn) {
                        String msg = btn.getMessage().getString().toLowerCase();
                        if (msg.contains("меню") || msg.contains("title") || msg.contains("leave")) {
                            titleButton = btn;
                        }
                    }
                }

                if (titleButton != null) {
                    event.removeListener(titleButton);
                    
                    int x = titleButton.getX();
                    int y = titleButton.getY();
                    int width = titleButton.getWidth();
                    int height = titleButton.getHeight();

                    event.addListener(Button.builder(
                            Component.literal("§cУДАЛИТЬ МИР"), 
                            button -> deleteCurrentWorldAndLeave(mc)
                    ).bounds(x, y, width, height).build());
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
                        
                        System.out.println("[РеальныйХардкор] Мир успешно удален.");
                    }
                } catch (IOException e) {
                    System.err.println("[РеальныйХардкор] Ошибка удаления мира: " + e.getMessage());
                }
            }
        }
    }
}
