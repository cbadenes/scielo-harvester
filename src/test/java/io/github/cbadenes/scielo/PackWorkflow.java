package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.AtomicDouble;
import io.github.cbadenes.scielo.data.Article;
import io.github.cbadenes.scielo.service.Translator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class PackWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(PackWorkflow.class);


    static List<String> LANGUAGES = Arrays.asList(new String[]{"en","es","fr","de"});

    static Integer TRAINING_SIZE = 40;

    static Integer TEST_SIZE = 8;


    public static void main(String[] args) throws IOException {


        double minTrainingSize = TRAINING_SIZE / LANGUAGES.size();
        double minTestSize = TEST_SIZE / LANGUAGES.size();

        // initialize reader
        File inputFile = new File("corpus/articles.json.gz");
        if (!inputFile.exists()){
            LOG.error("No input file exists!: " + inputFile.getAbsolutePath());
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFile))));


        // initialize translator
        Translator translator = new Translator();

//         Create writers
        Map<String,BufferedWriter> writers = new ConcurrentHashMap<>();
        for (int i=1;i<=LANGUAGES.size();i++){

            String baseTest = "exp"+i;

            for (int j=1;j<=i;j++){

                List<String> modes = Arrays.asList(new String[]{"train","test"});

                for (String mode: modes){
                    String id = baseTest+"_"+mode+"_"+LANGUAGES.get(j-1);
                    File outputFile = new File("corpus/"+ id + ".json.gz");
                    if (outputFile.exists()) outputFile.delete();
                    else outputFile.getParentFile().mkdirs();
                    try {
                        LOG.info("Writer initialized: " + id);
                        writers.put(id, new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outputFile)))));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


        ObjectMapper jsonMapper = new ObjectMapper();

        Map<String,String> testingArticles = new ConcurrentHashMap<>();
        Map<String,String> testingReferences = new ConcurrentHashMap<>();
        AtomicInteger testCounter = new AtomicInteger();
        String articleJson;

        // Create Test Set
        while ((articleJson = reader.readLine()) != null){

            if ((testCounter.get() >= TEST_SIZE)) break;

            Article article = jsonMapper.readValue(articleJson, Article.class);

            if (!article.getLanguage().equalsIgnoreCase(LANGUAGES.get(0))) continue;

            if (!article.getCitedBy().isEmpty() && article.getCitedBy().size() > 2){
                // candidate for testing
                for (int i=1;i<=LANGUAGES.size();i++){
                    String test = "exp"+i;

                    int maxSize = Double.valueOf(Math.ceil(Double.valueOf(TEST_SIZE) / Double.valueOf(i))).intValue();

                    int targetLangIndex = testCounter.get() / maxSize;
                    String targetLang = LANGUAGES.get(targetLangIndex);

                    String writerId = test+"_test_"+targetLang;


                    if (targetLangIndex > 0){
                        Article translation = translator.translate(article, targetLang);
                        article = translation;
                    }

                    LOG.info("Adding new testing article: " + article.getId() + " to: " + writerId + "[" + testCounter.get() + "]");
                    writers.get(writerId).write(jsonMapper.writeValueAsString(article)+"\n");
                    testingArticles.put(article.getId(),writerId);
                    article.getCitedBy().forEach(rid -> testingReferences.put(rid,writerId));
                }
                testCounter.incrementAndGet();
            }

        }

        reader.close();
        reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFile))));

        // Create Training Set
        AtomicInteger trainingCounter = new AtomicInteger();
        while ((articleJson = reader.readLine()) != null){

            if ((trainingCounter.get() >= TRAINING_SIZE)) break;

            Article article = jsonMapper.readValue(articleJson, Article.class);

            if (!article.getLanguage().equalsIgnoreCase(LANGUAGES.get(0))) continue;

            if (testingArticles.containsKey(article.getId())) continue;

            if (testingReferences.containsKey(article.getId())){
                // Translate?
                String writerId = testingReferences.get(article.getId());
                String newWriterId = StringUtils.substringBeforeLast(writerId, "_") + "_" + LANGUAGES.get(0);
                LOG.info("Adding new testing reference: " + article.getId() + " to: " + newWriterId);
                writers.get(newWriterId).write(jsonMapper.writeValueAsString(article)+"\n");

            }else{
                for (int i=1;i<=LANGUAGES.size();i++){
                    String test = "exp"+i;

                    int maxSize = Double.valueOf(Math.ceil(Double.valueOf(TRAINING_SIZE ) / Double.valueOf(i))).intValue();

                    int targetLangIndex = trainingCounter.get() / maxSize;
                    String targetLang = LANGUAGES.get(targetLangIndex);

                    String writerId = test+"_train_"+targetLang;

                    if (targetLangIndex > 0){
                        Article translation = translator.translate(article, targetLang);
                        article = translation;
                    }

                    LOG.info("Adding new training article: " + article.getId() + " to: " + writerId);
                    writers.get(writerId).write(jsonMapper.writeValueAsString(article)+"\n");

                }
                trainingCounter.incrementAndGet();
            }
        }

        // Close writers
        writers.entrySet().stream().forEach(entry -> {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });



    }



}
