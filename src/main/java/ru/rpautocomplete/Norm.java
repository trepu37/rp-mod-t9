package ru.rpautocomplete;

import java.util.Locale;

/** Нормализация строк для сравнения: регистр и буква «ё» не имеют значения. */
public final class Norm {
    private Norm() {}

    public static String n(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
