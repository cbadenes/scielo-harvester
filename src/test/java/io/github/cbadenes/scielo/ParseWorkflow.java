package io.github.cbadenes.scielo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cbadenes.scielo.data.Cite;
import io.github.cbadenes.scielo.service.CiteManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class ParseWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ParseWorkflow.class);

    public static void main(String[] args) {

        try{

            String filePath = "/Users/cbadenes/Downloads/5382757/EN_ES_PT.tmx";

            LOG.info("review cites from file");

            Document articleXML = Jsoup.parse(new File(filePath), "ISO-8859-1");


            Elements tuList = articleXML.select("tu");

            LOG.info("Number of sentences: " + tuList.size());


            for (Element tuElement : tuList){

                LOG.info("" + tuElement);


            }

        }catch (Exception e){
            LOG.error("Error loading cites",e);
        }

    }

}
