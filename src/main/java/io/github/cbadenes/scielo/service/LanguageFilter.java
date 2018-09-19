package io.github.cbadenes.scielo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class LanguageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(LanguageFilter.class);

    private final LanguageDetector langDetector;

    public LanguageFilter() {
        langDetector = new LanguageDetector();
    }

    public String retrieve(String lang, String text){

        // Split in sentences

        List<String> sentences = TextNormalizer.sentences(text);

        StringBuffer filteredText = new StringBuffer();

        for(String sentence: sentences){

            if (langDetector.isLanguage(lang, sentence)) filteredText.append(sentence).append(". ");

        }

        return filteredText.toString();
    }

}
