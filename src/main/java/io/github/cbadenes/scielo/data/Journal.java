package io.github.cbadenes.scielo.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class Journal {

    private static final Logger LOG = LoggerFactory.getLogger(Journal.class);

    private String id;

    private String name;

    private String site;

    public Journal(String id, String name, String site) {
        this.id = id;
        this.name = name;
        this.site = site;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSite() {
        return site;
    }

    @Override
    public String toString() {
        return "Journal{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", site='" + site + '\'' +
                '}';
    }
}
