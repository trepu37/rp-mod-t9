package ru.rpautocomplete;

import java.util.ArrayList;
import java.util.List;

public final class Categories {
    private Categories() {}

    public static final String OTHER = "Другое";

    public static final List<String> DEFAULTS = List.of(
            "Полиция", "Медицина", "Повседневные", "Разговоры",
            "Обыск", "Задержание", "Документы", OTHER);

    /** Стандартные категории + любые свои, которые встречаются в шаблонах. */
    public static List<String> all(List<Template> templates) {
        List<String> out = new ArrayList<>(DEFAULTS);
        for (Template t : templates) {
            String c = t.category;
            if (c == null || c.isBlank()) continue;
            boolean exists = false;
            for (String e : out) {
                if (e.equalsIgnoreCase(c)) { exists = true; break; }
            }
            if (!exists) out.add(c);
        }
        return out;
    }
}
