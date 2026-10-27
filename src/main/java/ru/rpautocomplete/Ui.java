package ru.rpautocomplete;

import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

/** Мелкие помощники для отрисовки текста. */
public final class Ui {
    private Ui() {}

    /** Обрезает строку по ширине, добавляя «…». */
    public static String fit(TextRenderer tr, String s, int maxWidth) {
        if (s == null) return "";
        if (maxWidth <= 0) return "";
        if (tr.getWidth(s) <= maxWidth) return s;
        int ell = tr.getWidth("…");
        String cut = tr.trimToWidth(s, Math.max(0, maxWidth - ell));
        return cut + "…";
    }

    /** Переносит текст по словам. Если строк больше maxLines — последняя заканчивается на «…». */
    public static List<String> wrap(TextRenderer tr, String text, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 8) return out;
        StringBuilder line = new StringBuilder();
        boolean truncated = false;

        for (String word : text.split(" ", -1)) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (tr.getWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                if (maxLines > 0 && out.size() >= maxLines) { truncated = true; break; }
            }
            String w = word;
            while (tr.getWidth(w) > maxWidth) {
                String part = tr.trimToWidth(w, maxWidth);
                if (part.isEmpty()) { w = ""; break; }
                out.add(part);
                w = w.substring(part.length());
                if (maxLines > 0 && out.size() >= maxLines) { truncated = true; break; }
            }
            if (truncated) break;
            line.append(w);
        }
        if (!truncated && line.length() > 0) {
            if (maxLines > 0 && out.size() >= maxLines) truncated = true;
            else out.add(line.toString());
        }
        if (truncated && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, fit(tr, out.get(last) + " …", maxWidth));
        }
        return out;
    }
}
