/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.vault;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;

import javax.annotation.Nonnull;
import java.io.File;

public class VaultPlugin implements Plugin<PluginAware> {

    final static String EXTENSION_NAME = "vault";

    @Override
    public void apply(@Nonnull PluginAware target) {
        final File rootDir;
        if (target instanceof Settings settings) {
            rootDir = settings.getRootDir();
        } else if (target instanceof Project project) {
            rootDir = project.getRootDir();
        } else {
            throw new GradleException("Can't apply plugin to " + target.getClass());
        }
        final VaultExtension extension = ((ExtensionAware) target).getExtensions().create(
                EXTENSION_NAME,
                VaultExtension.class,
                new File(rootDir, ".gradle/secrets")
        );
        extension.getExtensions().create("auth", VaultAuthenticationExtension.class);
    }

}
