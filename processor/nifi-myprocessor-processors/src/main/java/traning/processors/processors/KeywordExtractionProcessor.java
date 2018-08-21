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
public class KeywordExtractionProcessor extends AbstractProcessor {

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
    private static final String ARTICLE_KEYWORDS = "article.keywords";
    private MauiTopicExtractor topicExtractor;
    private MauiModelBuilder modelBuilder;
    //
    //    public static final Relationship FAILED = new Relationship.Builder()
    //            .name("FAILED")
    //            .description("FAILED relationship")
    //            .build();
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

    @OnScheduled
    public void onScheduled(ProcessContext context) throws ProcessException {
        try {
            testAutomaticTagging();
        } catch (Exception e) {
            throw new RuntimeException("failed to init MAUI");
        }
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

        final StringBuffer keywords = new StringBuffer();
        FlowFile enriched = session.write(original, (inputStream, outputStream) -> {
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
                if (article.hasChildNodes()) {
                    NodeList childNodes = article.getChildNodes();

                    if (!enrichContent(outputStream, doc, transformer, writer, article, childNodes, session, original, keywords)){
                        IOUtils.copy(inputStream, outputStream);
                    }

                } else {
                    IOUtils.copy(inputStream, outputStream);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        session.putAttribute(enriched, ARTICLE_KEYWORDS, keywords.toString());
        session.transfer(enriched, SUCCESS);
    }

    private boolean enrichContent(final OutputStream outputStream, final Document doc, final Transformer transformer, final StringWriter writer,
            final Node article, final NodeList childNodes, final ProcessSession session, final FlowFile original, final StringBuffer keywords) throws MauiFilter.MauiFilterException, TransformerException {
        for (int i = 0; i< childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if ("content".equals(item.getNodeName())) {
                List<MauiTopics> mauiTopics = topicExtractor.extractTopics(
                        Arrays.asList(new MauiDocument("test.txt", null, item.getTextContent(), StringUtils.EMPTY)));
                keywords.append(StringUtils.join(mauiTopics.get(0).getTopics().stream().map(a -> a.getTitle()).collect(Collectors.toList()), ","));
                getLogger().info("KEYWORDS: " + keywords);
                Element keywordsElement = doc.createElement("keywords");
                keywordsElement.setTextContent(keywords.toString());
                article.appendChild(keywordsElement);

                transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
                return true;
            }
        }
        return false;
    }

    public void testAutomaticTagging() throws Exception {
        topicExtractor = new MauiTopicExtractor();
        modelBuilder = new MauiModelBuilder();
        setFeatures();

        // Directories with train & test data
        String trainDir = "data/automatic_tagging/train";
        String testDir = "data/automatic_tagging/test";

        // name of the file to save the model
        String modelName = "test";

        // Settings for the model builder
        modelBuilder.inputDirectoryName = trainDir;
        modelBuilder.modelName = modelName;

        // change to 1 for short documents
        modelBuilder.minNumOccur = 2;

        // Run model builder
        //        HashSet<String> fileNames = modelBuilder.collectStems();
        MauiFilter mauiFilter = modelBuilder.buildModel();
        modelBuilder.saveModel(mauiFilter);

        // Settings for topic extractor
        topicExtractor.inputDirectoryName = testDir;
        topicExtractor.modelName = modelName;

        // Run topic extractor
        topicExtractor.loadModel();
    }

    private void setFeatures() {
        modelBuilder.setBasicFeatures(true);
        modelBuilder.setKeyphrasenessFeature(true);
        modelBuilder.setFrequencyFeatures(true);
        modelBuilder.setPositionsFeatures(true);
        modelBuilder.setLengthFeature(true);
    }

}
