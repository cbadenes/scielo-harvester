package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.github.cbadenes.scielo.data.MultiLangArticle;
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
import java.util.*;
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


    private static final List<String> languages = Arrays.asList(new String[]{"en","es","pt"});

    private static List<MultiLangArticle> recursiveDownloadByCitesOf(MultiLangArticle article, Map<String,Integer> registry, ArticleRetriever retriever) {
        if (article.isEmpty() || registry.containsKey(article.getId())) return Collections.emptyList();
        registry.put(article.getId(), 1);

        List<MultiLangArticle> articles = new ArrayList<>();
        articles.add(article);

        List<MultiLangArticle> citedArticles = article.getCitedBy().stream().filter(id -> CiteManager.getUrl(id).isPresent()).map(id -> CiteManager.getUrl(id).get()).map(url -> retriever.retrieveByUrl(url)).filter(art -> !art.isPresent()).map(art -> art.get()).collect(Collectors.toList());

        // add cites to articles
        articles.addAll(citedArticles);

        // update with only valid cites
        article.setCitedBy(citedArticles.stream().map(art -> art.getId()).collect(Collectors.toList()));

        // recursive action
        articles.addAll(citedArticles.stream().flatMap(art -> recursiveDownloadByCitesOf(art, registry, retriever).stream()).collect(Collectors.toList()));

        return articles;
    }

    @Test
    public void create() throws UnirestException, ParserConfigurationException, IOException, SAXException, XPathExpressionException {


        CiteManager.list(1);
        SiteHarvester siteHarvester = new SiteHarvester();
        ParallelExecutor executor = new ParallelExecutor();

        BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/sites.csv"));

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
                        List<MultiLangArticle> articles = articleRetriever.retrieveAll().stream().flatMap(article -> recursiveDownloadByCitesOf(article, articlesRegistry, articleRetriever).stream()).collect(Collectors.toList());

                        for(MultiLangArticle article: articles){

                            List<String> articleLangs = article.getArticles().keySet().stream().map(lang -> lang.toLowerCase()).collect(Collectors.toList());

                            Boolean languagesRequired = languages.stream().map(l -> articleLangs.contains(l)).reduce((a, b) -> a && b).get();

                            if (!languagesRequired){
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
