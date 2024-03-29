/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.hc.support.impl.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScriptingJavascript;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import javax.inject.Inject;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.felix.hc.api.Result;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.PathUtils;

/**
 * Tests for SLING-11141
 * @deprecated for SLING-11445 -  use the equivalent functionality from the org.apache.felix.healthcheck.generalchecks bundle instead
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
@Deprecated
public class ScriptedHealthCheckIT extends HCSupportTestSupport {

    @Inject
    protected SlingRepository repository;

    @Configuration
    public Option[] configuration() throws IOException {
        return options(
            baseConfiguration(),
            mavenBundle().groupId("org.apache.groovy").artifactId("groovy").version("4.0.3"),
            mavenBundle().groupId("org.apache.groovy").artifactId("groovy-jsr223").version("4.0.3"),
            slingScriptingJavascript(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test")
                .put("hc.tags", new String[] {"scriptedtest"})
                .put("language", "groovy")
                .put("script", "log.info('ok')")
                .put("scriptUrl", "not_valid_ignored")
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test2")
                .put("hc.tags", new String[] {"scriptedurltest"})
                .put("language", "groovy")
                .put("script", "")
                .put("scriptUrl", Paths.get(String.format("%s/target/test-classes/test-content/test2.groovy", PathUtils.getBaseDir())).toUri().toString())
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test3")
                .put("hc.tags", new String[] {"scriptedjcrurltest"})
                .put("language", "groovy")
                .put("script", "")
                .put("scriptUrl", "jcr:/content/test2.groovy")
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test (invalid language)")
                .put("hc.tags", new String[] {"not_valid"})
                .put("language", "not_valid")
                .put("script", "log.info('ok')")
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test2 (invalid path)")
                .put("hc.tags", new String[] {"not_valid_scriptedurltest"})
                .put("language", "groovy")
                .put("script", "")
                .put("scriptUrl", Paths.get(String.format("%s/target/test-classes/test-content/not_valid.groovy", PathUtils.getBaseDir())).toUri().toString())
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test3 (invalid path)")
                .put("hc.tags", new String[] {"not_valid_scriptedjcrurltest"})
                .put("language", "groovy")
                .put("script", "")
                .put("scriptUrl", "jcr:/content/not_valid.groovy")
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test4")
                .put("hc.tags", new String[] {"ecmascript_scriptedtest"})
                .put("language", "ECMAScriPt")
                .put("script", "log.info('ok')")
                .asOption()
        );
    }

    @Test
    public void testScriptedHealthCheck() throws Exception {
        assertTrue(waitForHealthCheck("scriptedtest", Duration.ofSeconds(30), Duration.ofMillis(100)));
    }

    @Test
    public void testECMAScriptScriptedHealthCheck() throws Exception {
        assertTrue(waitForHealthCheck("ecmascript_scriptedtest", Duration.ofSeconds(30), Duration.ofMillis(100)));
    }

    @Test
    public void testFileScriptedUrlHealthCheck() throws Exception {
        assertTrue(waitForHealthCheck("scriptedurltest", Duration.ofSeconds(30), Duration.ofMillis(100)));
    }

    @Test
    public void testJcrScriptedUrlHealthCheck() throws Exception {
        //publish the groovy script as a JCR file node
        Session jcrSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        Node fileNode = jcrSession.getNode("/content").addNode("test2.groovy", "nt:file");
        Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
        Binary dataBinary = jcrSession.getValueFactory().createBinary(getClass().getResourceAsStream("/test-content/test2.groovy"));
        contentNode.setProperty("jcr:data", dataBinary);
        contentNode.setProperty("jcr:mimeType", "application/x-groovy");
        jcrSession.save();

        assertTrue(waitForHealthCheck("scriptedjcrurltest", Duration.ofSeconds(30), Duration.ofMillis(100)));
    }

    private Result healthCheckError(String tags) throws Exception {
        List<Result> results = waitForHealthCheck(Result.Status.HEALTH_CHECK_ERROR, tags, Duration.ofSeconds(30), Duration.ofMillis(100));
        assertNotNull(results);
        assertEquals(1, results.size());
        Result r = results.get(0);
        assertEquals(Result.Status.HEALTH_CHECK_ERROR, r.getStatus());
        return r;
    }

    @Test
    public void testScriptedHealthCheckForNotValidLanguage() throws Exception {
        Result r = healthCheckError("not_valid");
        assertTrue("Expected IllegalStateException for invalid language", r.toString().contains("java.lang.IllegalStateException: Could not get script engine for not_valid from available factories"));
    }

    @Test
    public void testNotValidJcrScriptedUrlHealthCheck() throws Exception {
        Result r = healthCheckError("not_valid_scriptedjcrurltest");
        assertTrue("Expected IllegalStateException for invalid jcr path", r.toString().contains("Exception: Could not load script from path"));
    }

    @Test
    public void testNotValidFileScriptedUrlHealthCheck() throws Exception {
        Result r = healthCheckError("not_valid_scriptedurltest");
        assertTrue("Expected IllegalStateException for invalid file URL", r.toString().contains("Exception: Could not read file URL"));
    }

}
