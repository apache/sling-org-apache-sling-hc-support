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

import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;

import javax.inject.Inject;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

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
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ScriptedHealthCheckIT extends HCSupportTestSupport {

    @Inject
    protected SlingRepository repository;

    @Configuration
    public Option[] configuration() throws IOException {
        return options(
            baseConfiguration(),
            mavenBundle().groupId("org.apache.groovy").artifactId("groovy").version("4.0.3"),
            mavenBundle().groupId("org.apache.groovy").artifactId("groovy-jsr223").version("4.0.3"),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test")
                .put("hc.tags", new String[] {"scriptedtest"})
                .put("language", "groovy") //"gsp"
                .put("script", "log.info('ok')")
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test2")
                .put("hc.tags", new String[] {"scriptedurltest"})
                .put("language", "groovy") //"gsp"
                .put("script", "")
                .put("scriptUrl", Paths.get(String.format("%s/target/test-classes/test-content/test2.groovy", PathUtils.getBaseDir())).toUri().toString())
                .asOption(),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test3")
                .put("hc.tags", new String[] {"scriptedjcrurltest"})
                .put("language", "groovy") //"gsp"
                .put("script", "")
                .put("scriptUrl", "jcr:/content/test2.groovy")
                .asOption()
        );
    }

    @Test
    public void testScriptedHealthCheck() throws Exception {
        assertTrue(waitForHealthCheck("scriptedtest", Duration.ofSeconds(30), Duration.ofMillis(100)));
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

}
