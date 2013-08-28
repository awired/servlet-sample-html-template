/**
 *
 *     Copyright (C) norad.fr
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package fr.norad.servlet.sample.html.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.text.StrSubstitutor;

public class IndexTemplateServlet extends HttpServlet {

    private static final String TEMPLATE_PATH = "tplPath";
    private static final String CONTEXT_PATH_SUFFIX = "contextPathSuffix";

    private final Properties manifestProperties = new Properties();
    private final Map<String, String> propertiesNames = new HashMap<>();

    private String contextPathSuffix;
    private String template;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        String tplPath = config.getInitParameter(TEMPLATE_PATH);
        Validate.notNull(tplPath, "{} init param must be set", TEMPLATE_PATH);
        loadPropertiesFromManifest(config);
        contextPathSuffix = config.getInitParameter(CONTEXT_PATH_SUFFIX);
        template = loadTemplate(tplPath);
    }

    private void loadPropertiesFromManifest(ServletConfig config) {
        @SuppressWarnings("unchecked")
        Enumeration<String> initParameterNames = config.getInitParameterNames();
        while (initParameterNames.hasMoreElements()) {
            String initParamName = initParameterNames.nextElement();
            if (initParamName.endsWith(".property")) {
                String initParamValue = config.getInitParameter(initParamName);
                String value = WarManifestUtils.getWarManifestAttribute(getServletContext(), initParamValue);
                String name = initParamNameToName(initParamName);
                propertiesNames.put(name, initParamValue);
                if (value != null) {
                    manifestProperties.put(name, value);
                }
            }
        }
    }

    private String initParamNameToName(String initParamName) {
        if (initParamName == null) {
            return null;
        }
        return initParamName.substring(0, initParamName.indexOf('.'));
    }

    private Properties getPropertiesOverridenWithSystem() {
        Properties finalProperties = new Properties(manifestProperties);
        for (String name : propertiesNames.keySet()) {
            String systemValue = System.getProperty(propertiesNames.get(name));
            if (systemValue != null) {
                finalProperties.put(name, systemValue);
            }
        }
        return finalProperties;
    }

    private String loadTemplate(String tplPath) {
        InputStream tplStream = getServletContext().getResourceAsStream(tplPath);
        if (tplStream == null) {
            return null;
        }
        try {
            return IOUtils.toString(tplStream);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read template", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (template == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        HashMap<String, String> hashMap = new HashMap<>();
        String contextPath = req.getContextPath();
        if (contextPathSuffix != null && !contextPathSuffix.equals("")) {
            contextPath += contextPathSuffix;
        }
        hashMap.put("contextPath", contextPath);
        hashMap.put("fullWebPath", req.getRequestURL().toString().replace(req.getRequestURI(), contextPath));
        Properties propertiesOverridenWithSystem = getPropertiesOverridenWithSystem();
        for (String propertyName : propertiesNames.keySet()) {
            hashMap.put(propertyName, propertiesOverridenWithSystem.getProperty(propertyName));
        }

        String response = StrSubstitutor.replace(template, hashMap);

        resp.setStatus(200);
        resp.setContentType("text/html; charset=utf-8");
        resp.getWriter().write(response);
    }
}
