package io.github.cbadenes.scielo.service;

import com.google.common.base.Strings;
import io.github.cbadenes.scielo.data.ArticleInfo;
import io.github.cbadenes.scielo.data.MultiLangArticle;
import io.github.cbadenes.scielo.data.Journal;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class ArticleRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleRetriever.class);

    private final Journal journal;
//    private final LanguageDetector languageDetector;

    public ArticleRetriever(Journal journal) {
        this.journal = journal;
//        this.languageDetector = new LanguageDetector();
    }

    public Optional<MultiLangArticle> retrieveById(String paperId) {
        String url = "http://"+ journal.getSite() +"/scieloOrg/php/articleXML.php?pid="+paperId+"&lang=en";
        return retrieveByUrl(url);
    }

    public Optional<MultiLangArticle> retrieveByUrl(String url){
        LOG.debug("Retrieving article ["+ url + "]");
        if (Strings.isNullOrEmpty(url)) return Optional.empty();
        MultiLangArticle article = new MultiLangArticle();
        if (journal != null) article.setJournal(journal);

        try{
            Document articleXML = Jsoup.connect(url).get();
//            Document articleXML = Jsoup.parse(new URL(url).openStream(), "ISO-8859-1", url);

            String id   = articleXML.select("article-id:not([pub-id-type=doi])").text();
            if (Strings.isNullOrEmpty(id)){
                LOG.debug("missing ID from article");
                return Optional.empty();
            }
            article.setId(id);

            Elements titleList = articleXML.select("title-group article-title");

            if (titleList.size() <= 2) {
                LOG.debug("no multilanguage article");
                return Optional.empty();
            }

            // multilanguage
            Map<String,ArticleInfo> articles = new HashMap<>();

            String articleUrl = "http://"+journal.getSite()+"/scielo.php?script=sci_arttext&pid=" + id + "&lng=en";

            Document articleWebXML = Jsoup.connect(articleUrl).get();

            Elements pdfList = articleWebXML.select("meta[name=citation_pdf_url]");


            if (titleList.size() != pdfList.size()){
                LOG.debug("no multilanguage content");
                return Optional.empty();
            }

            for( Element pdfRef : pdfList){
                String lang = pdfRef.attr("language");
                String pdfUrl = pdfRef.attr("content");

                ArticleInfo articleInfo = new ArticleInfo();
                articleInfo.setLanguage(lang);
                articleInfo.setPdfUrl(pdfUrl);
                articles.put(lang,articleInfo);

            }

            String doi  = articleXML.select("article-id[pub-id-type=doi]").text();
            article.setDoi(doi);


            for (Element titleElement: titleList){
                String lang = titleElement.attr("xml:lang");
                ArticleInfo articleInfo = articles.get(lang);
                articleInfo.setTitle(titleElement.text());
                articles.put(lang,articleInfo);
            }

            Elements descriptionList = articleXML.select("abstract");
            for(Element descriptionElement : descriptionList){
                String lang = descriptionElement.attr("xml:lang");
                ArticleInfo articleInfo = articles.get(lang);
                articleInfo.setDescription(TextNormalizer.parse(descriptionElement.text()));
                articles.put(lang,articleInfo);
            }

            for(String lang : articles.keySet()){
                List<String> keywords = articleXML.select("kwd[lng=" + lang + "]").stream().map(el -> TextNormalizer.parse(el.text())).collect(Collectors.toList());
                ArticleInfo articleInfo = articles.get(lang);
                articleInfo.setKeywords(keywords);
                articles.put(lang,articleInfo);
            }


//            String paragraphs = articleXML.select("article > body").stream().map(el -> TextNormalizer.parse(el.text(), language)).filter( text -> !Strings.isNullOrEmpty(text)).collect(Collectors.joining(" "));
//            if (Strings.isNullOrEmpty(paragraphs)) {
//                LOG.debug("article is empty");
//                return article;
//            }
//            article.setText(paragraphs);


//            article.setCitedBy(CiteManager.get(id));
            article.setArticles(articles);
            LOG.info("article retrieved: " + article);
        }catch (Exception e){
            LOG.error("Error retrieving article from '"+ url +"' : " + e.getMessage());
            return Optional.empty();
        }

        return Optional.of(article);
    }

    public List<MultiLangArticle> retrieveAll(){
        List<MultiLangArticle> articles = new ArrayList<>();
        try {
            List<String> volumes = Jsoup.connect("http://" + journal.getSite() + "/scielo.php?script=sci_issues&pid=" + journal.getId() + "&lng=en&nrm=iso").get().select("a[href*=//" + journal.getSite() + "/scielo.php?script=sci_issuetoc]").stream().map(el -> StringUtils.substringBetween(el.attr("href"), "pid=", "&")).collect(Collectors.toList());
            for(String volumeId : volumes) {

                LOG.info("Retrieving articles from volume: " + volumeId + " in journal " + journal + " ..");
                String papersFromVolumeURL = "http://" + journal.getSite() + "/scielo.php?script=sci_issuetoc&pid=" + volumeId + "&lng=en&nrm=iso";

                List<String> paperIds = Jsoup.connect(papersFromVolumeURL).get().select("a[href]").stream().map(el -> el.attributes().get("href")).filter(ref -> ref.contains("sci_arttext&pid=")).map(uri -> StringUtils.substringBetween(uri,"pid=","&lng")).distinct().collect(Collectors.toList());

                List<MultiLangArticle> paperAsArticles = paperIds.stream().filter(id -> !Strings.isNullOrEmpty(id)).map(id -> retrieveById(id)).filter(art -> art.isPresent()).map(art -> art.get()).collect(Collectors.toList());


                articles.addAll(paperAsArticles);

            }
        } catch (Exception e) {
            LOG.error("Error getting articles from journal '"+ journal + "' : " + e.getMessage());
        }
        return articles;
    }


    public static void main(String[] args) {
//        ArticleRetriever articleRetriever = new ArticleRetriever(new Journal("1578-908X","sample","scielo.isciii.es"));
//        ArticleRetriever articleRetriever = new ArticleRetriever(new Journal("1684-1999","sample","www.scielo.org.za"));
        ArticleRetriever articleRetriever = new ArticleRetriever(new Journal("1578-908X","sample","www.scielo.br"));


//        List<Article> result = articleRetriever.retrieveAll();
//        Article result = articleRetriever.retrieveByUrl("http://scielo.isciii.es/scieloOrg/php/articleXML.php?pid=S0213-12852015000600005&lang=en");
//        Article result = articleRetriever.retrieveByUrl("http://scielo.isciii.es/scieloOrg/php/articleXML.php?pid=S1130-01082004001200012&lang=en");

        Optional<MultiLangArticle> result = articleRetriever.retrieveById("S0104-11692016000100328");

        LOG.info("result: " + result);
    }
}
