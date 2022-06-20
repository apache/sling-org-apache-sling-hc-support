/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.sling.hc.support.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Session;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link HealthCheck} that runs an arbitrary script. */
@Component(service = HealthCheck.class, name = "org.apache.sling.hc.support.ScriptedHealthCheck", configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = ScriptedHealthCheck.Config.class, factory = true)
public class ScriptedHealthCheck implements HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptedHealthCheck.class);

    public static final String HC_LABEL = "Health Check: Sling Script";

    public static final String JCR_FILE_URL_PREFIX = "jcr:";
    private static final String JCR_CONTENT = "/jcr:content";

    @ObjectClassDefinition(name = HC_LABEL, description = "NOTE: This Sling pendant of org.apache.felix.hc.generalchecks.ScriptedHealthCheck allows to use scriptUrls with prefix 'jcr:' and has the additional bindings 'resourceResolver' and 'session'. "
            + "Runs an arbitrary script in given scriping language (via javax.script). "
            + "The script has the following default bindings available: 'log', 'scriptHelper', 'bundleContext', 'resourceResolver' and 'session'. "
            + "'log' is an instance of org.apache.felix.hc.api.FormattingResultLog and is used to define the result of the HC. "
            + "'scriptHelper.getService(classObj)' can be used as shortcut to retrieve a service."
            + "'scriptHelper.getServices(classObj, filter)' used to retrieve multiple services for a class using given filter. "
            + "For all services retrieved via scriptHelper, unget() is called automatically at the end of the script execution."
            + "'bundleContext' is available for advanced use cases. The script does not need to return any value, but if it does and it is "
            + "a org.apache.felix.hc.api.Result, that result and entries in 'log' are combined then).")
    @interface Config {

        @AttributeDefinition(name = "Name", description = "Name of this health check.")
        String hc_name() default "Scripted Health Check"; // NOSONAR

        @AttributeDefinition(name = "Tags", description = "List of tags for this health check, used to select subsets of health checks for execution e.g. by a composite health check.")
        String[] hc_tags() default {}; // NOSONAR

        @AttributeDefinition(name = "Language", description = "The language the script is written in. To use e.g. 'groovy', ensure osgi bundle 'groovy-jsr223' is available.")
        String language() default "groovy";

        @AttributeDefinition(name = "Script", description = "The script itself (either use 'script' or 'scriptUrl').")
        String script() default "log.info('ok'); log.warn('not so good'); log.critical('bad') // minimal example";

        @AttributeDefinition(name = "Script Url", description = "Url to the script to be used as alternative source (either use 'script' or 'scriptUrl').")
        String scriptUrl() default "";

        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "Scripted HC: {hc.name} (tags: {hc.tags}) {scriptUrl} language: {language}"; // NOSONAR
    }

    private String language;
    private String script;
    private String scriptUrl;

    private BundleContext bundleContext;

    @Reference
    private ScriptEngineManager scriptEngineManager;

    private ScriptHelper scriptHelper = new ScriptHelper();

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Activate
    protected void activate(BundleContext context, Config config) {
        this.bundleContext = context;
        this.language = config.language().toLowerCase();
        this.script = config.script();
        this.scriptUrl = config.scriptUrl();

        if (StringUtils.isNotBlank(script) && StringUtils.isNotBlank(scriptUrl)) {
            LOG.info("Both 'script' and 'scriptUrl' (={}) are configured, ignoring 'scriptUrl'", scriptUrl);
            scriptUrl = null;
        }

        LOG.info("Activated Scripted HC {} with {}", config.hc_name(),
                (StringUtils.isNotBlank(script) ? "script " + script : "script url " + scriptUrl));

    }

    @Override
    public Result execute() {
        FormattingResultLog log = new FormattingResultLog();

        ResourceResolver resourceResolver = null;
        try {
            resourceResolver = resourceResolverFactory.getServiceResourceResolver(null);

            boolean urlIsUsed = StringUtils.isBlank(script);
            String scriptToExecute;
            if (urlIsUsed) {
                if (scriptUrl.startsWith(JCR_FILE_URL_PREFIX)) {
                    String jcrPath = StringUtils.substringAfter(scriptUrl, JCR_FILE_URL_PREFIX);
                    scriptToExecute = getScriptFromRepository(resourceResolver, jcrPath);
                } else {
                    scriptToExecute = scriptHelper.getFileContents(scriptUrl);
                }
            } else {
                scriptToExecute = script;
            }

            log.info("Executing script {} ({} lines)...", (urlIsUsed ? scriptUrl : " as configured"), scriptToExecute.split("\n").length);

            try {
                ScriptEngine scriptEngine = getScriptEngine(language);

                Map<String, Object> additionalBindings = new HashMap<>();
                additionalBindings.put("resourceResolver", resourceResolver);
                additionalBindings.put("session", resourceResolver.adaptTo(Session.class));
                scriptHelper.evalScript(bundleContext, scriptEngine, scriptToExecute, log, additionalBindings, true);
            } catch (Exception e) {
                log.healthCheckError("Exception while executing script: " + e, e);
            }

            return new Result(log);
        } catch (LoginException e) {
            throw new IllegalStateException("Could not get resource resolver: " + e, e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    private String factoriesToString(List<ScriptEngineFactory> engineFactories) {
        List<String> factoryArr = new ArrayList<>();
        for (ScriptEngineFactory ef : engineFactories) {
            factoryArr.add(ef.getEngineName() + " (" + StringUtils.join(ef.getExtensions(), ",") + ")");
        }
        return StringUtils.join(factoryArr, ", ");
    }

    private ScriptEngine getScriptEngine(String language) {
        ScriptEngine scriptEngine = scriptEngineManager.getEngineByExtension(language);
        if (scriptEngine == null) {
            try {
                scriptEngine = scriptHelper.getScriptEngine(scriptEngineManager, language);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Could not get script engine for " + language + " from available factories: "
                        + factoriesToString(scriptEngineManager.getEngineFactories()) + ") nor from regular bundles: " + e.getMessage());
            }
        }
        return scriptEngine;
    }

    private String getScriptFromRepository(ResourceResolver resourceResolver, String jcrPath) {
        String fileContent;
        try {
            Resource dataResource = resourceResolver.getResource(jcrPath + JCR_CONTENT);
            InputStream is = dataResource.adaptTo(InputStream.class);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            fileContent = result.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("Could not load script from path " + jcrPath + ": " + e, e);
        }
        return fileContent;
    }

    /**
     * Copied and adapted from org.apache.felix.hc.generalchecks.util.ScriptHelper
     */
    private static class ScriptHelper {

        public String getFileContents(String url) {
            String content;
            try {
                content = Files.readAllLines(Paths.get(new URI(url))).stream().collect(Collectors.joining("\n"));
                return content;
            }catch(IOException | URISyntaxException e) {
                throw new IllegalArgumentException("Could not read file URL "+url+": "+e, e);
            }
        }

        public ScriptEngine getScriptEngine(ScriptEngineManager scriptEngineManager, String language) {
            List<ScriptEngineFactory> engineFactories = scriptEngineManager.getEngineFactories();
            ScriptEngine scriptEngine = engineFactories.stream()
                .filter(s -> language.equals(s.getLanguageName()))
                .findFirst()
                .map(ScriptEngineFactory::getScriptEngine)
                .orElse(null);
            if(scriptEngine == null) {
                Set<String> availableLanguages = engineFactories.stream()
                    .map(ScriptEngineFactory::getLanguageName)
                    .collect(Collectors.toSet());
                throw new IllegalArgumentException("No ScriptEngineFactory found for language " + language + " (available languages: " + availableLanguages + ")");
            }
            return scriptEngine;
        }
        
        public Object evalScript(BundleContext bundleContext, ScriptEngine scriptEngine, String scriptToExecute, FormattingResultLog log, Map<String,Object> additionalBindings, boolean logScriptResult) throws ScriptException, IOException {

            final Bindings bindings = new SimpleBindings();
            final ScriptHelperBinding scriptHelper = new ScriptHelperBinding(bundleContext);

            StringWriter stdout = new StringWriter();
            StringWriter stderr = new StringWriter();

            bindings.put("scriptHelper", scriptHelper);
            bindings.put("osgi", scriptHelper); // also register script helper like in web console script console
            bindings.put("log", log);
            bindings.put("bundleContext", bundleContext);
            if (additionalBindings != null) {
                for (Map.Entry<String, Object> additionalBinding : additionalBindings.entrySet()) {
                    bindings.put(additionalBinding.getKey(), additionalBinding.getValue());
                }
            }
            
            SimpleScriptContext scriptContext = new SimpleScriptContext();
            scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            scriptContext.setWriter(stdout);
            scriptContext.setErrorWriter(stderr);

            try {
                log.debug(scriptToExecute);
                Object scriptResult = scriptEngine.eval(scriptToExecute, scriptContext);
                appendStreamsToResult(log, stdout, stderr, scriptContext);

                if(scriptResult instanceof Result) {
                    Result result = (Result) scriptResult;
                    for(ResultLog.Entry entry: result) {
                        log.add(entry);
                    }
                } else if(scriptResult != null && logScriptResult){
                    log.info("Script result: {}", scriptResult);
                }
                
                return scriptResult;
            } finally  {
                scriptHelper.ungetServices();
            }
        }

        private void appendStreamsToResult(FormattingResultLog log, StringWriter stdout, StringWriter stderr, SimpleScriptContext scriptContext)
                throws IOException {
            scriptContext.getWriter().flush();
            String stdoutStr = stdout.toString();
            if(StringUtils.isNotBlank(stdoutStr)) {
                log.info("stdout of script: {}", stdoutStr);
            }
            
            scriptContext.getErrorWriter().flush();
            String stderrStr = stderr.toString();
            if(StringUtils.isNotBlank(stderrStr)) {
                log.critical("stderr of script: {}", stderrStr);
            }
        }

        // Script Helper for OSGi available as binding 'scriptHelper'
        class ScriptHelperBinding {
            
            private final BundleContext bundleContext;
            private List<ServiceReference<?>> references;
            private Map<String, Object> services;

            public ScriptHelperBinding(BundleContext bundleContext) {
                this.bundleContext = bundleContext;
            }

            @SuppressWarnings({ "unchecked", "unused" })
            public <T> T getService(Class<T> type) {
                T service = (this.services == null ? null  : (T) this.services.get(type.getName()));
                if (service == null) {
                    final ServiceReference<?> ref = this.bundleContext.getServiceReference(type.getName());
                    if (ref != null) {
                        service = (T) this.bundleContext.getService(ref);
                        if (service != null) {
                            if (this.services == null) {
                                this.services = new HashMap<>();
                            }
                            if (this.references == null) {
                                this.references = new ArrayList<>();
                            }
                            this.references.add(ref);
                            this.services.put(type.getName(), service);
                        }
                    }
                }
                return service;
            }

            @SuppressWarnings("unused")
            public <T> T[] getServices(Class<T> serviceType,  String filter) throws InvalidSyntaxException {
                final ServiceReference<?>[] refs = this.bundleContext.getServiceReferences(serviceType.getName(), filter);
                T[] result = null;
                if (refs != null) {
                    final List<T> objects = new ArrayList<>();
                    for (int i = 0; i < refs.length; i++) {
                        @SuppressWarnings("unchecked")
                        final T service = (T) this.bundleContext.getService(refs[i]);
                        if (service != null) {
                            if (this.references == null) {
                                this.references = new ArrayList<>();
                            }
                            this.references.add(refs[i]);
                            objects.add(service);
                        }
                    }
                    if (!objects.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        T[] srv = (T[]) Array.newInstance(serviceType,  objects.size());
                        result = objects.toArray(srv);
                    }
                }
                return result;
            }

            public void ungetServices() {
                if (this.references != null) {
                    final Iterator<ServiceReference<?>> i = this.references.iterator();
                    while (i.hasNext()) {
                        final ServiceReference<?> ref = i.next();
                        this.bundleContext.ungetService(ref);
                    }
                    this.references.clear();
                }
                if (this.services != null) {
                    this.services.clear();
                }
            }
        }

    }

}
