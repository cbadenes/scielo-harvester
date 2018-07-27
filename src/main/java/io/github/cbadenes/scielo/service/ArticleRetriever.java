package io.github.cbadenes.scielo.service;

import com.google.common.base.Strings;
import io.github.cbadenes.scielo.data.Article;
import io.github.cbadenes.scielo.data.Journal;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class ArticleRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleRetriever.class);

    private final Journal journal;
    private final LanguageDetector languageDetector;

    public ArticleRetriever(Journal journal) {
        this.journal = journal;
        this.languageDetector = new LanguageDetector();
    }

    public Article retrieveById(String paperId) {
        String url = "http://"+ journal.getSite() +"/scieloOrg/php/articleXML.php?pid="+paperId+"&lang=en";
        return retrieveByUrl(url);
    }

    public Article retrieveByUrl(String url){
        LOG.info("Retrieving article ["+ url + "]");
        Article article = new Article();
        if (journal != null) article.setJournal(journal);

        try{
            Document articleXML = Jsoup.connect(url).get();
            //Document articleXML = Jsoup.parse(new URL(url).openStream(), "ISO-8859-1", url);

            String id   = articleXML.select("article-id:not([pub-id-type=doi])").text();
            if (Strings.isNullOrEmpty(id)){
                LOG.info("missing ID from article");
                return article;
            }
            article.setId(id);

            String doi  = articleXML.select("article-id[pub-id-type=doi]").text();
            article.setDoi(doi);

            String language = articleXML.select("title-group article-title").first().attr("xml:lang");
            article.setLanguage(language);

            String title        = articleXML.select("title-group article-title[xml:lang=" + language + "]").text().replaceAll("\\<.*?\\>","").replaceAll("\\&.*?\\;","");
            article.setTitle(title);

            String description  = articleXML.select("abstract[xml:lang=" + language + "]").text().replaceAll("\\<.*?\\>","").replaceAll("\\&.*?\\;","");
            article.setDescription(description);

            List<String> labels = articleXML.select("kwd[lng=en]").stream().map(el -> el.text().replaceAll("\\<.*?\\>","").replaceAll("\\&.*?\\;","")).collect(Collectors.toList());
            article.setLabels(labels);

            List<String> keywords = articleXML.select("kwd[lng=" + language + "]").stream().map(el -> el.text().replaceAll("\\<.*?\\>","").replaceAll("\\&.*?\\;","")).collect(Collectors.toList());
            article.setKeywords(keywords);

            String paragraphs = articleXML.select("body").stream().map(el -> el.text().replaceAll("\\<.*?\\>","").replaceAll("\\&.*?\\;","")).filter(text -> languageDetector.isLanguage(language, text)).collect(Collectors.joining(" "));
            if (Strings.isNullOrEmpty(paragraphs)) {
                LOG.info("article is empty");
                return article;
            }
            article.setText(paragraphs);


            article.setCitedBy(CiteManager.get(id));
            LOG.info("article retrieved: " + article);
        }catch (Exception e){
            LOG.error("Error retrieving article from '"+ url +"' : " + e.getMessage());
        }

        return article;
    }

    public List<Article> retrieveAll(){
        List<Article> articles = new ArrayList<>();
        try {
            List<String> volumes = Jsoup.connect("http://" + journal.getSite() + "/scielo.php?script=sci_issues&pid=" + journal.getId() + "&lng=en&nrm=iso").get().select("a[href^=http://" + journal.getSite() + "/scielo.php?script=sci_issuetoc]").stream().map(el -> StringUtils.substringBetween(el.attr("href"), "pid=", "&")).collect(Collectors.toList());
            for(String volumeId : volumes) {

                LOG.info("Retrieving articles from volume: " + volumeId + " in journal " + journal + " ..");
                String papersFromVolumeURL = "http://" + journal.getSite() + "/scielo.php?script=sci_issuetoc&pid=" + volumeId + "&lng=en&nrm=iso";

                List<String> paperIds = Jsoup.connect(papersFromVolumeURL).get().select("a[href]").stream().map(el -> el.attributes().get("href")).filter(ref -> ref.contains("sci_arttext&pid=")).map(uri -> StringUtils.substringBetween(uri,"pid=","&lng")).distinct().collect(Collectors.toList());

                List<Article> paperAsArticles = paperIds.stream().filter(id -> !Strings.isNullOrEmpty(id)).map(id -> retrieveById(id)).filter(art -> !Strings.isNullOrEmpty(art.getText())).collect(Collectors.toList());


                articles.addAll(paperAsArticles);

            }
        } catch (Exception e) {
            LOG.error("Error getting articles from journal '"+ journal + "' : " + e.getMessage());
        }
        return articles;
    }


    public static void main(String[] args) {
//        ArticleRetriever articleRetriever = new ArticleRetriever(new Journal("1578-908X","sample","scielo.isciii.es"));
        ArticleRetriever articleRetriever = new ArticleRetriever(new Journal("1684-1999","sample","www.scielo.org.za"));

        List<Article> result = articleRetriever.retrieveAll();
        //Article result = articleRetriever.retrieveByUrl("http://scielo.isciii.es/scieloOrg/php/articleXML.php?pid=S1578-908X2013000200003&lang=en");
        LOG.info("result: " + result);
    }
}
