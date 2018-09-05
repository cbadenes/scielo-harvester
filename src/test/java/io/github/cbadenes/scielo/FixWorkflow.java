package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cbadenes.scielo.data.ArticleInfo;
import io.github.cbadenes.scielo.data.MultiLangArticle;
import io.github.cbadenes.scielo.service.CiteManager;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class FixWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(FixWorkflow.class);


    @Test
    public void create() throws IOException {


        String filePath = "corpus/articles-full.json.gz";


        LOG.info("loading articles from file: " + filePath);

        InputStream fileInputStream = filePath.startsWith("http")? new URL(filePath).openStream(): new FileInputStream(filePath);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileInputStream), Charset.forName("UTF-8")));


        File outputFile = new File("corpus/articles-full-fixed.json.gz");
        if (outputFile.exists()) outputFile.delete();
        else outputFile.getParentFile().mkdirs();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outputFile))));


        try{
            String line = null;

            ObjectMapper jsonMapper = new ObjectMapper();

            while( ( line = reader.readLine()) != null){
                try{
                    MultiLangArticle article = jsonMapper.readValue(line,MultiLangArticle.class);


                    Map<String, ArticleInfo> articlesByLang = article.getArticles();

                    for (String lang: articlesByLang.keySet()){
                        ArticleInfo articleByLang = articlesByLang.get(lang);

                        List<String> keywords = articleByLang.getKeywords();

                        List<String> fixedKeywords = new ArrayList<>();

                        for(String kw : keywords){


                            if (kw.contains(",")){
                                String[] kws = kw.split(",");
                                fixedKeywords.addAll(Arrays.asList(kws).stream().map(i -> i.toLowerCase().replaceAll("\\.","").trim()).collect(Collectors.toList()));
                            }else{
                                fixedKeywords.add(kw.toLowerCase().replaceAll("\\.","").trim());
                            }

                        }

                        LOG.info("Fixing article: " + article.getId());
                        articleByLang.setKeywords(fixedKeywords);
                        articlesByLang.put(lang, articleByLang);

                    }

                    article.setArticles(articlesByLang);
                    writer.write(jsonMapper.writeValueAsString(article)+"\n");


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
            writer.close();
        }

    }

}
