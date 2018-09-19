package io.github.cbadenes.scielo.service;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.BuiltInLanguages;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class LanguageDetector {

    private static final Logger LOG = LoggerFactory.getLogger(LanguageDetector.class);

    private final com.optimaize.langdetect.LanguageDetector languageDetector;

    private final TextObjectFactory textObjectFactory;


    public LanguageDetector()  {
        try{
            //load all languages:
            LanguageProfileReader langReader = new LanguageProfileReader();

            List<LanguageProfile> languageProfiles = new ArrayList<>();

            Iterator it = BuiltInLanguages.getLanguages().iterator();

            List<String> availableLangs = Arrays.asList(new String[]{"en","es","fr","de","pt"});
            while(it.hasNext()) {
                LdLocale locale = (LdLocale)it.next();
                if (availableLangs.contains(locale.getLanguage())) {
                    LOG.info("language added: " + locale);
                    languageProfiles.add(langReader.readBuiltIn(locale));
                }
            }


            //build language detector:
            this.languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                    .withProfiles(languageProfiles)
                    .build();

            //create a text object factory
            this.textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public java.util.Optional<String> getLanguageFrom(String text){
        if (Strings.isNullOrEmpty(text)) return java.util.Optional.empty();
        TextObject textObject = textObjectFactory.forText(text);

        List<DetectedLanguage> probabilities = languageDetector.getProbabilities(textObject);
        if (probabilities.isEmpty()) return java.util.Optional.empty();

        long langs = probabilities.stream().filter(el -> el.getProbability() > 0.2).count();
        if (langs > 1) return java.util.Optional.empty();

        try{
            DetectedLanguage lang = probabilities.stream().sorted((a, b) -> -Double.valueOf(a.getProbability()).compareTo(Double.valueOf(b.getProbability()))).collect(Collectors.toList()).get(0);
            return java.util.Optional.of(lang.getLocale().getLanguage());
        }catch (Exception e){
            LOG.error("Unexpected error getting language", e);
            return java.util.Optional.empty();
        }

//        Optional<LdLocale> lang = languageDetector.detect(textObject);
//        if (!lang.isPresent()){
//            return java.util.Optional.empty();
//        }
//        return java.util.Optional.of(lang.get().getLanguage());
    }

    public boolean isLanguage(String language, String text){
        try{
            java.util.Optional<String> inferredLanguage = getLanguageFrom(text);
            if (!inferredLanguage.isPresent()) return false;
            return inferredLanguage.get().equalsIgnoreCase(language);
        }catch (Exception e){
            LOG.error("Unexpected error inferring language",e);
            return false;
        }
    }

}
