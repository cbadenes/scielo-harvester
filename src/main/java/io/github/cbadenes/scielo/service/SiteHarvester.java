package io.github.cbadenes.scielo.service;

import io.github.cbadenes.scielo.data.Journal;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class SiteHarvester {

    private static final Logger LOG = LoggerFactory.getLogger(SiteHarvester.class);


    public List<Journal> getJournals(String site){
        LOG.info("Getting list of journals from site: '" + site + "' ..");

        try {
            Elements journals = Jsoup.connect("http://" + site + "/scielo.php?script=sci_alphabetic&lng=es&nrm=iso").get().select("li");
            return journals.stream().map(element -> {
                Element a = element.select("a").first();
                final String journalId = StringUtils.substringBetween(a.attr("href"), "pid=", "&");
                final String journalName = a.text();
                return new Journal(journalId, journalName, site);
            }).collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("Unexpected error getting journals from '" + site+"': " + e.getMessage());
            return Collections.emptyList();
        }
    }

}
