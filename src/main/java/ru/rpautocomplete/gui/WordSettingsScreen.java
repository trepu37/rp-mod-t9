package ru.rpautocomplete.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.rpautocomplete.Settings;
import ru.rpautocomplete.Ui;
import ru.rpautocomplete.WordDictionary;

/** Настройки Т9 по словам («греч» → гречка). */
public final class WordSettingsScreen extends Screen {
    private final Screen parent;
    private final Settings s = Settings.get();
    private ButtonWidget enabledBtn, countBtn, minBtn, spaceBtn, profBtn;

    public WordSettingsScreen(Screen parent) {
        super(Text.literal("Т9 по словам"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - 100;
        int y = 34;
        enabledBtn = add(x, y, b -> { s.wordsEnabled = !s.wordsEnabled; refresh(); });
        y += 24;
        countBtn = add(x, y, b -> { s.maxWords = s.maxWords % 9 + 1; refresh(); });
        y += 24;
        minBtn = add(x, y, b -> { s.wordMinChars = s.wordMinChars % 4 + 1; refresh(); });
        y += 24;
        spaceBtn = add(x, y, b -> { s.wordAddSpace = !s.wordAddSpace; refresh(); });
        y += 24;
        profBtn = add(x, y, b -> { s.filterProfanity = !s.filterProfanity; refresh(); });

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(cx - 75, this.height - 26, 150, 20).build());
        refresh();
    }

    private ButtonWidget add(int x, int y, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(""), action).dimensions(x, y, 200, 20).build());
    }

    private void refresh() {
        enabledBtn.setMessage(Text.literal("Т9 по словам: " + (s.wordsEnabled ? "ВКЛ" : "ВЫКЛ")));
        countBtn.setMessage(Text.literal("Слов в подсказке: " + s.maxWords));
        minBtn.setMessage(Text.literal("С какой буквы подсказывать: " + s.wordMinChars));
        spaceBtn.setMessage(Text.literal("Пробел после слова: " + (s.wordAddSpace ? "ВКЛ" : "ВЫКЛ")));
        profBtn.setMessage(Text.literal("Скрывать мат: " + (s.filterProfanity ? "ВКЛ" : "ВЫКЛ")));
    }

    @Override
    public void removed() {
        s.sanitize();
        s.save();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        ctx.drawTextWithShadow(tr, "Т9 по словам", cx - 100, 14, 0xFFFFFFFF);

        WordDictionary d = WordDictionary.get();
        String info = d.isLoaded()
                ? "Словарь загружен: " + d.size() + " слов (+ слова из ваших шаблонов)"
                : "Большой словарь не найден в моде — подсказываются слова из ваших шаблонов.";
        int y = 34 + 5 * 24 + 4;
        for (String line : Ui.wrap(tr, info, 200, 3)) {
            ctx.drawTextWithShadow(tr, line, cx - 100, y, d.isLoaded() ? 0xFF7CFC9A : 0xFFFFC857);
            y += 10;
        }
    }
}
