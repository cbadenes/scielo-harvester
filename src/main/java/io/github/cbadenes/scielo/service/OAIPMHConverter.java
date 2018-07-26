package io.github.cbadenes.scielo.service;

import es.upm.oeg.camel.oaipmh.model.ElementType;
import es.upm.oeg.camel.oaipmh.model.MetadataType;
import es.upm.oeg.camel.oaipmh.model.OAIPMHtype;
import org.apache.camel.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Converter
public class OAIPMHConverter  {

    private static final Logger LOG = LoggerFactory.getLogger(OAIPMHConverter.class);

    @Converter
    public static Map oaipmhToMap(OAIPMHtype exchange){

        MetadataType metadata = exchange.getListRecords().getRecord().get(0).getMetadata();

        List<JAXBElement<ElementType>> attributes = metadata.getDc().getTitleOrCreatorOrSubject();


        Map result = new HashMap();
        result.put("identifier", attributes.stream().filter(el -> el.getName().getLocalPart().equalsIgnoreCase("identifier")).map(el -> el.getValue().getValue()).collect(Collectors.joining(",")));
        result.put("title", attributes.stream().filter(el -> el.getName().getLocalPart().equalsIgnoreCase("title")).map(el -> el.getValue().getValue()).collect(Collectors.joining(",")));
        result.put("description", attributes.stream().filter(el -> el.getName().getLocalPart().equalsIgnoreCase("description")).map(el -> el.getValue().getValue()).collect(Collectors.joining(",")));
        return result;

    }

}
