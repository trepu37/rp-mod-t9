package ru.rpautocomplete.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ru.rpautocomplete.Categories;
import ru.rpautocomplete.ChatOpener;
import ru.rpautocomplete.Norm;
import ru.rpautocomplete.Template;
import ru.rpautocomplete.TemplateStore;
import ru.rpautocomplete.Ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Главное меню: список шаблонов, поиск, категории, избранное, импорт/экспорт. */
public final class ManagerScreen extends Screen {
    private enum Mode {
        ALL("Все"), FAVORITES("Избранное"), RECENT("Недавние");

        final String label;

        Mode(String label) { this.label = label; }

        Mode next() {
            Mode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    private static final int ROW_H = 24;

    private final Screen parent;
    private final TemplateStore store = TemplateStore.get();

    private TextFieldWidget searchBox;
    private ButtonWidget categoryButton;
    private ButtonWidget modeButton;
    private ButtonWidget deleteButton;
    private ButtonWidget favButton;

    private String searchText = "";
    private String category = null; // null = все категории
    private Mode mode = Mode.ALL;
    private List<Template> view = new ArrayList<>();
    private Template selected;
    private int scroll = 0;

    private boolean confirmDelete = false;
    private long confirmUntil = 0L;
    private String status = "";
    private boolean statusError = false;
    private long statusUntil = 0L;
    private long lastClickAt = 0L;
    private int lastClickIndex = -1;

    private int listLeft, listTop, listRight, listBottom, detailLeft, detailRight;

    public ManagerScreen(Screen parent) {
        super(Text.literal("RP-отыгровки"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ построение

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int m = 10;

        listLeft = m;
        listTop = 48;
        listBottom = h - 66;
        int listW = Math.max(160, (int) ((w - 2 * m) * 0.56));
        listRight = listLeft + listW;
        detailLeft = listRight + 8;
        detailRight = w - m;
        int dw = detailRight - detailLeft;

        searchBox = new TextFieldWidget(this.textRenderer, listLeft, 22, listW, 18, Text.literal("Поиск"));
        searchBox.setMaxLength(100);
        searchBox.setPlaceholder(Text.literal("Поиск: название, сокращение, текст..."));
        searchBox.setText(searchText);
        searchBox.setChangedListener(v -> {
            searchText = v;
            scroll = 0;
            refreshView();
        });
        addDrawableChild(searchBox);
        setInitialFocus(searchBox);

        categoryButton = addDrawableChild(ButtonWidget.builder(categoryLabel(), b -> cycleCategory())
                .dimensions(detailLeft, 22, dw / 2 - 2, 18).build());
        modeButton = addDrawableChild(ButtonWidget.builder(modeLabel(), b -> {
            mode = mode.next();
            modeButton.setMessage(modeLabel());
            scroll = 0;
            refreshView();
        }).dimensions(detailLeft + dw / 2 + 2, 22, dw - dw / 2 - 2, 18).build());

        int gap = 4;
        int bw = (w - 2 * m - 5 * gap) / 6;
        int y1 = h - 50;
        int x = m;
        button("Добавить", x, y1, bw, this::onAdd);
        x += bw + gap;
        button("Изменить", x, y1, bw, this::openEdit);
        x += bw + gap;
        deleteButton = button("Удалить", x, y1, bw, this::onDelete);
        x += bw + gap;
        favButton = button("★ Избр.", x, y1, bw, this::onFavorite);
        x += bw + gap;
        button("+ Вариант", x, y1, bw, this::onVariant);
        x += bw + gap;
        button("В чат", x, y1, bw, this::onToChat);

        int bw2 = (w - 2 * m - 4 * gap) / 5;
        int y2 = h - 26;
        x = m;
        button("Импорт: файл", x, y2, bw2, this::onImportFile);
        x += bw2 + gap;
        button("Импорт: буфер", x, y2, bw2, this::onImportClipboard);
        x += bw2 + gap;
        button("Экспорт", x, y2, bw2, this::onExport);
        x += bw2 + gap;
        button("Настройки", x, y2, bw2, () -> this.client.setScreen(new SettingsScreen(this)));
        x += bw2 + gap;
        button("Закрыть", x, y2, bw2, this::close);

        refreshView();
    }

    private ButtonWidget button(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(x, y, w, 20).build());
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    // ------------------------------------------------------------------ данные

    private Text categoryLabel() {
        return Text.literal("Категория: " + (category == null ? "Все" : category));
    }

    private Text modeLabel() {
        return Text.literal("Показ: " + mode.label);
    }

    private void cycleCategory() {
        List<String> cats = Categories.all(store.all());
        if (category == null) {
            category = cats.get(0);
        } else {
            int i = -1;
            for (int k = 0; k < cats.size(); k++) {
                if (cats.get(k).equalsIgnoreCase(category)) { i = k; break; }
            }
            category = (i < 0 || i + 1 >= cats.size()) ? null : cats.get(i + 1);
        }
        categoryButton.setMessage(categoryLabel());
        scroll = 0;
        refreshView();
    }

    private void refreshView() {
        String q = Norm.n(searchText).strip();
        List<Template> out = new ArrayList<>();
        for (Template t : store.all()) {
            if (mode == Mode.FAVORITES && !t.favorite) continue;
            if (mode == Mode.RECENT && t.lastUsed <= 0) continue;
            if (category != null && !category.equalsIgnoreCase(t.category)) continue;
            if (!q.isEmpty() && !matches(t, q)) continue;
            out.add(t);
        }
        if (mode == Mode.RECENT) {
            out.sort((a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        } else {
            out.sort((a, b) -> {
                if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
                return a.title.compareToIgnoreCase(b.title);
            });
        }
        view = out;
        if (selected != null && !view.contains(selected)) selected = null;
        clampScroll();
    }

    private static boolean matches(Template t, String q) {
        String hay = Norm.n(t.title) + " " + String.join(" ", t.abbreviations) + " "
                + Norm.n(t.category) + " " + Norm.n(t.text);
        for (String token : q.split("\\s+")) {
            if (!token.isEmpty() && !hay.contains(token)) return false;
        }
        return true;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private void clampScroll() {
        int max = Math.max(0, view.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void select(Template t) {
        selected = t;
        confirmDelete = false;
        deleteButton.setMessage(Text.literal("Удалить"));
    }

    private void moveSelection(int delta) {
        if (view.isEmpty()) return;
        int idx = selected == null ? (delta > 0 ? -1 : view.size()) : view.indexOf(selected);
        idx = Math.max(0, Math.min(view.size() - 1, idx + delta));
        select(view.get(idx));
        if (idx < scroll) scroll = idx;
        if (idx >= scroll + visibleRows()) scroll = idx - visibleRows() + 1;
        clampScroll();
    }

    private void message(String text, boolean error) {
        status = text;
        statusError = error;
        statusUntil = System.currentTimeMillis() + 7000;
    }

    // ------------------------------------------------------------------ действия

    private void onAdd() {
        this.client.setScreen(new EditScreen(this, new Template(), true));
    }

    private void openEdit() {
        if (selected == null) { message("Сначала выберите шаблон в списке", true); return; }
        this.client.setScreen(new EditScreen(this, selected, false));
    }

    private void onDelete() {
        if (selected == null) { message("Сначала выберите шаблон в списке", true); return; }
        long now = System.currentTimeMillis();
        if (!confirmDelete || now > confirmUntil) {
            confirmDelete = true;
            confirmUntil = now + 3000;
            deleteButton.setMessage(Text.literal("Точно удалить?"));
            return;
        }
        store.remove(selected);
        message("Удалено: " + selected.title, false);
        selected = null;
        confirmDelete = false;
        deleteButton.setMessage(Text.literal("Удалить"));
        refreshView();
    }

    private void onFavorite() {
        if (selected == null) { message("Сначала выберите шаблон в списке", true); return; }
        selected.favorite = !selected.favorite;
        store.save();
        refreshView();
    }

    private void onVariant() {
        if (selected == null) { message("Выберите шаблон, к сокращению которого нужен ещё один вариант", true); return; }
        this.client.setScreen(new EditScreen(this, selected.copyAsVariant(), true));
    }

    private void onToChat() {
        if (selected == null) { message("Сначала выберите шаблон в списке", true); return; }
        String text = selected.text;
        store.markUsed(selected);
        this.client.setScreen(null);
        boolean opened = ChatOpener.openChatWith(this.client, text);
        if (!opened && this.client.inGameHud != null) {
            this.client.inGameHud.setOverlayMessage(
                    Text.literal("Откройте чат — текст шаблона будет вставлен автоматически"), false);
        }
    }

    private void onExport() {
        try {
            Path p = store.exportToFile();
            this.client.keyboard.setClipboard(store.exportJson());
            message("Экспорт: " + relative(p) + " (JSON также скопирован в буфер обмена)", false);
        } catch (Exception e) {
            message("Ошибка экспорта: " + e.getMessage(), true);
        }
    }

    private void onImportFile() {
        try {
            TemplateStore.ImportResult r = store.importFromFolder();
            if (r.files() == 0) {
                message("Нет файлов. Положите .json в папку config/rpautocomplete/import и нажмите снова", true);
            } else {
                message("Импорт из файлов (" + r.files() + "): добавлено " + r.added() + ", пропущено повторов " + r.skipped(), false);
            }
            refreshView();
        } catch (Exception e) {
            message("Ошибка импорта: " + e.getMessage(), true);
        }
    }

    private void onImportClipboard() {
        try {
            String clip = this.client.keyboard.getClipboard();
            TemplateStore.ImportResult r = store.importJson(clip);
            message("Импорт из буфера: добавлено " + r.added() + ", пропущено повторов " + r.skipped(), false);
            refreshView();
        } catch (Exception e) {
            message("Ошибка импорта: " + e.getMessage(), true);
        }
    }

    private static String relative(Path p) {
        try {
            return FabricLoader.getInstance().getGameDir().relativize(p).toString();
        } catch (Exception e) {
            return p.toString();
        }
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 265) { moveSelection(-1); return true; }
        if (keyCode == 264) { moveSelection(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - listTop) / ROW_H);
            int idx = scroll + row;
            if (row < visibleRows() && idx >= 0 && idx < view.size()) {
                long now = System.currentTimeMillis();
                if (idx == lastClickIndex && now - lastClickAt < 350) {
                    select(view.get(idx));
                    lastClickAt = 0;
                    openEdit(); // двойной клик = изменить
                    return true;
                }
                lastClickIndex = idx;
                lastClickAt = now;
                select(view.get(idx));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scroll -= (int) Math.signum(verticalAmount);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;
        long now = System.currentTimeMillis();

        if (confirmDelete && now > confirmUntil) {
            confirmDelete = false;
            deleteButton.setMessage(Text.literal("Удалить"));
        }
        favButton.setMessage(Text.literal(selected != null && selected.favorite ? "☆ Убрать" : "★ Избр."));

        ctx.drawTextWithShadow(tr, "RP-отыгровки", listLeft, 8, 0xFFFFFFFF);
        String count = "Шаблонов: " + store.all().size() + "   Показано: " + view.size();
        ctx.drawTextWithShadow(tr, count, detailRight - tr.getWidth(count), 8, 0xFFAAAAAA);

        // --- список
        ctx.fill(listLeft, listTop, listRight, listBottom, 0xB0000000);
        int visible = visibleRows();
        if (view.isEmpty()) {
            String empty = store.all().isEmpty() ? "Список пуст. Нажмите «Добавить»." : "Ничего не найдено.";
            ctx.drawTextWithShadow(tr, empty, listLeft + 6, listTop + 8, 0xFF999999);
        }
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= view.size()) break;
            Template t = view.get(idx);
            int ry = listTop + i * ROW_H;
            boolean sel = t == selected;
            boolean hover = mouseX >= listLeft && mouseX < listRight && mouseY >= ry && mouseY < ry + ROW_H;
            if (sel) {
                ctx.fill(listLeft + 1, ry, listRight - 7, ry + ROW_H - 1, 0xFF2B4B73);
            } else if (hover) {
                ctx.fill(listLeft + 1, ry, listRight - 7, ry + ROW_H - 1, 0x40FFFFFF);
            }
            String cat = "[" + t.category + "]";
            int catW = tr.getWidth(cat);
            int innerW = listRight - listLeft - 14;
            String title = (t.favorite ? "★ " : "") + t.title;
            ctx.drawTextWithShadow(tr, Ui.fit(tr, title, innerW - catW - 8), listLeft + 4, ry + 3,
                    t.favorite ? 0xFFFFE08A : 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, cat, listLeft + 4 + innerW - catW, ry + 3, 0xFF8FB4E8);
            String abbr = t.abbreviations.isEmpty() ? "без сокращения" : t.abbreviationsText();
            ctx.drawTextWithShadow(tr, Ui.fit(tr, abbr + " — " + t.text, innerW), listLeft + 4, ry + 13, 0xFF9A9A9A);
        }
        if (view.size() > visible) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(12, trackH * visible / view.size());
            int maxScroll = view.size() - visible;
            int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
            ctx.fill(listRight - 5, listTop, listRight - 2, listBottom, 0x60FFFFFF);
            ctx.fill(listRight - 5, thumbY, listRight - 2, thumbY + thumbH, 0xFFCCCCCC);
        }

        // --- описание выбранного
        ctx.fill(detailLeft, listTop, detailRight, listBottom, 0xB0000000);
        int dx = detailLeft + 6;
        int dy = listTop + 6;
        int dwid = detailRight - detailLeft - 12;
        if (selected == null) {
            for (String line : Ui.wrap(tr, "Выберите шаблон слева. Двойной клик — изменить. Стрелки ↑↓ — перемещение по списку.", dwid, 6)) {
                ctx.drawTextWithShadow(tr, line, dx, dy, 0xFF999999);
                dy += 10;
            }
        } else {
            ctx.drawTextWithShadow(tr, Ui.fit(tr, selected.title, dwid), dx, dy, 0xFFFFFFFF);
            dy += 12;
            ctx.drawTextWithShadow(tr, Ui.fit(tr, "Категория: " + selected.category, dwid), dx, dy, 0xFF8FB4E8);
            dy += 11;
            String ab = selected.abbreviations.isEmpty() ? "нет" : selected.abbreviationsText();
            ctx.drawTextWithShadow(tr, Ui.fit(tr, "Сокращения: " + ab, dwid), dx, dy, 0xFFB6E3A8);
            dy += 11;
            ctx.drawTextWithShadow(tr, "Использован раз: " + selected.uses, dx, dy, 0xFF8A8A8A);
            dy += 14;
            int maxLines = Math.max(1, (listBottom - dy - 4) / 10);
            for (String line : Ui.wrap(tr, selected.text, dwid, maxLines)) {
                ctx.drawTextWithShadow(tr, line, dx, dy, 0xFFE8E8E8);
                dy += 10;
            }
        }

        // --- статус
        if (!status.isEmpty() && now < statusUntil) {
            ctx.drawTextWithShadow(tr, Ui.fit(tr, status, this.width - 20), 10, this.height - 62,
                    statusError ? 0xFFFF7B7B : 0xFF7CFC9A);
        }
    }
}
