package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.github.cbadenes.scielo.data.ArticleInfo;
import io.github.cbadenes.scielo.data.Cite;
import io.github.cbadenes.scielo.data.MultiLangArticle;
import io.github.cbadenes.scielo.service.CiteManager;
import io.github.cbadenes.scielo.service.LanguageFilter;
import io.github.cbadenes.scielo.utils.ParallelExecutor;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class ParseWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ParseWorkflow.class);


    public static String toText(String pdfUrl){
        LOG.info("parsing content from '"+ pdfUrl+"'");
        try (PDDocument document = PDDocument.load(new URL(pdfUrl).openStream())) {

            document.getClass();

            if (!document.isEncrypted()) {

                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);

                PDFTextStripper tStripper = new PDFTextStripper();

                String pdfFileInText = tStripper.getText(document);
                return pdfFileInText;

            }

        } catch (InvalidPasswordException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Test
    public void create() throws IOException {

            AtomicInteger citedArticles = new AtomicInteger();
        AtomicInteger addedCounter = new AtomicInteger();
        AtomicInteger discardedCounter = new AtomicInteger();
        AtomicInteger errorCounter =  new AtomicInteger();

//            String filePath = "https://delicias.dia.fi.upm.es/nextcloud/index.php/s/E9kwqW72GGC2f8S/download";
            String filePath = "corpus/articles.json.gz";


            LOG.info("loading articles from file: " + filePath);

            InputStream fileInputStream = filePath.startsWith("http")? new URL(filePath).openStream(): new FileInputStream(filePath);

            BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileInputStream), Charset.forName("UTF-8")));

            File outputFile = new File("corpus/articles-full.json.gz");
            if (outputFile.exists()) outputFile.delete();
            else outputFile.getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outputFile))));
        LanguageFilter languageFilter = new LanguageFilter();
        try{
            String line = null;

            ObjectMapper jsonMapper = new ObjectMapper();

            ParallelExecutor executor = new ParallelExecutor();
            while( ( line = reader.readLine()) != null){

                final String json = line;
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            MultiLangArticle article = jsonMapper.readValue(json,MultiLangArticle.class);

                            Map<String, ArticleInfo> articles = article.getArticles();

                            for (String lang : articles.keySet()){
                                ArticleInfo articleByLang = articles.get(lang);
                                String text = toText(articleByLang.getPdfUrl());
                                if (Strings.isNullOrEmpty(text)){
                                    discardedCounter.incrementAndGet();
                                    continue;
                                }

                                String validText = languageFilter.retrieve(lang, text);

                                if (Strings.isNullOrEmpty(validText)){
                                    discardedCounter.incrementAndGet();
                                    continue;
                                }

                                articleByLang.setContent(validText.replace("\n"," ").replace("\r"," ").replace("- ",""));
                                articles.put(lang,articleByLang);
                                LOG.info("Characters ("+lang+"): " + validText.length());
                            }

                            article.setArticles(articles);
                            writer.write(jsonMapper.writeValueAsString(article)+"\n");

                            addedCounter.incrementAndGet();

                        }catch (Exception e) {
                            LOG.error("Cite info invalid: " + e. getMessage() + " jsonEntry: " + json);
                            errorCounter.incrementAndGet();
                        }
                    }
                });

            }
            executor.awaitTermination(1, TimeUnit.HOURS);
            CiteManager.close();
        }catch (Exception e){
            LOG.error("Error loading fullcontent",e);
        }finally {

            reader.close();
            writer.close();
            LOG.info(errorCounter.get() + " articles cited");
            LOG.info(addedCounter.get() + " articles with content");
            LOG.info(discardedCounter.get() + " articles without content");
            LOG.info(errorCounter.get() + " articles were wrong");

        }

    }

}
