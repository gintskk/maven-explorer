/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.f4sten.pomanalyzer.utils;

import java.io.File;

import org.apache.maven.settings.Settings;
import org.jboss.shrinkwrap.resolver.impl.maven.SettingsManager;

public class MavenSettingsUtils {

    public static File getPathOfLocalRepository() {
        // By default, this is set to ~/.m2/repository/, but that can be re-configured
        // or even provided as a parameter. As such, we are reusing an existing library
        // to find the right folder.
        var settings = new SettingsManager() {
            @Override
            public Settings getSettings() {
                return super.getSettings();
            }
        }.getSettings();
        var localRepository = settings.getLocalRepository();
        return new File(localRepository);
    }
}