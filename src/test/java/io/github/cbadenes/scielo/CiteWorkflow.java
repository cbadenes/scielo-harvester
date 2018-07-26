package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.github.cbadenes.scielo.data.Cite;
import io.github.cbadenes.scielo.service.CiteManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class CiteWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(CiteWorkflow.class);

    private static List<String> retrieveCitesFrom(String paperId){
        LOG.info("getting cites from Restful API for '" + paperId + "'");
        List<String> citeList = new ArrayList<>();
        try {
            HttpResponse<JsonNode> citesResponse = null;
            citesResponse = Unirest.get("http://citedby.scielo.org/api/v1/pid/?q=" + paperId).asJson();
            JSONArray cites = citesResponse.getBody().getObject().getJSONArray("cited_by");
            for(int i=0; i<cites.length(); i++){
                JSONObject cite = cites.getJSONObject(i);
                String citeId = cite.getString("code");
                citeList.add(citeId);
            }
        } catch (UnirestException e) {
            LOG.error("Unexpected error getting cites",e);
        }
        return citeList;
    }


    public static void main(String[] args) {

        try{

            //String filePath = "https://delicias.dia.fi.upm.es/nextcloud/index.php/s/GkZwwcmBcba8HJb/download";
            String filePath = "/Users/cbadenes/Corpus/cites/citedbyapi.json.gz";


            LOG.info("loading cites from file");

            InputStream fileInputStream = filePath.startsWith("http")? new URL(filePath).openStream(): new FileInputStream(filePath);

            BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(fileInputStream), Charset.forName("UTF-8")));

            String line = null;

            ObjectMapper jsonMapper = new ObjectMapper();

            int addedCounter = 0;
            int discardedCounter = 0;
            int errorCounter = 0;
            while( ( line = reader.readLine()) != null){
                try{
                    Cite cite = jsonMapper.readValue(line,Cite.class);
                    CiteManager.add(cite.getArticle().getCode(), cite.getCited_by().stream().map(a -> a.getCode()).collect(Collectors.toList()));
                    addedCounter++;

                }catch (Exception e) {
                    LOG.debug("Cite info invalid: " + e. getMessage() + " jsonEntry: " + line);
                    errorCounter++;
                }
            }
            LOG.info(addedCounter + " cites were added");
            LOG.info(discardedCounter + " cites were empty");
            LOG.info(errorCounter + " cites were wrong");
            reader.close();
            CiteManager.close();
        }catch (Exception e){
            LOG.error("Error loading cites",e);
        }

    }
}
