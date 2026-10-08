package com.example.realhardcore;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    }

    /**
     * ЛОГИКА 1: ЗАПРЕТ ТОТЕМОВ И СООБЩЕНИЕ В ЧАТ
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            Level level = player.level();
            
            if (!level.isClientSide() && level.getLevelData().isHardcore()) {
                boolean hadTotem = false;

                if (player.getMainHandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                    player.getMainHandItem().shrink(1);
                    hadTotem = true;
                }
                if (player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                    player.getOffhandItem().shrink(1);
                    hadTotem = true;
                }

                if (hadTotem) {
                    String playerName = player.getGameProfile().getName();
                    Component customDeathMessage = Component.literal(playerName + " пытался спастись тотемом бессмертия от смерти")
                            .withStyle(ChatFormatting.RED);
                    
                    if (level.getServer() != null) {
                        level.getServer().getPlayerList().broadcastSystemMessage(customDeathMessage, false);
                    }
                }
            }
        }
    }

    /**
     * ЛОГИКА 2 и 3: РАБОТА С КЛИЕНТСКИМ ИНТЕРФЕЙСОМ (Без builder() и лямбд)
     */
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientHandler {

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.getLevelData().isHardcore()) return;

            // 2. Блокировка кнопки читов в LAN-меню
            if (event.getScreen() instanceof ShareToLanScreen) {
                List<GuiEventListener> listeners = event.getListenersList();
                for (GuiEventListener listener : listeners) {
                    if (listener instanceof Button) {
                        Button button = (Button) listener;
                        String msg = button.getMessage().getString().toLowerCase();
                        if (msg.contains("читы") || msg.contains("cheat")) {
                            button.active = false;
                        }
                    }
                }
            }

            // 3. Изменение экрана смерти (Замена кнопки "Главное меню")
            if (event.getScreen() instanceof DeathScreen) {
                Button titleButton = null;
                List<GuiEventListener> listeners = event.getListenersList();
                
                for (GuiEventListener listener : listeners) {
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

                    // Создаем анонимный обработчик нажатия
                    Button.OnPress pressAction = new Button.OnPress() {
                        @Override
                        public void onPress(Button button) {
                            deleteCurrentWorldAndLeave(mc);
                        }
                    };

                    Component btnText = Component.literal("УДАЛИТЬ МИР").withStyle(ChatFormatting.RED);

                    // Прямой вызов конструктора (напрямую создаем объект Button)
                    Button myNewButton = new Button(x, y, width, height, btnText, pressAction, Button.DEFAULT_NARRATION);

                    event.addListener(myNewButton);
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
