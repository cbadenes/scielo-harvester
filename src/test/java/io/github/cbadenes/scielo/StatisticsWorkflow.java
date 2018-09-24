package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.github.cbadenes.scielo.data.ArticleInfo;
import io.github.cbadenes.scielo.data.MultiLangArticle;
import io.github.cbadenes.scielo.service.CiteManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class StatisticsWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsWorkflow.class);

    private boolean validate(MultiLangArticle article){

        if (Strings.isNullOrEmpty(article.getId())) {
            LOG.warn("Empty ID");
            return false;
        }

        if (article.getArticles().size() < 3) {
            LOG.warn("Not multi-language articles: " + article);
            return false;
        }

        for(Map.Entry<String, ArticleInfo> art : article.getArticles().entrySet()){

            ArticleInfo info = art.getValue();

//            if (info.getKeywords().isEmpty()){
//                LOG.warn("No keywords available in '" + art.getKey() + "' for article: " + article.getId());
//
//                if (art.getKey().equalsIgnoreCase("en")) return false;
//            }

            if (Strings.isNullOrEmpty(info.getContent())){
                LOG.warn("No content available in '" + art.getKey() + "' for article: " + article.getId());
                return false;
            }


        }

        return true;

    }


    @Test
    public void create() throws IOException {

        int numArticles = 0;

        String filePath = "corpus/articles-full.json.gz";


        LOG.info("loading articles from file: " + filePath);

        InputStream fileInputStream = filePath.startsWith("http")? new URL(filePath).openStream(): new FileInputStream(filePath);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileInputStream), Charset.forName("UTF-8")));

        Map<String,Integer> docsByTags      = new HashMap<>();
        Map<String,Integer> docsByJournal   = new HashMap<>();

        AtomicInteger validDocs     = new AtomicInteger();
        AtomicInteger invalidDocs   = new AtomicInteger();

        try{
            String line = null;

            ObjectMapper jsonMapper = new ObjectMapper();

            while( ( line = reader.readLine()) != null){
                try{
                    MultiLangArticle article = jsonMapper.readValue(line,MultiLangArticle.class);

                    if (validate(article)) validDocs.incrementAndGet();
                    else invalidDocs.incrementAndGet();

                    ++numArticles;

                    String journalId = article.getJournal().getName()+"["+article.getJournal().getId()+"]";

                    Integer numDocsByJournal = 0;

                    if (docsByJournal.containsKey(journalId)){
                        numDocsByJournal = docsByJournal.get(journalId);
                    }

                    docsByJournal.put(journalId, ++numDocsByJournal);


                    List<String> keywords = article.getArticles().get("en").getKeywords();

                    for(String kw : keywords){

                        String tag = kw.toLowerCase();

                        Integer numDocsbyTag = 0;

                        if (docsByTags.containsKey(tag)){
                            numDocsbyTag = docsByTags.get(tag);
                        }

                        docsByTags.put(tag, ++numDocsbyTag);

                    }

                    LOG.info("Article '" + article.getId() + "' analyzed");

                }catch (Exception e) {
                    LOG.error("Cite info invalid: " + e. getMessage() + " jsonEntry: " + line);
                }
            }
            CiteManager.close();
        }catch (Exception e){
            LOG.error("Error loading cites",e);
        }finally {

            reader.close();

            LOG.info("===================");

            LOG.info("Docs by Journal");
            docsByJournal.entrySet().stream().sorted((a,b) -> -a.getValue().compareTo(b.getValue())).limit(20).forEach( entry -> LOG.info("Journal '"+entry.getKey()+"' -> " + entry.getValue()));

            LOG.info("Docs by Tag");
            docsByTags.entrySet().stream().sorted((a,b) -> -a.getValue().compareTo(b.getValue())).limit(20).forEach( entry -> LOG.info("Tag '"+entry.getKey()+"' -> " + entry.getValue()));

            LOG.info("Total Articles: " + numArticles);

            LOG.info("Valid Articles: " + validDocs.get());
            LOG.info("Invalid Articles: " + invalidDocs.get());

        }

    }

}
