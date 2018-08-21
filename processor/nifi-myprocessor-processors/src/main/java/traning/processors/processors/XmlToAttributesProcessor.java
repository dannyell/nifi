/*
 * All rights reserved. Copyright (c) Ixxus Ltd 2018
 */

package traning.processors.processors;

import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.entopix.maui.filters.MauiFilter;
import com.entopix.maui.main.MauiModelBuilder;
import com.entopix.maui.main.MauiTopicExtractor;
import com.entopix.maui.util.MauiDocument;
import com.entopix.maui.util.MauiTopics;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.StringUtils;

@Tags({ "Keyword removal" })
@CapabilityDescription("extract keywords from xml")
@SeeAlso({})
@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
@WritesAttributes({ @WritesAttribute(attribute = "", description = "") })
public class XmlToAttributesProcessor extends AbstractProcessor {

    public static final PropertyDescriptor MY_PROPERTY = new PropertyDescriptor
            .Builder().name("MY_PROPERTY")
            .displayName("My property")
            .description("Example Property")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("SUCCESS")
            .description("Success relationship")
            .build();
    private static final String ARTICLE_TITLE = "article.title";
    private static final String ARTICLE_DOI = "article.doi";
    private static final String ARTICLE_ABSTRACT  = "article.abstract";

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(MY_PROPERTY);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(SUCCESS);
        //        relationships.add(FAILED);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @Override
    public void onTrigger(final ProcessContext processContext, final ProcessSession session) throws ProcessException {
        FlowFile original = session.get();
        if (original == null) {
            return;
        }
        FlowFile clone = session.clone(original);
        session.read(original, inputStream -> {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = null;
            try {
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(inputStream);

                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(doc), new StreamResult(writer));

                Node article = doc.getElementsByTagName("article").item(0);
                NodeList childNodes = article.getChildNodes();
                for (int i = 0; i< childNodes.getLength(); i++) {
                    Node item = childNodes.item(i);
                    if ("doi".equals(item.getNodeName())) {
                        addAttribute(session, clone, ARTICLE_DOI, item);
                    } else if ("title".equals(item.getNodeName())){
                        addAttribute(session, clone, ARTICLE_TITLE, item);
                    } else if ("abstract".equals(item.getNodeName())){
                        addAttribute(session, clone, ARTICLE_ABSTRACT, item);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        // need to remove otherwise error: "transfer relationship not specified"
        session.remove(original);
        session.transfer(clone, SUCCESS);
    }

    private void addAttribute(final ProcessSession session, final FlowFile flowFile, final String attrName,final Node item) {
        String value = item.getTextContent();
        session.putAttribute(flowFile, attrName, value);
        getLogger().info("Value: " + value);
    }
}
