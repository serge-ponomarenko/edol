package org.spon.edolcore.service.printmetadata;

import org.spon.edol.model.Filament;
import org.spon.edol.model.PrintObject;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SliceInfoParserService {

    public int extractPlateIndex(Path sliceInfoPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse(sliceInfoPath.toFile());

        XPath xpath = XPathFactory.newInstance().newXPath();

        String value = xpath.evaluate(
                "/config/plate/metadata[@key='index']/@value",
                doc
        );

        if (value == null || value.isEmpty())
            throw new RuntimeException("Plate index not found");

        return Integer.parseInt(value);
    }

    public List<Filament> parseFilaments(Path sliceInfoPath) {
        List<Filament> filaments = new ArrayList<>();

        try {

            Document doc = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(sliceInfoPath.toFile());

            NodeList nodes = doc.getElementsByTagName("filament");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element el = (Element) nodes.item(i);

                Filament filament = new Filament(
                        Integer.parseInt(el.getAttribute("id")),
                        el.getAttribute("tray_info_idx"),
                        el.getAttribute("type"),
                        el.getAttribute("color"),
                        null,
                        null,
                        Double.parseDouble(el.getAttribute("used_m")),
                        Double.parseDouble(el.getAttribute("used_g")),
                        Boolean.parseBoolean(el.getAttribute("used_for_object")),
                        Boolean.parseBoolean(el.getAttribute("used_for_support")),
                        null
                );

                filaments.add(filament);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Filaments in slice_info.config", e);
        }

        return filaments;
    }

    public List<PrintObject> parsePrintObjects(Path sliceInfoPath) {
        List<PrintObject> printObjects = new ArrayList<>();

        try {
            Document doc = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(sliceInfoPath.toFile());

            NodeList nodes = doc.getElementsByTagName("object");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element el = (Element) nodes.item(i);

                PrintObject printObject = new PrintObject(
                        Integer.parseInt(el.getAttribute("identify_id")),
                        el.getAttribute("name"),
                        Boolean.parseBoolean(el.getAttribute("skipped"))
                );

                printObjects.add(printObject);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Print Objects in slice_info.config", e);
        }

        return printObjects;
    }
}
