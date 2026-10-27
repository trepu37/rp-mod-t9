package ru.rpautocomplete.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ru.rpautocomplete.Categories;
import ru.rpautocomplete.Template;
import ru.rpautocomplete.TemplateStore;
import ru.rpautocomplete.Ui;

import java.util.List;

/** Создание и редактирование одной отыгровки. */
public final class EditScreen extends Screen {
    private static final int TEXT_LIMIT = 256;

    private final Screen parent;
    private final Template tpl;
    private final boolean isNew;

    private String titleVal;
    private String abbrVal;
    private String catVal;
    private String textVal;

    private TextFieldWidget titleField, abbrField, catField, textField;
    private String error = "";

    private int left, cw;
    private static final int Y_TITLE = 36, Y_ABBR = 72, Y_CAT = 108, Y_TEXT = 144;

    public EditScreen(Screen parent, Template tpl, boolean isNew) {
        super(Text.literal(isNew ? "Новая отыгровка" : "Редактирование"));
        this.parent = parent;
        this.tpl = tpl;
        this.isNew = isNew;
        this.titleVal = tpl.title;
        this.abbrVal = tpl.abbreviationsText();
        this.catVal = tpl.category;
        this.textVal = tpl.text;
    }

    @Override
    protected void init() {
        cw = Math.min(this.width - 40, 380);
        left = this.width / 2 - cw / 2;

        titleField = field(left, Y_TITLE, cw, 80, titleVal, "Например: Показать удостоверение");
        titleField.setChangedListener(v -> titleVal = v);

        abbrField = field(left, Y_ABBR, cw, 120, abbrVal, "Например: дост, уд");
        abbrField.setChangedListener(v -> abbrVal = v);

        catField = field(left, Y_CAT, cw - 66, 40, catVal, "Категория");
        catField.setChangedListener(v -> catVal = v);
        addDrawableChild(ButtonWidget.builder(Text.literal("Сменить"), b -> cycleCategory())
                .dimensions(left + cw - 62, Y_CAT, 62, 18).build());

        textField = field(left, Y_TEXT, cw, TEXT_LIMIT, textVal, "/me достал удостоверение...");
        textField.setChangedListener(v -> textVal = v);

        setInitialFocus(titleField);

        int by = this.height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), b -> save())
                .dimensions(this.width / 2 - 104, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close())
                .dimensions(this.width / 2 + 4, by, 100, 20).build());
    }

    private TextFieldWidget field(int x, int y, int w, int maxLen, String value, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(placeholder));
        f.setMaxLength(maxLen);
        f.setPlaceholder(Text.literal(placeholder));
        f.setText(value == null ? "" : value);
        addDrawableChild(f);
        return f;
    }

    private void cycleCategory() {
        List<String> cats = Categories.all(TemplateStore.get().all());
        int i = -1;
        for (int k = 0; k < cats.size(); k++) {
            if (cats.get(k).equalsIgnoreCase(catVal == null ? "" : catVal.strip())) { i = k; break; }
        }
        catVal = cats.get((i + 1) % cats.size());
        catField.setText(catVal);
    }

    private void save() {
        String text = textVal == null ? "" : textVal.strip();
        if (text.isEmpty()) {
            error = "Введите текст отыгровки";
            return;
        }
        tpl.text = text;
        tpl.title = titleVal == null ? "" : titleVal.strip();
        tpl.category = catVal == null ? "" : catVal.strip();
        tpl.abbreviations = Template.parseAbbreviations(abbrVal);
        tpl.normalize();

        TemplateStore store = TemplateStore.get();
        if (isNew) {
            store.add(tpl);
        } else {
            store.save();
        }
        close();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private int sharedCount() {
        List<String> mine = Template.parseAbbreviations(abbrVal);
        if (mine.isEmpty()) return 0;
        int n = 0;
        for (Template t : TemplateStore.get().all()) {
            if (t == tpl) continue;
            for (String a : mine) {
                if (t.abbreviations.contains(a)) { n++; break; }
            }
        }
        return n;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;

        ctx.drawTextWithShadow(tr, this.title.getString(), left, 12, 0xFFFFFFFF);

        ctx.drawTextWithShadow(tr, "Название", left, Y_TITLE - 11, 0xFFAAAAAA);
        ctx.drawTextWithShadow(tr, "Сокращения (через запятую)", left, Y_ABBR - 11, 0xFFAAAAAA);
        int shared = sharedCount();
        if (shared > 0) {
            String info = "уже у " + shared + " шт. — станут вариантами";
            ctx.drawTextWithShadow(tr, info, left + cw - tr.getWidth(info), Y_ABBR - 11, 0xFFFFC857);
        }
        ctx.drawTextWithShadow(tr, "Категория", left, Y_CAT - 11, 0xFFAAAAAA);

        int len = textVal == null ? 0 : textVal.length();
        ctx.drawTextWithShadow(tr, "Текст (вместе с командой: /me, /do, /todo)", left, Y_TEXT - 11, 0xFFAAAAAA);
        String counter = len + "/" + TEXT_LIMIT;
        ctx.drawTextWithShadow(tr, counter, left + cw - tr.getWidth(counter), Y_TEXT - 11,
                len >= TEXT_LIMIT ? 0xFFFF7B7B : 0xFF888888);

        // полный предпросмотр текста (поле ввода однострочное)
        int py = Y_TEXT + 24;
        int maxLines = Math.max(1, (this.height - 36 - py) / 10);
        for (String line : Ui.wrap(tr, textVal, cw, maxLines)) {
            ctx.drawTextWithShadow(tr, line, left, py, 0xFFE0E0E0);
            py += 10;
        }

        if (!error.isEmpty()) {
            ctx.drawTextWithShadow(tr, error, left, this.height - 40, 0xFFFF7B7B);
        }
    }
}
