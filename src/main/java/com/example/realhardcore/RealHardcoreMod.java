package com.example.realhardcore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@Mod(RealHardcoreMod.MODID)
public class RealHardcoreMod {
    public static final String MODID = "examplemod";

    public RealHardcoreMod() {
        // Регистрируем наш класс в шине событий Forge
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * ЛОГИКА 1: ЗАПРЕТ ТОТЕМОВ БЕССМЕРТИЯ
     * Если игрок умирает с тотемом в руке, мы принудительно очищаем его, 
     * чтобы игра не успела его активировать.
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            Level level = player.level();
            // Проверяем, что включен режим хардкора (или можно проверять всегда)
            if (!level.isClientSide() && level.getLevelData().isHardcore()) {
                // Если в главной или левой руке тотем — удаляем его
                if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    player.getMainHandItem().shrink(1);
                }
                if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                    player.getOffhandItem().shrink(1);
                }
            }
        }
    }

    /**
     * ЛОГИКА 2 и 3: ИНТЕРФЕЙС (Выполняется только на стороне клиента)
     */
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.getLevelData().isHardcore()) return;

            // 2. Блокировка читов в LAN
            if (event.getScreen() instanceof ShareToLanScreen lanScreen) {
                // Ищем кнопку, отвечающую за читы, и отключаем её/делаем неактивной
                event.getListeners().stream()
                        .filter(listener -> listener instanceof Button)
                        .map(listener -> (Button) listener)
                        .forEach(button -> {
                            // В 1.20.1 текст кнопки зависит от перевода, проверяем по ключевым словам
                            String msg = button.getMessage().getString().toLowerCase();
                            if (msg.contains("читы") || msg.contains("cheat")) {
                                button.active = false; // Кнопка станет серой и кликнуть по ней нельзя
                            }
                        });
            }

            // 3. Изменение экрана смерти
            if (event.getScreen() instanceof DeathScreen deathScreen) {
                // Ищем стандартную кнопку "Главное меню" (Title Screen) и удаляем её
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
                    event.removeListener(titleButton); // Убираем старую кнопку
                    
                    // Координаты старой кнопки для сохранения дизайна
                    int x = titleButton.getX();
                    int y = titleButton.getY();
                    int width = titleButton.getWidth();
                    int height = titleButton.getHeight();

                    // Добавляем новую кнопку "УДАЛИТЬ МИР"
                    event.addListener(Button.builder(
                            Component.literal("§cУДАЛИТЬ МИР"), 
                            button -> deleteCurrentWorldAndLeave(mc)
                    ).bounds(x, y, width, height).build());
                }
            }
        }

        // Метод полного удаления папки мира
        private static void deleteCurrentWorldAndLeave(Minecraft mc) {
            if (mc.getSingleplayerServer() != null) {
                String folderName = mc.getSingleplayerServer().getWorldData().getLevelName();
                File savesDir = new File(mc.gameDirectory, "saves");
                File worldFolder = new File(savesDir, mc.getSingleplayerServer().storageSource.getLevelId());

                // Сначала выходим в главное меню, чтобы закрыть все файлы мира
                if (mc.level != null) {
                    mc.level.disconnect();
                }
                mc.clearLevel();
                mc.setScreen(null); // Закрываем экран смерти

                // Удаляем файлы физически
                try {
                    if (worldFolder.exists()) {
                        FileUtils.deleteDirectory(worldFolder);
                        System.out.println("[RealHardcore] Мир успешно удален: " + worldFolder.getName());
                    }
                } catch (IOException e) {
                    System.err.println("[RealHardcore] Не удалось удалить папку мира автоматически: " + e.getMessage());
                }
            }
        }
    }
}
