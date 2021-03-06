/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.maven.ext.manip.impl;

import static org.commonjava.maven.ext.manip.util.IdUtils.ga;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.commonjava.maven.ext.manip.ManipulationException;
import org.commonjava.maven.ext.manip.model.Project;
import org.commonjava.maven.ext.manip.state.BOMState;
import org.commonjava.maven.ext.manip.state.ManipulationSession;

/**
 * {@link Manipulator} implementation that can alter plugin sections in a project's pom file.
 * Configuration is stored in a {@link BOMState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Component( role = Manipulator.class, hint = "plugin-manipulator" )
public class PluginManipulator
    extends AlignmentManipulator
{
    protected PluginManipulator()
    {
    }

    public PluginManipulator( final Logger logger )
    {
        super( logger );
    }

    @Override
    public void init( final ManipulationSession session )
    {
        super.init( session );
    }

    @Override
    protected Map<String, String> loadRemoteBOM( final BOMState state, final ManipulationSession session )
        throws ManipulationException
    {
        return loadRemoteOverrides( RemoteType.PLUGIN, state.getRemotePluginMgmt(), session );
    }

    @Override
    protected void apply( final ManipulationSession session, final Project project, final Model model,
                          final Map<String, String> override )
        throws ManipulationException
    {
        // TODO: Should plugin override apply to all projects?
        logger.info( "Applying plugin changes to: " + ga( project ) );

        if ( project.isTopPOM() )
        {
            // If the model doesn't have any plugin management set by default, create one for it
            Build build = model.getBuild();

            if ( build == null )
            {
                build = new Build();
                model.setBuild( build );
                logger.info( "Created new Build for model " + model.getId() );
            }

            PluginManagement pluginManagement = model.getBuild()
                                                     .getPluginManagement();

            if ( pluginManagement == null )
            {
                pluginManagement = new PluginManagement();
                model.getBuild()
                     .setPluginManagement( pluginManagement );
                logger.info( "Created new Plugin Management for model " + model.getId() );
            }

            // Override plugin management versions
            applyOverrides( pluginManagement.getPlugins(), override );
        }

        if ( model.getBuild() != null )
        {
            // Override plugin versions
            final List<Plugin> projectPlugins = model.getBuild()
                                                     .getPlugins();
            applyOverrides( projectPlugins, override );
        }

    }

    /**
     * Set the versions of any plugins which match the contents of the list of plugin overrides
     *
     * @param plugins The list of plugins to modify
     * @param pluginVersionOverrides The list of version overrides to apply to the plugins
     */
    protected void applyOverrides( final List<Plugin> plugins, final Map<String, String> pluginVersionOverrides )
    {
        for ( final Plugin plugin : ( plugins == null ? Collections.<Plugin> emptyList() : plugins ) )
        {
            final String groupIdArtifactId = plugin.getGroupId() + BOMState.GAV_SEPERATOR + plugin.getArtifactId();
            if ( pluginVersionOverrides.containsKey( groupIdArtifactId ) )
            {
                final String overrideVersion = pluginVersionOverrides.get( groupIdArtifactId );
                plugin.setVersion( overrideVersion );
                logger.info( "Altered plugin: " + groupIdArtifactId + "=" + overrideVersion );
            }
        }
    }
}
