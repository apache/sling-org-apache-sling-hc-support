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

import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

import ch.qos.logback.classic.Level;

/**
 * @deprecated for SLING-11445 -  use the equivalent functionality from the org.apache.felix.healthcheck.generalchecks bundle instead
 */
@Deprecated
public class ScriptedHealthCheckTest {

    @Test
    public void testHealthCheckDeprecatedWarning() throws Exception {
        final ScriptedHealthCheck c = new ScriptedHealthCheck();
        ScriptedHealthCheck.Config config = Mockito.mock(ScriptedHealthCheck.Config.class);
        Mockito.when(config.language()).thenReturn("groovy");

        BundleContext bc = Mockito.mock(BundleContext.class);
        try (LogCapture capture = new LogCapture("org.apache.sling.hc.support.impl.ScriptedHealthCheck", true)) {
            // this should log a deprecation warning
            c.activate(bc, config);

            // verify the warning was logged
            capture.assertContains(Level.WARN, "This is deprecated. Please use the use the equivalent functionality from the org.apache.felix.healthcheck.generalchecks bundle instead.");
        }

    }

}
