package io.github.cbadenes.scielo.service;

import com.google.common.base.Strings;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.process.DocumentPreprocessor;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class TextNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(TextNormalizer.class);


    private static LanguageDetector languageDetector;

    static{
        languageDetector = new LanguageDetector();
    }

    public static String parse(String text, String language){
        if (Strings.isNullOrEmpty(text)) return "";
        return sentences(text).stream().filter(s -> !Strings.isNullOrEmpty(s)).filter(s -> languageDetector.isLanguage(language,s)).map(s -> normalize(s)).collect(Collectors.joining(" "));
    }

    public static List<String> sentences(String text){
        List<String> sentences = new ArrayList<>();

        Reader reader = new StringReader(StringEscapeUtils.unescapeHtml4(text));
        DocumentPreprocessor dp = new DocumentPreprocessor(reader);

        for (List<HasWord> sentence : dp) {
            // SentenceUtils not Sentence
            String sentenceString = SentenceUtils.listToString(sentence);
            sentences.add(sentenceString);
        }

        return sentences;
    }

    public static String parse(String text){
        if (Strings.isNullOrEmpty(text)) return "";
        return sentences(text).stream().map(s -> normalize(s)).collect(Collectors.joining(" "));
    }

    public static String normalize(String sentence){
        if (Strings.isNullOrEmpty(sentence)) return "";
        return removeParenthesis(sentence.trim().replaceAll("\\<.*?\\>","").replaceAll("\\&.*?\\;","").replaceAll("( )+", " ").replaceAll("-LRB-", "[").replaceAll("-RRB-", "]"),"[]");
    }

    public static String removeParenthesis(String input_string, String parenthesis_symbol){
        // removing parenthesis and everything inside them, works for (),[] and {}
        if(parenthesis_symbol.contains("[]")){
            return input_string.replaceAll("\\s*\\[[^\\]]*\\]\\s*", " ");
        }else if(parenthesis_symbol.contains("{}")){
            return input_string.replaceAll("\\s*\\{[^\\}]*\\}\\s*", " ");
        }else{
            return input_string.replaceAll("\\s*\\([^\\)]*\\)\\s*", " ");
        }
    }



    public static void main(String[] args) {
        String text = "La atribuci&oacute;n de la guarda y custodia de menores est&aacute; determinada en la jurisprudencia espa&ntilde;ola por la supremac&iacute;a del Inter&eacute;s Superior del Menor (en adelante ISM), objetivo fundamental del Derecho de Familia, en correspondencia con el conjunto de instituciones y &aacute;mbitos en que su persona o patrimonio pueden ser afectados por medidas que otros tomen en su nombre (de Torres, 2011).";

        LOG.info(StringEscapeUtils.unescapeJava(text));
        LOG.info(StringEscapeUtils.unescapeCsv(text));
        LOG.info(StringEscapeUtils.unescapeXml(text));
        LOG.info(StringEscapeUtils.unescapeHtml3(text));
        LOG.info(StringEscapeUtils.unescapeHtml4(text));
    }

}
