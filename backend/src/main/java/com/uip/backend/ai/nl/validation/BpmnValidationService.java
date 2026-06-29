package com.uip.backend.ai.nl.validation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates BPMN validation: XSD structural check → custom semantic rules.
 *
 * <p>Both stages run and errors are combined. Validation halts at XSD stage only on
 * fatal XML parse errors (malformed XML); otherwise all rules are evaluated and
 * all violations are reported together.
 *
 * <p>M5-2 T10: Base validation. M5-3 T02 extends with semantic safety rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BpmnValidationService {

    private static final String XSD_PATH = "validation/bpmn-subset.xsd";

    private final BpmnCustomValidator customValidator;

    private Schema schema;

    @PostConstruct
    void loadSchema() throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Disable external entity access (XXE prevention)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");

        ClassPathResource xsdResource = new ClassPathResource(XSD_PATH);
        schema = factory.newSchema(new StreamSource(xsdResource.getInputStream()));
        log.info("BPMN subset XSD schema loaded from classpath:{}", XSD_PATH);
    }

    /**
     * Validate BPMN XML against XSD then custom rules.
     *
     * @param bpmnXml raw BPMN XML string
     * @return combined {@link ValidationResult} from both stages
     */
    public ValidationResult validate(String bpmnXml) {
        List<String> errors = new ArrayList<>();

        // Stage 1: XSD structural validation
        List<String> xsdErrors = validateXsd(bpmnXml);
        errors.addAll(xsdErrors);

        // Stage 2: Custom rules (run regardless of XSD result, unless XML is unparseable)
        if (xsdErrors.stream().noneMatch(e -> e.startsWith("XSD_PARSE:"))) {
            ValidationResult customResult = customValidator.validate(bpmnXml);
            errors.addAll(customResult.errors());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private List<String> validateXsd(String bpmnXml) {
        List<String> errors = new ArrayList<>();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            errors.add("XSD_PARSE: BPMN XML must not be empty");
            return errors;
        }
        try {
            Validator validator = schema.newValidator();
            // Disable external entity access on validator as well (defence-in-depth)
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new StringReader(bpmnXml)));
        } catch (SAXException e) {
            errors.add("XSD: " + e.getMessage());
        } catch (IOException e) {
            errors.add("XSD_PARSE: " + e.getMessage());
            log.error("Unexpected IO error during BPMN XSD validation", e);
        }
        return errors;
    }
}
