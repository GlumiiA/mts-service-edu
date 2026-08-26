package ru.aigul.mts_service.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.util.Map;

public class XMLLoginModule implements LoginModule {
    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, ?> sharedState;
    private Map<String, ?> options;

    private boolean loginSucceeded;
    private boolean commitSucceeded;

    private Document document;
    private String xmlPath;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private String username;
    private String password;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
            Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = sharedState;
        this.options = options;
        this.xmlPath = (String) options.get("usersFile");

        loadXMLDocument();
    }

    @Override
    public boolean login() throws LoginException {
        if (callbackHandler == null) {
            throw new LoginException("No callback handler");
        }
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);
        try {
            callbackHandler.handle(callbacks);
        } catch (IOException e) {
            throw new LoginException("IOException: " + e.getMessage());
        } catch (UnsupportedCallbackException e) {
            throw new LoginException("UnsupportedCallbackException: " + e.getMessage());
        }

        username = ((NameCallback) callbacks[0]).getName();
        char[] passwordChar = ((PasswordCallback) callbacks[1]).getPassword();
        password = new String(passwordChar);

        if (authenticateFromXml(username, password)) {
            loginSucceeded = true;
        } else {
            loginSucceeded = false;
            throw new LoginException("Login failed");
        }

        return true;
    }

    @Override
    public boolean commit() throws LoginException {
        if (!loginSucceeded) {
            return false;
        }

        UserPrincipal usernamePrincipal = new UserPrincipal(username);
        RolePrincipal rolePrincipal = new RolePrincipal(getRoleFromXML(username));
        subject.getPrincipals().add(usernamePrincipal);
        subject.getPrincipals().add(rolePrincipal);

        commitSucceeded = true;
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        if (!loginSucceeded) {
            return false;
        }

        if (commitSucceeded) {
            logout();
        } else {
            loginSucceeded = false;
        }

        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        subject.getPrincipals().removeIf(u -> u instanceof UserPrincipal || u instanceof RolePrincipal);
        loginSucceeded = false;
        commitSucceeded = false;
        username = null;
        password = null;

        return true;
    }

    public void loadXMLDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();

            String fsPath = System.getProperty(XmlUserStore.SYSTEM_PROPERTY);
            if (fsPath != null) {
                document = documentBuilder.parse(new java.io.File(fsPath));
            } else {
                document = documentBuilder.parse(
                        getClass().getClassLoader().getResourceAsStream(xmlPath));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean authenticateFromXml(String username, String password) {
        if (document == null) {
            System.err.println("Document is null");
            return false;
        }

        try {
            NodeList nodes = document.getElementsByTagName("user");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String xmlEmail = el.getAttribute("email");
                String xmlPasswordHash = el.getAttribute("passwordHash");
                if (xmlEmail.equals(username) && passwordEncoder.matches(password, xmlPasswordHash)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getRoleFromXML(String username) {
        if (document == null) {
            System.err.println("Document is null");
            return null;
        }

        try {
            NodeList nodes = document.getElementsByTagName("user");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String xmlEmail = el.getAttribute("email");
                if (xmlEmail.equals(username)) {
                    return el.getAttribute("role");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
