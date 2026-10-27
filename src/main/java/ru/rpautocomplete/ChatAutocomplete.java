package ru.rpautocomplete;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import ru.rpautocomplete.SuggestionEngine.Suggestion;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Автодополнение внутри стандартного чата.
 *
 * Работает только на клиенте, через публичные события Fabric API (без миксинов):
 *  - список подсказок рисуется над полем ввода;
 *  - Tab (по умолчанию) ВСТАВЛЯЕТ выбранный вариант в поле ввода — ничего не отправляется;
 *  - стрелки вверх/вниз (или Shift+Tab) переключают варианты, Alt+1..9 — выбрать номер;
 *  - Enter всегда отправляет только то, что реально написано в поле ввода.
 */
public final class ChatAutocomplete {
    private ChatAutocomplete() {}

    private static final int CHAT_LIMIT = 256;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^{}\\n]{1,40}\\}");

    private static List<Suggestion> current = List.of();
    private static int selected = 0;
    private static String snapText = null;
    private static int snapCursor = -1;

    // Т9 по словам (работает, когда нет подсказок-шаблонов)
    private static List<String> wordList = List.of();
    private static int wordSel = 0;
    private static int wordStart = 0;

    private static Screen lastScreen = null;
    private static boolean insertedFromTemplate = false;
    private static long lastInsertAt = 0L;

    private static String toast = null;
    private static long toastUntil = 0L;
    private static boolean errorLogged = false;

    // ---------------------------------------------------------------- регистрация

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) return;
            onChatOpened(screen);
            ScreenKeyboardEvents.allowKeyPress(screen).register(ChatAutocomplete::onKey);
            ScreenEvents.afterRender(screen).register(ChatAutocomplete::onRender);
        });
    }

    private static void onChatOpened(Screen screen) {
        try {
            if (screen != lastScreen) {
                lastScreen = screen;
                current = List.of();
                selected = 0;
                wordList = List.of();
                wordSel = 0;
                snapText = null;
                snapCursor = -1;
                insertedFromTemplate = false;
                toast = null;
            }
            String pending = ChatOpener.takePending();
            if (pending != null && !pending.isEmpty()) {
                TextFieldWidget tf = findField(screen);
                if (tf != null) {
                    tf.setText(pending);
                    selectFirstPlaceholderOrEnd(tf, pending);
                    insertedFromTemplate = true;
                    lastInsertAt = System.currentTimeMillis();
                    if (pending.length() > CHAT_LIMIT) {
                        showToast("Текст длиннее " + CHAT_LIMIT + " символов — лишнее будет обрезано");
                    }
                }
            }
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    // ---------------------------------------------------------------- вкл/выкл

    /** @return новое состояние: true = включено. */
    public static boolean toggleEnabled() {
        Settings s = Settings.get();
        s.enabled = !s.enabled;
        s.save();
        current = List.of();
        selected = 0;
        wordList = List.of();
        wordSel = 0;
        snapText = null;
        showToast("RP-автодополнение: " + (s.enabled ? "ВКЛ" : "ВЫКЛ"));
        return s.enabled;
    }

    private static void showToast(String text) {
        toast = text;
        toastUntil = System.currentTimeMillis() + 2800;
    }

    // ---------------------------------------------------------------- клавиатура

    private static boolean onKey(Screen screen, int key, int scancode, int modifiers) {
        try {
            return handleKey(screen, key, scancode, modifiers);
        } catch (Throwable t) {
            logOnce(t);
            return true; // при любой ошибке не мешаем обычной работе чата
        }
    }

    private static boolean handleKey(Screen screen, int key, int scancode, int modifiers) {
        Settings s = Settings.get();

        // Быстрое выключение (по умолчанию F8). Буквенные клавиши в чате не перехватываем — только с Ctrl.
        if (RpAutocompleteClient.toggleKey != null
                && RpAutocompleteClient.toggleKey.matchesKey(key, scancode)
                && (key >= 256 || (modifiers & Keys.MOD_CONTROL) != 0)) {
            toggleEnabled();
            return false;
        }

        if (!s.enabled) return true;
        TextFieldWidget tf = findField(screen);
        if (tf == null) return true;
        refresh(tf);

        boolean shift = (modifiers & Keys.MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & Keys.MOD_CONTROL) != 0;
        boolean alt = (modifiers & Keys.MOD_ALT) != 0;

        // Защита от случайной отправки сразу после вставки шаблона
        if (key == Keys.ENTER || key == Keys.KP_ENTER) {
            if (!insertedFromTemplate) return true;
            String text = tf.getText();
            if (s.guardPlaceholders && PLACEHOLDER.matcher(text).find()) {
                showToast("В тексте остался {заполнитель} — замените его, затем нажмите Enter");
                return false;
            }
            long now = System.currentTimeMillis();
            if (s.guardEnterMs > 0 && now - lastInsertAt < s.guardEnterMs) {
                showToast("Проверьте текст и нажмите Enter ещё раз");
                return false;
            }
            return true;
        }

        if (current.isEmpty()) return handleWordKey(tf, key, modifiers, s);

        // Быстрый выбор: Alt + 1..9
        if (alt && !ctrl && key >= 49 && key <= 57) {
            int idx = key - 49;
            if (idx < current.size()) {
                accept(tf, current.get(idx));
                return false;
            }
            return true;
        }

        if (key == s.keyAccept) {
            if (ctrl || alt) return true;
            if (shift) { // Shift+Tab — следующий вариант по кругу
                move(1);
                return false;
            }
            accept(tf, current.get(selected));
            return false;
        }

        if (current.size() > 1 && !ctrl && !alt && !shift) {
            if (key == s.keyNext) { move(1); return false; }
            if (key == s.keyPrev) { move(-1); return false; }
        }
        return true;
    }

    /** Клавиши в режиме слов: Tab — вставить, Shift+Tab — следующее слово, Alt+1..9 — слово по номеру. Стрелки не трогаем. */
    private static boolean handleWordKey(TextFieldWidget tf, int key, int modifiers, Settings s) {
        if (wordList.isEmpty()) return true;
        boolean shift = (modifiers & Keys.MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & Keys.MOD_CONTROL) != 0;
        boolean alt = (modifiers & Keys.MOD_ALT) != 0;

        if (alt && !ctrl && key >= 49 && key <= 57) {
            int idx = key - 49;
            if (idx < wordList.size()) {
                acceptWord(tf, wordList.get(idx));
                return false;
            }
            return true;
        }
        if (key == s.keyAccept) {
            if (ctrl || alt) return true;
            if (shift) {
                wordSel = (wordSel + 1) % wordList.size();
                return false;
            }
            acceptWord(tf, wordList.get(Math.min(wordSel, wordList.size() - 1)));
            return false;
        }
        return true;
    }

    private static void acceptWord(TextFieldWidget tf, String word) {
        Settings s = Settings.get();
        String text = tf.getText();
        int cursor = Math.max(0, Math.min(tf.getCursor(), text.length()));
        int start = Math.max(0, Math.min(wordStart, cursor));
        String suffix = text.substring(cursor);
        boolean addSpace = s.wordAddSpace && (suffix.isEmpty() || !Character.isWhitespace(suffix.charAt(0)));
        String newBefore = text.substring(0, start) + word + (addSpace ? " " : "");
        tf.setText(newBefore + suffix);
        tf.setCursor(newBefore.length(), false);
        snapText = null;
        wordList = List.of();
        wordSel = 0;
        if (newBefore.length() + suffix.length() > CHAT_LIMIT) {
            showToast("Текст длиннее " + CHAT_LIMIT + " символов — лишнее будет обрезано");
        }
    }

    private static boolean isWordChar(char c) {
        return (c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё';
    }

    private static void move(int delta) {
        if (current.isEmpty()) return;
        int n = current.size();
        selected = ((selected + delta) % n + n) % n;
    }

    // ---------------------------------------------------------------- вставка

    private static void accept(TextFieldWidget tf, Suggestion sg) {
        String text = tf.getText();
        int cursor = Math.max(0, Math.min(tf.getCursor(), text.length()));
        String suffix = text.substring(cursor); // всё, что правее курсора, сохраняется
        String insertion = sg.insertion();
        String full = insertion + suffix;

        tf.setText(full);
        selectFirstPlaceholderOrEnd(tf, insertion);

        TemplateStore.get().markUsed(sg.template());
        insertedFromTemplate = true;
        lastInsertAt = System.currentTimeMillis();
        snapText = null; // пересчитать подсказки
        current = List.of(); // список закрывается сразу после вставки
        selected = 0;
        wordList = List.of();
        wordSel = 0;

        if (full.length() > CHAT_LIMIT) {
            showToast("Текст длиннее " + CHAT_LIMIT + " символов — лишнее будет обрезано");
        }
    }

    /** Если в тексте есть {заполнитель} — выделяем его (можно сразу печатать поверх), иначе курсор в конец вставки. */
    private static void selectFirstPlaceholderOrEnd(TextFieldWidget tf, String insertion) {
        Matcher m = PLACEHOLDER.matcher(insertion);
        if (m.find()) {
            tf.setCursor(m.start(), false);
            tf.setCursor(m.end(), true);
        } else {
            tf.setCursor(insertion.length(), false);
        }
    }

    // ---------------------------------------------------------------- пересчёт подсказок

    private static void refresh(TextFieldWidget tf) {
        String text = tf.getText();
        int cursor = tf.getCursor();
        if (text.equals(snapText) && cursor == snapCursor) return;
        snapText = text;
        snapCursor = cursor;

        Settings s = Settings.get();
        if (!s.enabled) {
            current = List.of();
            selected = 0;
            wordList = List.of();
            wordSel = 0;
            return;
        }
        int cur = Math.max(0, Math.min(cursor, text.length()));
        String before = text.substring(0, cur);
        List<Template> all = TemplateStore.get().all();
        List<Suggestion> list = SuggestionEngine.suggest(before, all, s);

        String keepId = (selected >= 0 && selected < current.size()) ? current.get(selected).template().id : null;
        current = list;
        selected = 0;
        if (keepId != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).template().id.equals(keepId)) { selected = i; break; }
            }
        }

        // Т9 по словам: только когда нет подсказок-шаблонов
        wordList = List.of();
        wordSel = 0;
        if (list.isEmpty() && s.wordsEnabled) {
            boolean cursorInsideWord = cur < text.length() && isWordChar(text.charAt(cur));
            if (!cursorInsideWord && SuggestionEngine.wordsAllowed(before, all, s)) {
                int start = cur;
                while (start > 0 && isWordChar(before.charAt(start - 1))) start--;
                String partial = before.substring(start);
                if (partial.length() >= s.wordMinChars) {
                    wordStart = start;
                    wordList = WordDictionary.get().complete(partial, s.maxWords, s.filterProfanity);
                }
            }
        }
    }

    static TextFieldWidget findField(Screen screen) {
        for (Element e : screen.children()) {
            if (e instanceof TextFieldWidget tf) return tf;
        }
        return null;
    }

    // ---------------------------------------------------------------- отрисовка

    private static void onRender(Screen screen, DrawContext ctx, int mouseX, int mouseY, float tickDelta) {
        try {
            draw(screen, ctx);
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    private static void draw(Screen screen, DrawContext ctx) {
        Settings s = Settings.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        TextFieldWidget tf = findField(screen);
        if (tf == null) return;

        long now = System.currentTimeMillis();
        String toastNow = (toast != null && now < toastUntil) ? toast : null;

        if (s.enabled) {
            refresh(tf);
        } else {
            current = List.of();
        }

        int bottom = screen.height - 16; // над полем ввода чата
        int x = 4;
        int width = Math.min(screen.width - 8, 420);

        if (current.isEmpty() && !wordList.isEmpty() && s.enabled) {
            drawWords(ctx, tr, s, toastNow, x, bottom, width);
            return;
        }

        if (current.isEmpty()) {
            if (toastNow != null) {
                drawToastOnly(ctx, tr, toastNow, x, bottom, width);
            } else if (!s.enabled) {
                String off = "RP-автодополнение выключено (" + keyName(RpAutocompleteClient.toggleKey) + " — включить)";
                ctx.drawTextWithShadow(tr, off, x + 2, bottom - 9, 0xFF8A8A8A);
            }
            return;
        }

        int shown = Math.min(Math.max(s.maxSuggestions, 1), current.size());
        Suggestion sel = current.get(Math.min(selected, current.size() - 1));

        List<String> preview = s.showPreview ? Ui.wrap(tr, sel.insertion(), width - 10, 3) : List.of();
        int h = 4;
        if (toastNow != null) h += 12;
        if (!preview.isEmpty()) h += preview.size() * 10 + 4;
        h += shown * 12;
        if (s.showHint) h += 12;

        int top = bottom - h;
        ctx.fill(x, top, x + width, bottom, 0xE0101010);
        int y = top + 3;

        if (toastNow != null) {
            ctx.drawTextWithShadow(tr, Ui.fit(tr, toastNow, width - 8), x + 4, y, 0xFFFFC857);
            y += 12;
        }
        if (!preview.isEmpty()) {
            for (String line : preview) {
                ctx.drawTextWithShadow(tr, line, x + 5, y, 0xFFE8E8E8);
                y += 10;
            }
            y += 4;
        }

        int start = Math.max(0, Math.min(selected - shown / 2, current.size() - shown));
        for (int i = 0; i < shown; i++) {
            int idx = start + i;
            Suggestion sg = current.get(idx);
            boolean isSel = idx == selected;
            int ry = y + i * 12;
            if (isSel) ctx.fill(x + 2, ry - 1, x + width - 2, ry + 10, 0xFF2B4B73);

            Template t = sg.template();
            String label = (idx + 1) + ". " + (t.favorite ? "★ " : "") + t.title;
            int maxLabel = Math.min(width / 2, 200);
            label = Ui.fit(tr, label, maxLabel);
            int lw = tr.getWidth(label);
            ctx.drawTextWithShadow(tr, label, x + 5, ry, isSel ? 0xFFFFFFFF : 0xFFC8C8C8);

            int previewX = x + 5 + lw + 8;
            int previewW = x + width - 6 - previewX;
            if (previewW > 20) {
                ctx.drawTextWithShadow(tr, Ui.fit(tr, sg.insertion(), previewW), previewX, ry,
                        isSel ? 0xFFB8D4FF : 0xFF8F8F8F);
            }
        }
        y += shown * 12;

        if (s.showHint) {
            String hint = Keys.name(s.keyAccept) + " — вставить   "
                    + (current.size() > 1 ? Keys.name(s.keyPrev) + Keys.name(s.keyNext) + " — выбор   Alt+1…9 — быстро   " : "")
                    + keyName(RpAutocompleteClient.toggleKey) + " — выкл";
            ctx.drawTextWithShadow(tr, Ui.fit(tr, hint, width - 50), x + 5, y + 1, 0xFF7A7A7A);
            String counter = (Math.min(selected, current.size() - 1) + 1) + "/" + current.size();
            ctx.drawTextWithShadow(tr, counter, x + width - 5 - tr.getWidth(counter), y + 1, 0xFF7A7A7A);
        }
    }

    /** Компактная строка со словами над полем ввода: «1 гречка  2 греческий  3 гречневый». */
    private static void drawWords(DrawContext ctx, TextRenderer tr, Settings s, String toastNow, int x, int bottom, int width) {
        int top = bottom - 14;
        if (toastNow != null) {
            drawToastOnly(ctx, tr, toastNow, x, top, width);
        }
        ctx.fill(x, top, x + width, bottom, 0xD0101010);
        int cx = x + 4;
        int limit = x + width - 4;
        int sel = Math.min(wordSel, wordList.size() - 1);
        for (int i = 0; i < wordList.size(); i++) {
            String num = String.valueOf(i + 1);
            String w = wordList.get(i);
            int itemW = tr.getWidth(num) + 4 + tr.getWidth(w) + 8;
            if (cx + itemW > limit) break;
            if (i == sel) ctx.fill(cx - 2, top + 1, cx + itemW - 4, bottom - 1, 0xFF2B4B73);
            ctx.drawTextWithShadow(tr, num, cx, top + 3, 0xFF7A7A7A);
            ctx.drawTextWithShadow(tr, w, cx + tr.getWidth(num) + 4, top + 3, i == sel ? 0xFFFFFFFF : 0xFFD0D0D0);
            cx += itemW;
        }
        if (s.showHint) {
            String hint = Keys.name(s.keyAccept) + " — вставить";
            int hw = tr.getWidth(hint);
            if (cx + hw + 8 < limit) {
                ctx.drawTextWithShadow(tr, hint, limit - hw, top + 3, 0xFF6A6A6A);
            }
        }
    }

    private static void drawToastOnly(DrawContext ctx, TextRenderer tr, String text, int x, int bottom, int width) {
        ctx.fill(x, bottom - 14, x + width, bottom, 0xE0101010);
        ctx.drawTextWithShadow(tr, Ui.fit(tr, text, width - 8), x + 4, bottom - 11, 0xFFFFC857);
    }

    private static String keyName(KeyBinding kb) {
        if (kb == null) return "?";
        return kb.getBoundKeyLocalizedText().getString();
    }

    private static void logOnce(Throwable t) {
        if (errorLogged) return;
        errorLogged = true;
        RpAutocompleteClient.LOGGER.error("Ошибка в автодополнении чата (дальнейшие ошибки не логируются)", t);
    }
}
