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

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.sling.testing.paxexam.TestSupport;
import org.awaitility.Awaitility;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.OptionalCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;

public class HCSupportTestSupport extends TestSupport {

    @Inject
    private HealthCheckExecutor hcExecutor;


    final Option hcSupport = mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.hc.support").version(versionResolver);

    public ModifiableCompositeOption baseConfiguration() {
        final String workingDirectory = workingDirectory();
        final int httpPort = findFreePort();

        return composite(
            super.baseConfiguration(),
            slingQuickstartOakTar(workingDirectory, httpPort),
            // Sling HC Support
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.hc.support=[sling-readall]"})
                .asOption(),
            testBundle("bundle.filename"),
            awaitility(),
            junitBundles(),
            optionalRemoteDebug(),
            jacoco() // remove with Testing PaxExam 4.0
        ).remove(
            hcSupport
        );
    }

    /**
     * Optionally configure remote debugging on the port supplied by the "debugPort"
     * system property.
     */
    protected ModifiableCompositeOption optionalRemoteDebug() {
        VMOption option = null;
        String property = System.getProperty("debugPort");
        if (property != null) {
            option = vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", property));
        }
        return composite(option);
    }

    // remove with Testing PaxExam 4.0
    protected OptionalCompositeOption jacoco() {
        final String jacocoCommand = System.getProperty("jacoco.command");
        final VMOption option = Objects.nonNull(jacocoCommand) && !jacocoCommand.trim().isEmpty() ? vmOption(jacocoCommand) : null;
        return when(Objects.nonNull(option)).useOptions(option);
    }

    /**
     * Wait for the heathcheck to be completed
     * 
     * @param timeoutMsec the max time to wait for the healthcheck
     * @param nextIterationDelay the sleep time between the check attempts
     */
    protected boolean waitForHealthCheck(String tags, Duration atMost, Duration pollInterval) throws Exception {
        Awaitility.await("healthCheck: " + tags)
            .atMost(atMost)
            .pollInterval(pollInterval)
            .until(() -> {
                boolean result = false;
                HealthCheckSelector hcs = HealthCheckSelector.tags(tags);
                List<HealthCheckExecutionResult> results = hcExecutor.execute(hcs);
                if (!results.isEmpty()) {
                    for (final HealthCheckExecutionResult exR : results) {
                        final Result r = exR.getHealthCheckResult();
                        result = r.isOk();
                    }
                }
                return result;
            });
        return true;
    }

}
