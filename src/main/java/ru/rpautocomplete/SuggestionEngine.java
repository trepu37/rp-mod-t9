package ru.rpautocomplete;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Подбор подсказок по тексту ПЕРЕД курсором.
 *
 * Режимы:
 *  1) Явный поиск: «/rp слово» — ищет по сокращениям, названиям, тексту и категориям.
 *  2) Внутри RP-команды («/me дост», «/todo здр») — сокращения и начало фразы.
 *  3) Обычный текст без «/» — только точное/начальное совпадение сокращения и начало фразы.
 *
 * Любые другие команды (/gamemode, /tp, ...) игнорируются — мод в них не вмешивается.
 */
public final class SuggestionEngine {
    private SuggestionEngine() {}

    public record Suggestion(Template template, String insertion, int score) {}

    private static final int MAX_RESULTS = 40;

    public static List<Suggestion> suggest(String before, List<Template> all, Settings s) {
        if (before == null || before.isEmpty() || all.isEmpty()) return List.of();

        String trigger = s.trigger == null ? "" : s.trigger.strip();
        String lowerBefore = before.toLowerCase(Locale.ROOT);

        boolean explicit = false;
        String prefix = "";
        String cmd = "";
        String query;

        if (!trigger.isEmpty() && lowerBefore.startsWith(trigger.toLowerCase(Locale.ROOT) + " ")) {
            explicit = true;
            query = before.substring(trigger.length() + 1).stripLeading();
        } else if (before.startsWith("/")) {
            int sp = before.indexOf(' ');
            if (sp < 0) return List.of(); // пользователь ещё печатает имя команды
            cmd = before.substring(1, sp).toLowerCase(Locale.ROOT);
            if (!isKnownCommand(cmd, all, s)) return List.of();
            prefix = before.substring(0, sp + 1);
            query = before.substring(sp + 1).stripLeading();
            if (query.isEmpty()) return List.of();
        } else {
            query = before;
        }

        String q = Norm.n(query);
        String qTrim = q.stripTrailing();
        if (!explicit && qTrim.isEmpty()) return List.of();

        String normBefore = Norm.n(before);
        List<Suggestion> out = new ArrayList<>();
        for (Template t : all) {
            String tc = t.command();
            int score;
            if (explicit) {
                score = scoreExplicit(t, q, qTrim);
            } else {
                if (!cmd.isEmpty() && !tc.isEmpty() && !tc.equals(cmd)) continue; // другая команда
                score = scoreImplicit(t, q, qTrim, !cmd.isEmpty(), s.minChars);
            }
            if (score <= 0) continue;

            String insertion = t.text.startsWith("/") ? t.text : prefix + t.text;
            if (Norm.n(insertion).equals(normBefore)) continue; // уже напечатано целиком

            score += (t.favorite ? 40 : 0) + Math.min(t.uses, 30);
            out.add(new Suggestion(t, insertion, score));
        }

        out.sort((x, y) -> {
            if (x.score() != y.score()) return Integer.compare(y.score(), x.score());
            if (x.template().lastUsed != y.template().lastUsed) {
                return Long.compare(y.template().lastUsed, x.template().lastUsed);
            }
            return x.template().title.compareToIgnoreCase(y.template().title);
        });
        if (out.size() > MAX_RESULTS) return new ArrayList<>(out.subList(0, MAX_RESULTS));
        return out;
    }

    /**
     * Можно ли подсказывать слова в этом месте. В обычном тексте и внутри RP-команд — да;
     * в остальных командах Minecraft (/gamemode, /tp, /msg ...) и в имени команды — нет.
     */
    public static boolean wordsAllowed(String before, List<Template> all, Settings s) {
        if (before == null || before.isEmpty()) return false;
        if (!before.startsWith("/")) return true;
        int sp = before.indexOf(' ');
        if (sp < 0) return false;
        String cmd = before.substring(1, sp).toLowerCase(Locale.ROOT);
        return isKnownCommand(cmd, all, s);
    }

    private static boolean isKnownCommand(String cmd, List<Template> all, Settings s) {
        if (s.rpCommands != null && s.rpCommands.contains(cmd)) return true;
        Set<String> fromTemplates = new HashSet<>();
        for (Template t : all) {
            String c = t.command();
            if (!c.isEmpty()) fromTemplates.add(c);
        }
        return fromTemplates.contains(cmd);
    }

    private static int scoreImplicit(Template t, String q, String qTrim, boolean cmdMode, int min) {
        int len = qTrim.length();
        // Внутри RP-команды порог ниже; в обычном тексте строже, чтобы не мешать нормальному вводу.
        int minExact = cmdMode ? min : min + 1;
        int minBody = cmdMode ? min + 1 : min + 2;
        int score = 0;
        for (String a : t.abbreviations) {
            String na = Norm.n(a);
            if (na.isEmpty()) continue;
            if (na.equals(qTrim)) {
                if (len >= minExact) score = Math.max(score, 1000);
            } else if (cmdMode && len >= min && na.startsWith(qTrim)) {
                score = Math.max(score, 800); // начало сокращения — только внутри /me, /do, /todo...
            }
        }
        if (len >= minBody) {
            String body = Norm.n(t.body());
            if (body.length() > q.length() && body.startsWith(q)) score = Math.max(score, 600);
        }
        return score;
    }

    private static int scoreExplicit(Template t, String q, String qTrim) {
        if (qTrim.isEmpty()) return 1; // «/rp » без слова: избранное и недавние
        int score = 0;
        for (String a : t.abbreviations) {
            String na = Norm.n(a);
            if (na.isEmpty()) continue;
            if (na.equals(qTrim)) score = Math.max(score, 1000);
            else if (na.startsWith(qTrim)) score = Math.max(score, 800);
            else if (qTrim.length() >= 2 && na.contains(qTrim)) score = Math.max(score, 500);
        }
        String title = Norm.n(t.title);
        if (title.startsWith(qTrim)) score = Math.max(score, 700);
        else if ((" " + title).contains(" " + qTrim)) score = Math.max(score, 600);
        else if (title.contains(qTrim)) score = Math.max(score, 450);

        String body = Norm.n(t.body());
        if (body.startsWith(q)) score = Math.max(score, 650);
        String fullText = Norm.n(t.text);
        if (fullText.contains(qTrim)) score = Math.max(score, 250);

        if (Norm.n(t.category).startsWith(qTrim)) score = Math.max(score, 300);

        if (score == 0 && qTrim.contains(" ")) {
            String hay = title + " " + String.join(" ", t.abbreviations) + " " + Norm.n(t.category) + " " + fullText;
            boolean all = true;
            for (String token : qTrim.split("\\s+")) {
                if (!token.isEmpty() && !hay.contains(token)) { all = false; break; }
            }
            if (all) score = 280;
        }
        return score;
    }
}
