/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.internal.discovery;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.codehaus.plexus.util.IOUtil;
import org.eclipse.equinox.internal.p2.discovery.Catalog;
import org.eclipse.equinox.internal.p2.discovery.DiscoveryCore;
import org.eclipse.equinox.internal.p2.discovery.compatibility.RemoteBundleDiscoveryStrategy;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.internal.p2.discovery.model.Tag;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.m2e.core.internal.lifecyclemapping.LifecycleMappingFactory;
import org.eclipse.m2e.core.internal.lifecyclemapping.model.LifecycleMappingMetadataSource;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.eclipse.m2e.internal.discovery.wizards.MavenCatalogConfiguration;
import org.eclipse.m2e.internal.discovery.wizards.MavenCatalogViewer;
import org.eclipse.m2e.internal.discovery.wizards.MavenDiscoveryWizard;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.Workbench;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings("restriction")
public class MavenDiscovery {
  private static final Logger log = LoggerFactory.getLogger(MavenDiscovery.class);

  public static final Tag NO_RESTART_TAG = new Tag("norestart", "norestart"); //$NON-NLS-1$//$NON-NLS-2$

  public static final Tag APPLICABLE_TAG = new Tag("applicable", Messages.MavenDiscovery_Wizard_Applicable_Tag); //$NON-NLS-1$

  private static final Tag EXTRAS_TAG = new Tag("extras", Messages.MavenDiscovery_Wizard_ExtrasTag); //$NON-NLS-1$

  private static final Tag LIFECYCLES_TAG = new Tag("lifecycles", Messages.MavenDiscovery_Wizard_LifecyclesTag); //$NON-NLS-1$

  private static final Tag MAVEN_TAG = new Tag("maven", Messages.MavenDiscovery_Wizard_MavenTag); //$NON-NLS-1$

  private static final String PATH = "http://desktop.ifedorenko.com/discovery.xml"; //$NON-NLS-1$

  public static void launchWizard(Shell shell) {
    launchWizard(shell, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
  }

  public static void launchWizard(final Collection<String> packagingTypes, final Collection<MojoExecutionKey> mojos,
      final Collection<String> lifecycleIds, final Collection<String> configuratorIds) {
    final Display display = Workbench.getInstance().getDisplay();
    display.asyncExec(new Runnable() {
      public void run() {
        launchWizard(display.getActiveShell(), packagingTypes, mojos, lifecycleIds, configuratorIds);
      }
    });
  }

  public static void launchWizard(Shell shell, Collection<String> packagingTypes, Collection<MojoExecutionKey> mojos,
      Collection<String> lifecycleIds, Collection<String> configuratorIds) {
    Catalog catalog = getCatalog();

    // Build the list of tags to show in the Wizard header
    List<Tag> tags = new ArrayList<Tag>(3);
    if(!packagingTypes.isEmpty() || !mojos.isEmpty() || !configuratorIds.isEmpty() || !lifecycleIds.isEmpty()) {
      tags.add(APPLICABLE_TAG);
    }
    tags.add(EXTRAS_TAG);
    tags.add(LIFECYCLES_TAG);
    tags.add(MAVEN_TAG);
    catalog.setTags(tags);

    // Create configuration for the catalog
    MavenCatalogConfiguration configuration = new MavenCatalogConfiguration();
    configuration.setShowTagFilter(true);
    if(!packagingTypes.isEmpty() || !mojos.isEmpty() || !configuratorIds.isEmpty() || !lifecycleIds.isEmpty()) {
      tags = new ArrayList<Tag>(1);
      tags.add(APPLICABLE_TAG);
      configuration.setSelectedTags(tags);
    } else {
      configuration.setSelectedTags(tags);
    }
    configuration.setShowInstalledFilter(false);
    configuration.setSelectedPackagingTypes(packagingTypes);
    configuration.setSelectedMojos(mojos);
    configuration.setSelectedLifecycleIds(lifecycleIds);
    configuration.setSelectedConfigurators(configuratorIds);

    MavenDiscoveryWizard wizard = new MavenDiscoveryWizard(catalog, configuration);
    WizardDialog dialog = new WizardDialog(shell, wizard);
    dialog.open();
  }

  public static Catalog getCatalog() {
    Catalog catalog = new Catalog();
    catalog.setEnvironment(DiscoveryCore.createEnvironment());
    catalog.setVerifyUpdateSiteAvailability(false);

    // look for remote descriptor
    RemoteBundleDiscoveryStrategy remoteDiscoveryStrategy = new RemoteBundleDiscoveryStrategy();
    remoteDiscoveryStrategy.setDirectoryUrl(PATH);
    catalog.getDiscoveryStrategies().add(remoteDiscoveryStrategy);
    return catalog;
  }

  public static LifecycleMappingMetadataSource getLifecycleMappingMetadataSource(CatalogItem ci) {
    try {
      URL url = MavenCatalogViewer.getLifecycleMappingMetadataSourceURL(ci);
      InputStream is = url.openStream();
      try {
        return LifecycleMappingFactory.createLifecycleMappingMetadataSource(is);
      } finally {
        IOUtil.close(is);
      }
    } catch(FileNotFoundException e) {
      log.warn("CatalogItem {} does not contain lifecycle mapping metadata", ci.getId()); //$NON-NLS-1$
    } catch(Exception e) {
      log.warn(NLS.bind(Messages.MavenCatalogViewer_Error_loading_lifecycle, ci.getId()), e);
    }
    return null;
  }

  /*
   * Restart is required when one or more CatalogItem lacks the norestart tag.
   */
  public static boolean requireRestart(Iterable<CatalogItem> catalogItems) {
    for(CatalogItem item : catalogItems) {
      if(!item.hasTag(NO_RESTART_TAG)) {
        return true;
      }
    }
    return false;
  }
}
