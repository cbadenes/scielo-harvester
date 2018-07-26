package io.github.cbadenes.scielo.service;

import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class CiteManager {

    private static final Logger LOG = LoggerFactory.getLogger(CiteManager.class);


    private static File index = new File("src/main/resources/cites.db");
    private static ConcurrentMap<String,String> map;
    private static DB db;

    static {

        try{
            db = DBMaker.fileDB(index.getAbsolutePath()).checksumHeaderBypass().closeOnJvmShutdown().make();
            map = db.hashMap("map", Serializer.STRING, Serializer.STRING).createOrOpen();
        }catch (Exception e){
            LOG.error("Error initializing db",e);
        }

    }

    public static void close(){
        db.commit();
        db.close();
    }

    public static void add(String refId, List<String> cites){
        map.put(refId, cites.stream().collect(Collectors.joining(",")));
    }


    public static List<String> get(String paperId){
        try{
            if (!map.containsKey(paperId)) {
                add(paperId, retrieveFromAPI(paperId));
            }
        }catch (Exception e){
            LOG.error("Error reading from db: " + e.getMessage());
            return Collections.emptyList();
        }
        String cites = map.getOrDefault(paperId, "");

        if (Strings.isNullOrEmpty(cites)) return Collections.emptyList();

        return Arrays.asList(cites.split(","));
    }

    private static List<String> retrieveFromAPI(String paperId){
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

    public static List<String> list(int num){
        LOG.info("Num records: " + map.size());

        return map.entrySet().stream().limit(num).map(entry -> entry.toString()).collect(Collectors.toList());

    }

    public static void main(String[] args) {

//        add("1", Arrays.asList(new String[]{"1","2","3"}));
//        add("2", Arrays.asList(new String[]{"4","2","7"}));
//        add("3", Arrays.asList(new String[]{"5","3","1"}));
//        add("4", Arrays.asList(new String[]{"6","5","3"}));
//        add("5", Arrays.asList(new String[]{"2","3","8"}));
//        add("6", Arrays.asList(new String[]{"5","6","3"}));
//
//        LOG.info("Ref: " + get("5"));

        LOG.info("Cites: " + CiteManager.list(10));

    }



}
