package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.github.cbadenes.scielo.data.Article;
import io.github.cbadenes.scielo.data.Journal;
import io.github.cbadenes.scielo.service.ArticleRetriever;
import io.github.cbadenes.scielo.service.CiteManager;
import io.github.cbadenes.scielo.service.SiteHarvester;
import io.github.cbadenes.scielo.utils.ParallelExecutor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class HarvestHTTPWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(HarvestHTTPWorkflow.class);


    private static List<Article> recursiveDownloadByCitesOf(Article article, Map<String,Integer> registry, ArticleRetriever retriever) {
        if (Strings.isNullOrEmpty(article.getText())) return Collections.emptyList();
        List<Article> articles = new ArrayList<>();
        articles.add(article);
        if (registry.containsKey(article.getId())) return articles;

        registry.put(article.getId(), 1);

        articles.addAll(article.getCitedBy().stream().filter(id -> !Strings.isNullOrEmpty(id)).filter(id -> !registry.containsKey(id)).flatMap(id -> recursiveDownloadByCitesOf(retriever.retrieveById(id), registry, retriever).stream()).collect(Collectors.toList()));

        return articles;
    }

    @Test
    public void create() throws UnirestException, ParserConfigurationException, IOException, SAXException, XPathExpressionException {


        CiteManager.list(1);
        SiteHarvester siteHarvester = new SiteHarvester();
        ParallelExecutor executor = new ParallelExecutor();

        BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/journals.csv"));

        File outputFile = new File("corpus/articles.json.gz");
        if (outputFile.exists()) outputFile.delete();
        else outputFile.getParentFile().mkdirs();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream("corpus/articles.json.gz"))));

        AtomicInteger addedArticles = new AtomicInteger();
        AtomicInteger discardedArticles = new AtomicInteger();

        String siteInfo;

        Map<String,Integer> articlesRegistry = new ConcurrentHashMap();
        while( (siteInfo = reader.readLine()) != null){

            if (siteInfo.startsWith("#")) continue;

            String baseUrl = siteInfo;


            List<Journal> journals = siteHarvester.getJournals(baseUrl);

            for (Journal journal : journals){

                executor.submit(() -> {
                    try{
                        ArticleRetriever articleRetriever = new ArticleRetriever(journal);

                        ObjectMapper jsonMapper = new ObjectMapper();
                        List<Article> articles = articleRetriever.retrieveAll().stream().flatMap(article -> recursiveDownloadByCitesOf(article, articlesRegistry, articleRetriever).stream()).collect(Collectors.toList());

                        for(Article article: articles){

                            if (article.getKeywords().isEmpty() || (article.getKeywords().size() != article.getLabels().size())){
                                discardedArticles.incrementAndGet();
                                continue;
                            }

                            writer.write(jsonMapper.writeValueAsString(article)+"\n");
                            LOG.info("Added Article : " + article);
                            addedArticles.incrementAndGet();
                        }

                    }catch (Exception e){
                         LOG.error("Error getting articles from  '" + journal + "'",e);
                    }
                });
            }

            break;

        }
        executor.awaitTermination(1, TimeUnit.HOURS);

        LOG.info("Total discarded articles: " + discardedArticles.get());
        LOG.info("Total added articles: " + addedArticles.get());
        reader.close();
        writer.close();
    }



}
