package ru.rpautocomplete.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ru.rpautocomplete.Keys;
import ru.rpautocomplete.Norm;
import ru.rpautocomplete.Settings;
import ru.rpautocomplete.Ui;

import java.util.ArrayList;
import java.util.List;

/** Настройки автодополнения. Сохраняются при закрытии экрана. */
public final class SettingsScreen extends Screen {
    private enum Capture { NONE, ACCEPT, NEXT, PREV }

    private static final int[] GUARD_VALUES = {0, 400, 700, 1500};

    private final Screen parent;
    private final Settings s = Settings.get();

    private ButtonWidget enabledBtn, countBtn, minBtn, guardBtn, placeholderBtn, previewBtn, hintBtn;
    private ButtonWidget acceptBtn, nextBtn, prevBtn;
    private TextFieldWidget triggerField, commandsField;
    private Capture capture = Capture.NONE;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Настройки автодополнения"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int lx = cx - 155;
        int rx = cx + 5;
        int bw = 150;
        int y = 28;
        int step = 24;

        enabledBtn = add(lx, y, bw, b -> { s.enabled = !s.enabled; refreshLabels(); });
        y += step;
        countBtn = add(lx, y, bw, b -> { s.maxSuggestions = s.maxSuggestions % 8 + 1; refreshLabels(); });
        y += step;
        minBtn = add(lx, y, bw, b -> { s.minChars = s.minChars % 4 + 1; refreshLabels(); });
        y += step;
        guardBtn = add(lx, y, bw, b -> {
            int i = 0;
            for (int k = 0; k < GUARD_VALUES.length; k++) if (GUARD_VALUES[k] == s.guardEnterMs) i = k;
            s.guardEnterMs = GUARD_VALUES[(i + 1) % GUARD_VALUES.length];
            refreshLabels();
        });
        y += step;
        placeholderBtn = add(lx, y, bw, b -> { s.guardPlaceholders = !s.guardPlaceholders; refreshLabels(); });
        y += step;
        previewBtn = add(lx, y, bw, b -> { s.showPreview = !s.showPreview; refreshLabels(); });
        y += step;
        hintBtn = add(lx, y, bw, b -> { s.showHint = !s.showHint; refreshLabels(); });

        // правая колонка
        triggerField = new TextFieldWidget(this.textRenderer, rx, 38, bw, 18, Text.literal("Триггер"));
        triggerField.setMaxLength(20);
        triggerField.setText(s.trigger);
        triggerField.setChangedListener(v -> s.trigger = v.strip());
        addDrawableChild(triggerField);

        commandsField = new TextFieldWidget(this.textRenderer, rx, 76, bw, 18, Text.literal("Команды"));
        commandsField.setMaxLength(100);
        commandsField.setText(String.join(", ", s.rpCommands));
        commandsField.setChangedListener(v -> {
            List<String> list = new ArrayList<>();
            for (String part : v.split("[,;\\s]+")) {
                String x = Norm.n(part.strip());
                while (x.startsWith("/")) x = x.substring(1);
                if (!x.isEmpty() && !list.contains(x)) list.add(x);
            }
            s.rpCommands = list;
        });
        addDrawableChild(commandsField);

        acceptBtn = add(rx, 114, bw, b -> startCapture(Capture.ACCEPT));
        nextBtn = add(rx, 138, bw, b -> startCapture(Capture.NEXT));
        prevBtn = add(rx, 162, bw, b -> startCapture(Capture.PREV));

        add(rx, 186, bw, b -> this.client.setScreen(new WordSettingsScreen(this)))
                .setMessage(Text.literal("Т9 по словам..."));

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(cx - 75, this.height - 26, 150, 20).build());

        refreshLabels();
    }

    private ButtonWidget add(int x, int y, int w, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(""), action).dimensions(x, y, w, 20).build());
    }

    private void startCapture(Capture c) {
        capture = c;
        refreshLabels();
    }

    private void refreshLabels() {
        enabledBtn.setMessage(Text.literal("Автодополнение: " + (s.enabled ? "ВКЛ" : "ВЫКЛ")));
        countBtn.setMessage(Text.literal("Вариантов в списке: " + s.maxSuggestions));
        minBtn.setMessage(Text.literal("Мин. символов: " + s.minChars));
        guardBtn.setMessage(Text.literal("Защита Enter: " + (s.guardEnterMs == 0 ? "выкл" : (s.guardEnterMs / 1000.0) + " с")));
        placeholderBtn.setMessage(Text.literal("Блок. {заполнители}: " + (s.guardPlaceholders ? "ВКЛ" : "ВЫКЛ")));
        previewBtn.setMessage(Text.literal("Полный текст: " + (s.showPreview ? "ВКЛ" : "ВЫКЛ")));
        hintBtn.setMessage(Text.literal("Подсказка клавиш: " + (s.showHint ? "ВКЛ" : "ВЫКЛ")));
        acceptBtn.setMessage(Text.literal(capture == Capture.ACCEPT ? "Нажмите клавишу..." : "Вставить: " + Keys.name(s.keyAccept)));
        nextBtn.setMessage(Text.literal(capture == Capture.NEXT ? "Нажмите клавишу..." : "Следующий: " + Keys.name(s.keyNext)));
        prevBtn.setMessage(Text.literal(capture == Capture.PREV ? "Нажмите клавишу..." : "Предыдущий: " + Keys.name(s.keyPrev)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capture != Capture.NONE) {
            if (keyCode != Keys.ESCAPE) {
                switch (capture) {
                    case ACCEPT -> s.keyAccept = keyCode;
                    case NEXT -> s.keyNext = keyCode;
                    case PREV -> s.keyPrev = keyCode;
                    default -> { }
                }
            }
            capture = Capture.NONE;
            refreshLabels();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        int rx = cx + 5;

        ctx.drawTextWithShadow(tr, "Настройки автодополнения", cx - 155, 10, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, "Слово-триггер поиска:", rx, 27, 0xFFAAAAAA);
        ctx.drawTextWithShadow(tr, "RP-команды (через запятую):", rx, 65, 0xFFAAAAAA);
        ctx.drawTextWithShadow(tr, "Клавиши в чате (нажмите и выберите):", rx, 103, 0xFFAAAAAA);

        int y = 188;
        String note = "Клавиши меню и вкл/выкл: Настройки → Управление → RP-отыгровки.";
        for (String line : Ui.wrap(tr, note, 150, 2)) {
            ctx.drawTextWithShadow(tr, line, cx - 155, y, 0xFF888888);
            y += 10;
        }
    }
}
