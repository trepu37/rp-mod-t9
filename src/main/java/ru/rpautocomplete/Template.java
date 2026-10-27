package ru.rpautocomplete;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Одна RP-отыгровка. Поля публичные, чтобы Gson мог их читать и писать в JSON.
 * Несколько вариантов для одного сокращения = несколько шаблонов с одинаковым сокращением.
 */
public final class Template {
    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String category = Categories.OTHER;
    /** Полный текст, как он попадёт в чат (вместе с командой, например "/me достал..."). */
    public String text = "";
    public List<String> abbreviations = new ArrayList<>();
    public boolean favorite = false;
    public int uses = 0;
    public long lastUsed = 0L;

    public Template() {}

    public Template(String title, String category, String text, String... abbrs) {
        this.title = title;
        this.category = category;
        this.text = text;
        for (String a : abbrs) {
            this.abbreviations.add(a);
        }
        normalize();
    }

    /** Команда шаблона в нижнем регистре без «/», либо "" если шаблон — обычный текст. */
    public String command() {
        if (text == null || !text.startsWith("/")) return "";
        int sp = text.indexOf(' ');
        String c = sp < 0 ? text.substring(1) : text.substring(1, sp);
        return c.toLowerCase(Locale.ROOT);
    }

    /** Текст без команды. */
    public String body() {
        if (text == null) return "";
        if (!text.startsWith("/")) return text;
        int sp = text.indexOf(' ');
        return sp < 0 ? "" : text.substring(sp + 1);
    }

    public String abbreviationsText() {
        return String.join(", ", abbreviations);
    }

    /** Приводит данные (например, из импортированного файла) в порядок. */
    public void normalize() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (text == null) text = "";
        text = text.strip();
        if (title == null) title = "";
        title = title.strip();
        if (title.isEmpty()) {
            title = text.length() > 40 ? text.substring(0, 40) + "…" : text;
        }
        if (category == null || category.isBlank()) category = Categories.OTHER;
        category = category.strip();
        List<String> clean = new ArrayList<>();
        if (abbreviations != null) {
            for (String a : abbreviations) {
                String x = cleanAbbreviation(a);
                if (!x.isEmpty() && !clean.contains(x)) clean.add(x);
            }
        }
        abbreviations = clean;
        if (uses < 0) uses = 0;
        if (lastUsed < 0) lastUsed = 0;
    }

    public Template copyAsVariant() {
        Template t = new Template();
        t.title = title + " (вариант)";
        t.category = category;
        t.text = text;
        t.abbreviations = new ArrayList<>(abbreviations);
        return t;
    }

    public static String cleanAbbreviation(String a) {
        if (a == null) return "";
        String x = Norm.n(a.strip());
        while (x.startsWith("/")) x = x.substring(1);
        return x.replaceAll("\\s+", "");
    }

    /** "дост, уд; рц" -> [дост, уд, рц] */
    public static List<String> parseAbbreviations(String input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String part : input.split("[,;\\s]+")) {
            String x = cleanAbbreviation(part);
            if (!x.isEmpty() && !out.contains(x)) out.add(x);
        }
        return out;
    }
}
