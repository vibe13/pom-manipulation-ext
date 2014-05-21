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
package org.commonjava.maven.ext.manip.fixture;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;

public class StubRepositorySystem
    implements RepositorySystem
{

    private File metadataFile;

    @Override
    public VersionRangeResult resolveVersionRange( final RepositorySystemSession session,
                                                   final VersionRangeRequest request )
        throws VersionRangeResolutionException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public VersionResult resolveVersion( final RepositorySystemSession session, final VersionRequest request )
        throws VersionResolutionException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ArtifactDescriptorResult readArtifactDescriptor( final RepositorySystemSession session,
                                                            final ArtifactDescriptorRequest request )
        throws ArtifactDescriptorException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CollectResult collectDependencies( final RepositorySystemSession session, final CollectRequest request )
        throws DependencyCollectionException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DependencyResult resolveDependencies( final RepositorySystemSession session, final DependencyRequest request )
        throws DependencyResolutionException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ArtifactResult resolveArtifact( final RepositorySystemSession session, final ArtifactRequest request )
        throws ArtifactResolutionException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ArtifactResult> resolveArtifacts( final RepositorySystemSession session,
                                                  final Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {
        // TODO Auto-generated method stub
        return null;
    }

    public void setMetadataFile( final File metadataFile )
    {
        this.metadataFile = metadataFile;
    }

    @Override
    public List<MetadataResult> resolveMetadata( final RepositorySystemSession session,
                                                 final Collection<? extends MetadataRequest> requests )
    {
        final List<MetadataResult> results = new ArrayList<MetadataResult>();
        for ( final MetadataRequest req : requests )
        {
            Metadata md = req.getMetadata();
            md = md.setFile( metadataFile );
            final MetadataResult result = new MetadataResult( req );
            result.setMetadata( md );
            results.add( result );
        }

        return results;
    }

    @Override
    public InstallResult install( final RepositorySystemSession session, final InstallRequest request )
        throws InstallationException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DeployResult deploy( final RepositorySystemSession session, final DeployRequest request )
        throws DeploymentException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SyncContext newSyncContext( final RepositorySystemSession session, final boolean shared )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public LocalRepositoryManager newLocalRepositoryManager( RepositorySystemSession session,
                                                             LocalRepository localRepository )
    {
        // TODO Auto-generated method stub
        return null;
    }
}
