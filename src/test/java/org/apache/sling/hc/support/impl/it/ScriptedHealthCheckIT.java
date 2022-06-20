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

import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Tests for SLING-11141
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ScriptedHealthCheckIT extends HCSupportTestSupport {

    @Configuration
    public Option[] configuration() {
        return options(
            baseConfiguration(),
            mavenBundle().groupId("org.apache.groovy").artifactId("groovy").version("4.0.3"),
            mavenBundle().groupId("org.apache.groovy").artifactId("groovy-jsr223").version("4.0.3"),
            factoryConfiguration("org.apache.sling.hc.support.ScriptedHealthCheck")
                .put("hc.name", "Scripted Heath Check Test")
                .put("hc.tags", new String[] {"scriptedtest"})
                .put("language", "groovy") //"gsp"
                .put("script", "log.info('ok')")
                .asOption()
        );
    }

    @Test
    public void testScriptedHealthCheck() throws Exception {
        assertTrue(waitForHealthCheck("scriptedtest", Duration.ofSeconds(30), Duration.ofMillis(100)));
    }

}
