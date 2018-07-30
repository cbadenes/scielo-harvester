package io.github.cbadenes.scielo;

import es.upm.oeg.camel.oaipmh.model.*;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class HarvestOAIWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(HarvestOAIWorkflow.class);


    public static void main(String args[]) throws Exception {
        LOG.info("creating camel context ..");
        CamelContext context = new DefaultCamelContext();

        Predicate byStatus = exchange -> {
            final OAIPMHtype body = exchange.getIn().getBody(OAIPMHtype.class);
            List<RecordType> record = body.getListRecords().getRecord();
            if (record.isEmpty()) return false;
            StatusType status = record.get(0).getHeader().getStatus();
            if (status == null) return true;
            return !status.name().equalsIgnoreCase("deleted");
        };

        Predicate byType = exchange -> {
            final OAIPMHtype body = exchange.getIn().getBody(OAIPMHtype.class);
            MetadataType metadata = body.getListRecords().getRecord().get(0).getMetadata();
            List<String> types = metadata.getDc().getTitleOrCreatorOrSubject().stream().filter(el -> el.getName().getLocalPart().equalsIgnoreCase("type")).map(el -> el.getValue().getValue()).collect(Collectors.toList());
            return types.contains("info:eu-repo/semantics/conferenceObject");
        };

        Predicate byLanguage = exchange -> {
            final OAIPMHtype body = exchange.getIn().getBody(OAIPMHtype.class);
            MetadataType metadata = body.getListRecords().getRecord().get(0).getMetadata();
            List<String> types = metadata.getDc().getTitleOrCreatorOrSubject().stream().filter(el -> el.getName().getLocalPart().equalsIgnoreCase("language")).map(el -> el.getValue().getValue()).collect(Collectors.toList());
            return types.contains("spa");
        };



        LOG.info("adding routes to context ..");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("oaipmh://oa.upm.es/cgi/oai2?delay=60000&from=2017-01-01T00:00:00Z")
                        .unmarshal()
                        .jaxb("es.upm.oeg.camel.oaipmh.model")
                        .filter(byStatus)
                        .filter(byType)
                        .filter(byLanguage)
                        .marshal().csv()
                        .log("${body}");
//                        .to("log:io.github.cbadenes.scielo?level=INFO");
            }
        });

        LOG.info("starting workflow ..");
        context.start();


        // wait a bit and then stop
        LOG.info("sleeping for a while ..");
        Thread.sleep(Integer.MAX_VALUE);

        LOG.info("stop application");
        context.stop();
    }

}
