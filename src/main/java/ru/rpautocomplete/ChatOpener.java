package ru.rpautocomplete;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;

/**
 * Открывает чат с заранее вставленным текстом (кнопка «В чат» в меню).
 * Конструктор ChatScreen ищется через рефлексию, чтобы не зависеть от его точной сигнатуры.
 */
public final class ChatOpener {
    private ChatOpener() {}

    private static String pending;

    /** Текст, который нужно вставить при следующем открытии чата. */
    public static String takePending() {
        String p = pending;
        pending = null;
        return p;
    }

    /** @return true, если чат открыт; false — текст будет вставлен при ближайшем открытии чата вручную. */
    public static boolean openChatWith(MinecraftClient mc, String text) {
        pending = text;
        Screen chat = createChat();
        if (chat == null) return false;
        mc.setScreen(chat);
        return true;
    }

    private static Screen createChat() {
        try {
            for (Constructor<?> c : ChatScreen.class.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 0 || p[0] != String.class) continue;
                Object[] args = new Object[p.length];
                args[0] = "";
                boolean ok = true;
                for (int i = 1; i < p.length; i++) {
                    if (p[i] == boolean.class) {
                        args[i] = Boolean.FALSE;
                    } else {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                c.setAccessible(true);
                return (Screen) c.newInstance(args);
            }
        } catch (Throwable t) {
            RpAutocompleteClient.LOGGER.warn("Не удалось открыть чат программно", t);
        }
        return null;
    }
}
