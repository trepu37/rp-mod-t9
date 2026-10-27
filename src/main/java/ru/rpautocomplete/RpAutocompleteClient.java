package ru.rpautocomplete;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rpautocomplete.gui.ManagerScreen;

public final class RpAutocompleteClient implements ClientModInitializer {
    public static final String MOD_ID = "rpautocomplete";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Открыть меню (по умолчанию K). Меняется в Настройки → Управление. */
    public static KeyBinding openMenuKey;
    /** Быстро включить/выключить автодополнение (по умолчанию F8). */
    public static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        Settings.load();
        TemplateStore.get().load();
        WordDictionary.get().loadAsync(); // большой словарь Т9 грузится в фоне

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rpautocomplete.open_menu", InputUtil.Type.KEYSYM, 75 /* K */, "key.categories.rpautocomplete"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rpautocomplete.toggle", InputUtil.Type.KEYSYM, 297 /* F8 */, "key.categories.rpautocomplete"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ManagerScreen(null));
                }
            }
            while (toggleKey.wasPressed()) {
                if (client.currentScreen == null) {
                    boolean on = ChatAutocomplete.toggleEnabled();
                    if (client.inGameHud != null) {
                        client.inGameHud.setOverlayMessage(
                                Text.literal("RP-автодополнение: " + (on ? "ВКЛ" : "ВЫКЛ")), false);
                    }
                }
            }
        });

        ChatAutocomplete.register();
        LOGGER.info("RP Autocomplete загружен. Шаблонов: {}", TemplateStore.get().all().size());
    }
}
