package ru.aigul.mts_service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class XmlUserStore {

    public static final String SYSTEM_PROPERTY = "app.users.xml.path";

    private final Path xmlPath;

    public XmlUserStore(@Value("${app.security.users-xml-path}") String path) throws Exception {
        this.xmlPath = Paths.get(path).toAbsolutePath();
        initFile();
        System.setProperty(SYSTEM_PROPERTY, xmlPath.toString());
    }

    private void initFile() throws Exception {
        if (!Files.exists(xmlPath)) {
            Files.createDirectories(xmlPath.getParent());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("users.xml")) {
                if (is == null) throw new IllegalStateException("classpath:users.xml not found");
                Files.copy(is, xmlPath);
            }
        }
    }

    public synchronized void addUser(String email, String passwordHash, String role) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlPath.toFile());

        Element user = doc.createElement("user");
        user.setAttribute("email", email);
        user.setAttribute("passwordHash", passwordHash);
        user.setAttribute("role", role);
        doc.getDocumentElement().appendChild(user);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        try (OutputStream os = Files.newOutputStream(xmlPath);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
        }
    }
}