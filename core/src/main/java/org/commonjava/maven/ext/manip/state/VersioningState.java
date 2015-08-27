/**
 * Copyright (C) 2012 Red Hat, Inc. (jcasey@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.maven.ext.manip.state;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.manip.impl.ProjectVersioningManipulator;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Captures configuration and changes relating to the projects' versions. Used by {@link ProjectVersioningManipulator}.
 *
 * @author jdcasey
 */
public class VersioningState
    implements State
{

    public static final String VERSION_SUFFIX_SYSPROP = "version.suffix";

    public static final String INCREMENT_SERIAL_SUFFIX_SYSPROP = "version.incremental.suffix";

    public static final String VERSION_SUFFIX_SNAPSHOT_SYSPROP = "version.suffix.snapshot";

    public static final String VERSION_OSGI_SYSPROP = "version.osgi";

    public static final String VERSION_OVERRIDE_SYSPROP = "version.override";

    private final String suffix;

    private final String incrementSerialSuffix;

    private final boolean preserveSnapshot;

    private final boolean osgi;

    private final String override;

    /**
     * Store preprocessed metadata from the REST call in order to use for incremental lookup.
     */
    private Map<ProjectRef, Set<String>> restMetaData;

    public VersioningState( final Properties userProps )
    {
        suffix = userProps.getProperty( VERSION_SUFFIX_SYSPROP );
        incrementSerialSuffix = userProps.getProperty( INCREMENT_SERIAL_SUFFIX_SYSPROP );
        preserveSnapshot = Boolean.parseBoolean( userProps.getProperty( VERSION_SUFFIX_SNAPSHOT_SYSPROP ) );
        osgi = Boolean.parseBoolean( userProps.getProperty( VERSION_OSGI_SYSPROP, "true" ) );
        override = userProps.getProperty( VERSION_OVERRIDE_SYSPROP );
    }

    /**
     * @return the incremental suffix that will be appended to the project version.
     */
    public String getIncrementalSerialSuffix()
    {
        return incrementSerialSuffix;
    }

    /**
     * @return the version suffix to be appended to the project version.
     */
    public String getSuffix()
    {
        return suffix;
    }

    /**
     * @return true if we should preserve the snapshot
     */
    public boolean preserveSnapshot()
    {
        return preserveSnapshot;
    }

    /**
     * Forcibly override the version to a new one.
     * @return the new version
     */
    public String getOverride()
    {
        return override;
    }

    /**
     * @return true if we should make the versions OSGi compliant
     */
    public boolean osgi()
    {
        return osgi;
    }

    /**
     * Enabled ONLY if either version.incremental.suffix or version.suffix is provided in the user properties / CLI -D options.
     *
     * @see #VERSION_SUFFIX_SYSPROP
     * @see #INCREMENT_SERIAL_SUFFIX_SYSPROP
     * @see org.commonjava.maven.ext.manip.state.State#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return incrementSerialSuffix != null || suffix != null || override != null;
    }

    public void setRESTMetadata( Map<ProjectRef, Set<String>> versionStates )
    {
        restMetaData = versionStates;
    }

    public Map<ProjectRef, Set<String>> getRESTMetadata( )
    {
        return restMetaData;
    }
}
