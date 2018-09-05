package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cbadenes.scielo.data.ArticleInfo;
import io.github.cbadenes.scielo.data.MultiLangArticle;
import io.github.cbadenes.scielo.service.CiteManager;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class IndexWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(IndexWorkflow.class);


    @Test
    public void create() throws IOException {

        int numArticles = 0;

        String filePath = "corpus/articles-full-fixed.json.gz";


        LOG.info("loading articles from file: " + filePath);

        InputStream fileInputStream = filePath.startsWith("http")? new URL(filePath).openStream(): new FileInputStream(filePath);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileInputStream), Charset.forName("UTF-8")));

        try{
            String line = null;

            String serverUrl = "http://localhost:8983/solr/articles";
            SolrClient solrClient = new HttpSolrClient.Builder(serverUrl).build();



            ObjectMapper jsonMapper = new ObjectMapper();

            while( ( line = reader.readLine()) != null){
                try{
                    MultiLangArticle article = jsonMapper.readValue(line,MultiLangArticle.class);

                    ++numArticles;


                    SolrInputDocument document = new SolrInputDocument();
                    document.addField("id",article.getId());
                    document.addField("doi",article.getDoi());
                    document.addField("journal_name",article.getJournal().getName().trim().replace(" ","_"));
                    document.addField("journal_site",article.getJournal().getSite());
                    document.addField("journal_id",article.getJournal().getId());

                    Map<String, ArticleInfo> articlesByLang = article.getArticles();

                    for(String lang: articlesByLang.keySet()){

                        ArticleInfo articleByLang = articlesByLang.get(lang);
                        document.addField("title_"+lang,articleByLang.getTitle());
                        document.addField("pdf_"+lang,articleByLang.getPdfUrl());
                        document.addField("keywords_"+lang,articleByLang.getKeywords().stream().map(kw -> kw.replaceAll(" ","_")).collect(Collectors.toList()));
//                        document.addField("content_"+lang,articleByLang.getContent());

                    }


                    solrClient.add(document);

                    if (numArticles%100 == 0) solrClient.commit();

                    LOG.info("Article '" + article.getId() + "' indexed");

                }catch (Exception e) {
                    LOG.error("Cite info invalid: " + e. getMessage() + " jsonEntry: " + line);
                }
            }
            solrClient.commit();
        }catch (Exception e){
            LOG.error("Error loading cites",e);
        }finally {
            reader.close();

            LOG.info(numArticles + " indexed successfully");

        }

    }

}
