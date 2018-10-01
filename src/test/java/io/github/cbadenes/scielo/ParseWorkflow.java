package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.mashape.unirest.http.Unirest;
import io.github.cbadenes.scielo.data.ArticleInfo;
import io.github.cbadenes.scielo.data.MultiLangArticle;
import io.github.cbadenes.scielo.service.CiteManager;
import io.github.cbadenes.scielo.service.LanguageFilter;
import io.github.cbadenes.scielo.service.TextNormalizer;
import io.github.cbadenes.scielo.utils.ParallelExecutor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class ParseWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ParseWorkflow.class);

    static{
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
    }


    public static String toText(String pdfUrl){
        LOG.info("parsing content from '"+ pdfUrl+"'");

        try (PDDocument document = PDDocument.load(new URL(pdfUrl).openStream())) {

            if (!document.isEncrypted()) {
                PDFTextStripper tStripper = new PDFTextStripper();
                String pdfFileInText = tStripper.getText(document);
                return pdfFileInText;

            }

        } catch (Exception e){
            LOG.error("Unexpected error", e);
        }
        return "";
    }

    public static Map<String,String> retrieveContent(MultiLangArticle article){
        Map<String,String> content = new HashMap<>();
        try{
            String url = "http://articlemeta.scielo.org/api/v1/article/?collection=scl&format=xmlrsps&body=true&code="+article.getId();

            Document articleXML = Jsoup.connect(url).get();

            Elements translationList = articleXML.select("article sub-article[article-type=translation]");

            for (Element translation: translationList){

                String lang     = translation.attr("xml:lang");
                String text     = translation.select("body[specific-use=quirks-mode]").text();

                if (Strings.isNullOrEmpty(text)){
                    text = toText(article.getArticles().get(lang).getPdfUrl());
                }

                if (Strings.isNullOrEmpty(text)){
                    LOG.error("Not available content in '"+ lang+"' for article: " + article.getId());
                    continue;
                }

                String parsedText = TextNormalizer.parse(text,lang);
                content.put(lang, parsedText);

            }

            String mainLanguage = articleXML.select("article").attr("xml:lang");
            String mainText     = articleXML.select("article body[specific-use=quirks-mode]").text();
            String parsedMainText   = TextNormalizer.parse(mainText);
            content.put(mainLanguage, parsedMainText);


        }catch (Exception e){

            for(Map.Entry<String,ArticleInfo> art : article.getArticles().entrySet()){

                String lang = art.getKey();
                String text = toText(art.getValue().getPdfUrl());
                if (Strings.isNullOrEmpty(text)){
                    LOG.error("Not available content in '"+ lang+"' for article: " + article.getId());
                    continue;
                }
                String parsedText = TextNormalizer.parse(text);
                content.put(lang, parsedText);
            }


        }
        return content;

    }

    @Test
    public void create() throws IOException {

        AtomicInteger citedArticles = new AtomicInteger();
        AtomicInteger addedCounter = new AtomicInteger();
        AtomicInteger discardedCounter = new AtomicInteger();
        AtomicInteger errorCounter = new AtomicInteger();

//            String filePath = "https://delicias.dia.fi.upm.es/nextcloud/index.php/s/E9kwqW72GGC2f8S/download";
        String filePath = "corpus/articles.json.gz";


        LOG.info("loading articles from file: " + filePath);

        InputStream fileInputStream = filePath.startsWith("http") ? new URL(filePath).openStream() : new FileInputStream(filePath);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileInputStream), Charset.forName("UTF-8")));

        File outputFile = new File("corpus/articles-full.json.gz");
        if (outputFile.exists()) outputFile.delete();
        else outputFile.getParentFile().mkdirs();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outputFile))));
        try {
            String line = null;

            ObjectMapper jsonMapper = new ObjectMapper();

            ParallelExecutor executor = new ParallelExecutor();
            while ((line = reader.readLine()) != null) {

                final String json = line;
                executor.submit(() -> {
                    try {
                        MultiLangArticle article = jsonMapper.readValue(json, MultiLangArticle.class);

                        Map<String, ArticleInfo> articles = article.getArticles();

                        Map<String, String> articleContent = retrieveContent(article);

                        for (String lang : articles.keySet()) {
                            ArticleInfo articleByLang = articles.get(lang);
                            if (!articleContent.containsKey(lang) || Strings.isNullOrEmpty(articleContent.get(lang))){
                                discardedCounter.incrementAndGet();
                                LOG.warn("Content in '" + lang + "' is empty for article: " + article.getId());
                                return;
                            }
                            String contentByLang = articleContent.get(articleByLang.getLanguage());
                            articleByLang.setContent(contentByLang);
                            articles.put(lang, articleByLang);
                            LOG.info("Characters (" + lang + "): " + contentByLang.length());
                        }

                        article.setArticles(articles);
                        writer.write(jsonMapper.writeValueAsString(article) + "\n");

                        addedCounter.incrementAndGet();

                    } catch (Exception e) {
                        LOG.error("Cite info invalid: " + e.getMessage() + " jsonEntry: " + json);
                        errorCounter.incrementAndGet();
                    }
                });

            }
            executor.awaitTermination(1, TimeUnit.HOURS);
            CiteManager.close();
        } catch (Exception e) {
            LOG.error("Error loading fullcontent", e);
        } finally {

            reader.close();
            writer.close();
            LOG.info(addedCounter.get() + " articles added");
            LOG.info(discardedCounter.get() + " articles discarded");
            LOG.info(errorCounter.get() + " articles were wrong");

        }

    }

    public static void main(String[] args) {
        //String text = toText("http://www.scielo.br/pdf/asoc/v20n3/1809-4422-asoc-20-03-00001.pdf");

        String text = toText("http://www.scielo.br/pdf/ape/v20n2/a01v20n2.pdf");

        LOG.info("->" + text);
    }

}
