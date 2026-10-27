package ru.rpautocomplete;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Словарь для Т9 по словам («греч» → гречка, греческий, ...).
 *
 * Состоит из двух частей:
 *  1) «личные» слова — из ваших шаблонов и из файла config/rpautocomplete/words.txt; показываются первыми;
 *  2) большой частотный словарь (до 100 000 русских слов) из ресурса words_ru.txt, самые частые — первыми.
 *
 * Большой словарь грузится в фоне при старте игры и не тормозит запуск.
 */
public final class WordDictionary {
    private static final WordDictionary INSTANCE = new WordDictionary();

    public static WordDictionary get() { return INSTANCE; }

    private static final Pattern WORD = Pattern.compile("[а-яёА-ЯЁ]{3,}");
    // Корни, которых нет в обычных словах: можно искать в любом месте слова.
    private static final Pattern PROFANITY_ANYWHERE = Pattern.compile(
            "хуй|хуе|хуя|пизд|пезд|мудак|мудил|мудозвон|залуп|пидор|пидар|гондон|сучар|шлюх|дроч|говн|ублюд|бляд|блят");
    // Корни, похожие на куски обычных слов (р-ебён-ок): ищем только в начале слова, с возможной приставкой.
    private static final Pattern PROFANITY_START = Pattern.compile(
            "^(?:на|за|вы|у|по|под|об|от|до|про|при|с|раз|рас|пере)?(?:ебан|ебат|ебал|ебну|ебаш|ебуч|ебис|ебл)");

    // основной словарь
    private volatile String[] words = new String[0];  // порядок = частота (первые — самые частые)
    private volatile String[] norm = new String[0];   // то же, но ё → е
    private volatile int[] sorted = new int[0];       // индексы, отсортированные по norm
    private volatile boolean loaded = false;

    // личные слова
    private String[] personal = new String[0];
    private String[] personalNorm = new String[0];
    private int personalVersion = -1;

    private WordDictionary() {}

    public boolean isLoaded() { return loaded; }

    public int size() { return words.length; }

    // ------------------------------------------------------------------ загрузка

    /** Запускает чтение словаря в фоновом потоке. */
    public void loadAsync() {
        Thread t = new Thread(this::loadFromResources, "rpautocomplete-words");
        t.setDaemon(true);
        t.start();
    }

    private void loadFromResources() {
        try (InputStream in = WordDictionary.class.getResourceAsStream("/assets/rpautocomplete/words_ru.txt")) {
            if (in == null) {
                RpAutocompleteClient.LOGGER.warn("Словарь words_ru.txt не найден в моде — будут подсказываться только слова из ваших шаблонов.");
                return;
            }
            List<String> lines = new ArrayList<>(110_000);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (!line.isEmpty()) lines.add(line);
                }
            }
            setWords(lines);
            RpAutocompleteClient.LOGGER.info("Словарь Т9 загружен: {} слов", words.length);
        } catch (Exception e) {
            RpAutocompleteClient.LOGGER.warn("Не удалось загрузить словарь Т9", e);
        }
    }

    /** Задаёт основной словарь (список слов от самых частых к редким). */
    public void setWords(List<String> list) {
        List<String> clean = new ArrayList<>(list.size());
        Set<String> seen = new HashSet<>();
        for (String w : list) {
            String lw = w.toLowerCase(Locale.ROOT);
            if (lw.isEmpty() || !seen.add(lw)) continue;
            clean.add(lw);
        }
        int n = clean.size();
        String[] ws = clean.toArray(new String[0]);
        String[] nm = new String[n];
        for (int i = 0; i < n; i++) {
            nm[i] = ws[i].indexOf('ё') >= 0 ? ws[i].replace('ё', 'е') : ws[i];
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> nm[a].compareTo(nm[b]));
        int[] so = new int[n];
        for (int i = 0; i < n; i++) so[i] = idx[i];

        words = ws;
        norm = nm;
        sorted = so;
        loaded = true;
    }

    // ------------------------------------------------------------------ личные слова

    /** Слова из шаблонов (чаще встречаются — выше) + слова из words.txt (в порядке файла — самые первые). */
    private synchronized void ensurePersonal() {
        int v = TemplateStore.get().version();
        if (v == personalVersion) return;
        personalVersion = v;

        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try {
            Path f = Io.dir().resolve("words.txt");
            if (Files.exists(f)) {
                for (String line : Io.read(f).split("\\R")) {
                    String w = line.strip().toLowerCase(Locale.ROOT);
                    if (w.length() >= 2 && seen.add(Norm.n(w))) out.add(w);
                }
            }
        } catch (IOException | RuntimeException e) {
            RpAutocompleteClient.LOGGER.warn("Не удалось прочитать words.txt", e);
        }

        Map<String, Integer> counts = new HashMap<>();
        for (Template t : TemplateStore.get().all()) {
            Matcher m = WORD.matcher(t.body());
            while (m.find()) {
                counts.merge(m.group().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        List<String> fromTemplates = new ArrayList<>(counts.keySet());
        fromTemplates.sort((a, b) -> {
            int c = Integer.compare(counts.get(b), counts.get(a));
            if (c != 0) return c;
            c = Integer.compare(a.length(), b.length());
            return c != 0 ? c : a.compareTo(b);
        });
        for (String w : fromTemplates) {
            if (seen.add(Norm.n(w))) out.add(w);
        }

        personal = out.toArray(new String[0]);
        personalNorm = new String[personal.length];
        for (int i = 0; i < personal.length; i++) personalNorm[i] = Norm.n(personal[i]);
    }

    // ------------------------------------------------------------------ поиск

    /**
     * Варианты окончания слова.
     *
     * @param partial начало слова, как напечатано (регистр сохраняется в ответе)
     * @param max     сколько вариантов вернуть
     */
    public List<String> complete(String partial, int max, boolean filterProfanity) {
        List<String> out = new ArrayList<>();
        if (partial == null || partial.isEmpty() || max <= 0) return out;
        String p = Norm.n(partial);
        Set<String> seen = new HashSet<>();
        seen.add(p);

        // 1) личные слова
        ensurePersonal();
        String[] pw = personal;
        String[] pn = personalNorm;
        for (int i = 0; i < pw.length && out.size() < max; i++) {
            if (pn[i].startsWith(p) && seen.add(pn[i])) out.add(matchCase(partial, pw[i]));
        }
        if (out.size() >= max) return out;

        // 2) основной словарь: бинарный поиск диапазона + выбор самых частых
        String[] ws = words;
        String[] nm = norm;
        int[] so = sorted;
        if (so.length == 0) return out;

        int lo = 0;
        int hi = so.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (nm[so[mid]].compareTo(p) < 0) lo = mid + 1;
            else hi = mid;
        }

        // Дублей с личными словами не больше, чем уже найдено личных (out.size()), поэтому берём max с запасом.
        int want = Math.min(max, 40);
        int[] best = new int[want];
        int bestN = 0;
        for (int i = lo; i < so.length; i++) {
            int wi = so[i];
            if (!nm[wi].startsWith(p)) break;
            if (bestN == want && wi >= best[bestN - 1]) continue; // хуже всех текущих
            if (filterProfanity && isProfane(nm[wi])) continue;
            int pos = bestN < want ? bestN : want - 1;
            while (pos > 0 && best[pos - 1] > wi) {
                if (pos < want) best[pos] = best[pos - 1];
                pos--;
            }
            best[pos] = wi;
            if (bestN < want) bestN++;
        }
        for (int i = 0; i < bestN && out.size() < max; i++) {
            String key = nm[best[i]];
            if (seen.add(key)) out.add(matchCase(partial, ws[best[i]]));
        }
        return out;
    }

    /** Слово уже должно быть в нижнем регистре и с «ё» → «е». */
    public static boolean isProfane(String normalizedWord) {
        return PROFANITY_ANYWHERE.matcher(normalizedWord).find() || PROFANITY_START.matcher(normalizedWord).find();
    }

    /** Переносит регистр: «Греч» → «Гречка», «ГРЕЧ» → «ГРЕЧКА». */
    static String matchCase(String typed, String word) {
        if (typed.isEmpty() || word.isEmpty()) return word;
        boolean allUpper = typed.length() >= 2;
        for (int i = 0; i < typed.length(); i++) {
            if (!Character.isUpperCase(typed.charAt(i))) { allUpper = false; break; }
        }
        if (allUpper) return word.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(typed.charAt(0))) {
            return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
        }
        return word;
    }
}
