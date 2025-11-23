package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.dto.LemmaDto;

import java.io.IOException;
import java.util.*;
@Slf4j
public class LemmaFinder {

    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;

    private static LemmaFinder instance;

    private static final Set<String> RUS_PART = Set.of("МЕЖД", "ПРЕДЛ", "СОЮЗ");
    private static final Set<String> ENG_PART = Set.of("CONJ", "PREP", "PRON");

    private LemmaFinder(LuceneMorphology russianMorph, LuceneMorphology englishMorph) {
        this.russianMorph = russianMorph;
        this.englishMorph = englishMorph;
    }

    public static LemmaFinder getInstance() throws IOException {
        if (instance == null) {
            instance = new LemmaFinder(
                    new RussianLuceneMorphology(),
                    new EnglishLuceneMorphology()
            );
        }
        return instance;
    }

    private LuceneMorphology getMorph(String word) {
        if (word.matches(".*[а-яА-ЯёЁ].*")) return russianMorph;
        if (word.matches(".*[a-zA-Z].*")) return englishMorph;
        return null;
    }

    private List<String> splitWords(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.toLowerCase(Locale.ROOT)
                        .split("[^a-zA-Zа-яА-ЯёЁ0-9\\-']+"))
                .filter(w -> w.length() > 1)
                .toList();
    }

    private boolean isParticle(List<String> info) {
        for (String s : info) {
            String u = s.toUpperCase(Locale.ROOT);
            if (RUS_PART.stream().anyMatch(u::contains)) return true;
            if (ENG_PART.stream().anyMatch(u::contains)) return true;
        }
        return false;
    }

    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> map = new HashMap<>();

        for (String word : splitWords(text)) {
            LuceneMorphology morph = getMorph(word);
            if (morph == null) continue;

            try {
                List<String> info = morph.getMorphInfo(word);
                if (isParticle(info)) continue;

                String lemma = morph.getNormalForms(word).get(0);
                map.merge(lemma, 1, Integer::sum);
            } catch (Exception ex) {
                log.debug("⚠ Пропущено слово при подсчете количества лемм '{}': {}", word, ex.getMessage());
            }
        }
        return map;
    }

    public Set<String> getLemmaSet(String text) {
        Set<String> set = new HashSet<>();

        for (String word : splitWords(text)) {
            LuceneMorphology morph = getMorph(word);
            if (morph == null) continue;

            try {
                List<String> info = morph.getMorphInfo(word);
                if (isParticle(info)) continue;
                set.addAll(morph.getNormalForms(word));
            } catch (Exception ex) {
                log.debug("⚠ Пропущено слово при формировании уникального набора лемм '{}': {}", word, ex.getMessage());
            }
        }
        return set;
    }

    public List<LemmaDto> getLemmaDto(String text) {
        List<LemmaDto> list = new ArrayList<>();

        for (String word : splitWords(text)) {
            LuceneMorphology morph = getMorph(word);
            if (morph == null) continue;

            try {
                List<String> info = morph.getMorphInfo(word);
                if (isParticle(info)) continue;

                LemmaDto dto = new LemmaDto();
                dto.setIncomingForm(word);
                dto.setNormalForm(morph.getNormalForms(word).get(0));
                list.add(dto);

            } catch (Exception ignore) { }
        }
        return list;
    }
}
