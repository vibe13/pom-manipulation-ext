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

import static org.commonjava.maven.ext.manip.util.IdUtils.gav;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.maven.ext.manip.ManipulationException;
import org.commonjava.maven.ext.manip.model.Project;
import org.commonjava.maven.ext.manip.state.ManipulationSession;
import org.commonjava.maven.ext.manip.state.VersioningState;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.metadata.DefaultMetadata;

/**
 * Component that calculates project version modifications, based on configuration stored in {@link VersioningState}. Snapshots may/may not be
 * preserved, and either a static or incremental (calculated) version qualifier may / may not be incorporated in the version. The calculator
 * strives for OSGi compatibility, so the use of '.' and '-' qualifier separators will vary accordingly.
 *
 * See: http://www.aqute.biz/Bnd/Versioning for an explanation of OSGi versioning.
 *
 * @author jdcasey
 */
@Component( role = VersionCalculator.class )
public class VersionCalculator
{

    private static final String SERIAL_SUFFIX_PATTERN = "([^-.]+)(?:([-.])(\\d+))?$";

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    @Requirement
    private Logger logger;

    @Requirement
    protected RepositorySystem repositorySystem;

    protected VersionCalculator()
    {
    }

    public VersionCalculator( final RepositorySystem repositorySystem, final Logger logger )
    {
        this.logger = logger;
        this.repositorySystem = repositorySystem;
    }

    /**
     * Calculate any project version changes for the given set of projects, and return them in a Map keyed by project GA.
     */
    public Map<String, String> calculateVersioningChanges( final Collection<Project> projects,
                                                           final ManipulationSession session )
        throws ManipulationException
    {
        final Map<String, String> versionsByGAV = new HashMap<String, String>();

        for ( final Project project : projects )
        {
            final String originalVersion = project.getVersion();
            final String modifiedVersion =
                calculate( project.getGroupId(), project.getArtifactId(), originalVersion, session );

            if ( !modifiedVersion.equals( originalVersion ) )
            {
                final String gav = gav( project );
                logger.info( String.format( "%s has updated version: %s. Marking for rewrite.", gav, modifiedVersion ) );
                versionsByGAV.put( gav, modifiedVersion );
            }
        }

        return versionsByGAV;
    }

    /**
     * Calculate the version modification for a given GAV.
     */
    // FIXME: Loooong method
    protected String calculate( final String groupId, final String artifactId, final String originalVersion,
                                final ManipulationSession session )
        throws ManipulationException
    {
        String result = originalVersion;

        boolean snapshot = false;
        // If we're building a snapshot, make sure the resulting version ends
        // in "-SNAPSHOT"
        if ( result.endsWith( SNAPSHOT_SUFFIX ) )
        {
            snapshot = true;
            result = result.substring( 0, result.length() - SNAPSHOT_SUFFIX.length() );
        }

        final VersioningState state = session.getState( VersioningState.class );
        final String incrementalSerialSuffix = state.getIncrementalSerialSuffix();
        final String suffix = state.getSuffix();

        logger.debug( "Got the following version suffixes:\n  Static: " + suffix + "\nIncremental: "
            + incrementalSerialSuffix );

        final String suff = suffix != null ? suffix : incrementalSerialSuffix;

        logger.debug( "Using suffix: " + suff );
        final Pattern serialSuffixPattern = Pattern.compile( SERIAL_SUFFIX_PATTERN );
        final Matcher suffixMatcher = serialSuffixPattern.matcher( suff );

        String useSuffix = suff;
        if ( suffixMatcher.matches() )
        {
            // the "redhat" in "redhat-1"
            final String suffixBase = suffixMatcher.group( 1 );
            String sep = suffixMatcher.group( 2 );
            if ( sep == null )
            {
                sep = "-";
            }

            final int idx = result.indexOf( suffixBase );

            if ( idx > 1 )
            {
                // trim the old suffix off.
                result = result.substring( 0, idx - 1 );
                logger.debug( "Trimmed version (without pre-existing suffix): " + result );
            }

            // If we're using serial suffixes (-redhat-N) and the flag is set
            // to increment the existing suffix, read available versions from the
            // existing POM, plus the repository metadata, and find the highest
            // serial number to increment...then increment it.
            if ( suff.equals( incrementalSerialSuffix ) )
            {
                logger.debug( "Resolving suffixes already found in metadata to determine increment base." );

                final List<String> versionCandidates = new ArrayList<String>();
                versionCandidates.add( originalVersion );
                versionCandidates.addAll( getMetadataVersions( groupId, artifactId, session ) );

                int maxSerial = 0;

                for ( final String version : versionCandidates )
                {
                    final Matcher candidateSuffixMatcher = serialSuffixPattern.matcher( version );

                    if ( candidateSuffixMatcher.find() )
                    {
                        final String wholeSuffix = candidateSuffixMatcher.group();
                        logger.debug( "Group 0 of serial-suffix matcher is: '" + wholeSuffix + "'" );
                        final int baseIdx = version.indexOf( wholeSuffix );

                        // Need room for at least a character in the base-version, plus a separator like '-'
                        if ( baseIdx < 2 )
                        {
                            logger.debug( "Ignoring invalid version: '" + version
                                + "' (seems to be naked version suffix with no base)." );
                            continue;
                        }

                        final String base = version.substring( 0, baseIdx - 1 );
                        if ( !result.equals( base ) )
                        {
                            logger.debug( "Ignoring irrelevant version: '" + version + "' ('" + base
                                + "' doesn't match on base-version: '" + result + "')." );
                            continue;
                        }

                        // grab the old serial number.
                        final String serialStr = candidateSuffixMatcher.group( 3 );
                        logger.debug( "Group 3 of serial-suffix matcher is: '" + serialStr + "'" );
                        final int serial = serialStr == null ? 0 : Integer.parseInt( serialStr );
                        if ( serial > maxSerial )
                        {
                            logger.debug( "new max serial number: " + serial + " (previous was: " + maxSerial + ")" );
                            maxSerial = serial;

                            // don't assume we're using '-' as suffix-base-to-serial-number separator...
                            sep = candidateSuffixMatcher.group( 2 );
                        }
                    }
                }

                useSuffix = suffixBase + sep + ( maxSerial + 1 );
            }

            // Now, pare back the trimmed version base to remove non-alphanums
            // like '.' and '-' so we have more control over them...
            int trim = 0;

            // calculate the trim size
            for ( int i = result.length() - 1; i > 0 && !Character.isLetterOrDigit( result.charAt( i ) ); i-- )
            {
                trim++;
            }

            // perform the actual trim to get back to an alphanumeric ending.
            if ( trim > 0 )
            {
                result = result.substring( 0, result.length() - trim );
            }
        }
        // If we're not using a serial suffix, and the version already ends
        // with the chosen suffix, there's nothing to do!
        else if ( originalVersion.endsWith( suffix ) )
        {
            return originalVersion;
        }

        // assume the version is of the form 1.2.3.GA, where appending the
        // suffix requires a '-' to concatenate the string of the final version
        // part in OSGi.
        String sep = "-";

        // now, check the above assumption...
        // if the version is of the form: 1.2.3, then we need to append the
        // suffix as a final version part using '.'
        logger.info( "Partial result: " + result );
        if ( result.matches( ".+[-.]\\d+" ) )
        {
            sep = ".";
        }

        // TODO OSGi fixup for versions like 1.2.GA or 1.2 (too few parts)

        result += sep + useSuffix;

        // tack -SNAPSHOT back on if necessary...
        if ( state.preserveSnapshot() && snapshot )
        {
            result += SNAPSHOT_SUFFIX;
        }

        return result;
    }

    /**
     * Accumulate all available versions for a given GAV from all available repositories.
     */
    // FIXME: Switch to galley APIs
    private Set<String> getMetadataVersions( final String groupId, final String artifactId,
                                             final ManipulationSession session )
        throws ManipulationException
    {
        logger.debug( "Reading available versions from repository metadata for: " + groupId + ":" + artifactId );

        final Set<String> versions = new HashSet<String>();
        final List<ArtifactRepository> remoteRepositories = session.getRequest()
                                                                   .getRemoteRepositories();
        for ( final ArtifactRepository repo : remoteRepositories )
        {
            final RemoteRepository remote = RepositoryUtils.toRepo( repo );

            logger.debug( "Checking: " + remote.getUrl() );
            resolveMetadata( groupId, artifactId, remote, versions, session );
        }

        return versions;
    }

    /**
     * Read available versions for a given GAV from its repository metadata, and accumulate the versions in the given {@link Set}.
     */
    private void resolveMetadata( final String groupId, final String artifactId, final RemoteRepository remote,
                                  final Set<String> versions, final ManipulationSession session )
        throws ManipulationException
    {
        final MetadataRequest req =
            new MetadataRequest( new DefaultMetadata( groupId, artifactId, "maven-metadata.xml",
                                                      Nature.RELEASE_OR_SNAPSHOT ), remote, "version-calculator" );

        req.setDeleteLocalCopyIfMissing( true );

        final List<MetadataRequest> reqs = new ArrayList<MetadataRequest>();
        reqs.add( req );

        final List<MetadataResult> mdResults =
            repositorySystem.resolveMetadata( session.getRepositorySystemSession(), reqs );

        if ( mdResults != null )
        {
            File mdFile = null;
            final MetadataXpp3Reader mdReader = new MetadataXpp3Reader();
            for ( final MetadataResult mdResult : mdResults )
            {
                Metadata metadata = mdResult.getMetadata();
                if ( metadata == null )
                {
                    metadata = mdResult.getRequest()
                                       .getMetadata();
                }

                if ( metadata == null )
                {
                    logger.error( "Cannot find metadata instance associated with MetadataResult: " + mdResult
                        + ". Skipping..." );
                    continue;
                }

                mdFile = metadata.getFile();
                if ( mdFile == null )
                {
                    final Exception exception = mdResult.getException();
                    if ( exception != null )
                    {
                        if ( logger.isDebugEnabled() )
                        {
                            logger.error( "Failed to resolve metadata: " + metadata + ". Error: "
                                              + exception.getMessage(), exception );
                        }
                        else
                        {
                            logger.error( "Failed to resolve metadata: " + metadata + ". Error: "
                                + exception.getMessage() );
                        }
                    }

                    continue;
                }

                logger.debug( "Reading: " + mdFile );
                FileInputStream stream = null;
                try
                {
                    stream = new FileInputStream( mdFile );
                    final org.apache.maven.artifact.repository.metadata.Metadata md = mdReader.read( stream );
                    final Versioning versioning = md.getVersioning();

                    if ( versioning != null )
                    {
                        logger.debug( "Got versions: " + versioning.getVersions() );
                        versions.addAll( versioning.getVersions() );
                    }
                }
                catch ( final IOException e )
                {
                    throw new ManipulationException(
                                                     "Cannot read metadata from: %s to determine last version-suffix serial number. Error: %s",
                                                     e, mdFile, e.getMessage() );
                }
                catch ( final XmlPullParserException e )
                {
                    throw new ManipulationException(
                                                     "Cannot parse metadata from: %s to determine last version-suffix serial number. Error: %s",
                                                     e, mdFile, e.getMessage() );
                }
                finally
                {
                    IOUtil.close( stream );
                }
            }
        }
    }

}
