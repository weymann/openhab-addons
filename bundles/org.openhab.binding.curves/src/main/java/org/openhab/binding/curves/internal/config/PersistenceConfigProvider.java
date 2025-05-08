/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.curves.internal.config;

import static org.openhab.binding.curves.internal.CurvesConstants.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.persistence.PersistenceService;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.persistence.QueryablePersistenceService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * The {@link PersistenceConfigProvider} gets available Persistence Services as configuration options.
 *
 * @author Bernd Weymann - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = { ConfigOptionProvider.class })
@NonNullByDefault
public class PersistenceConfigProvider implements ConfigOptionProvider {

    private final PersistenceServiceRegistry persistenceServiceRegistry;
    private final LocaleProvider localeProvider;
    private final List<String> supportedURIs = List.of(HEMS_CONFIG_URI, PV_CONFIG_URI, HOUSEHOLD_CONFIG_URI,
            GRID_CONFIG_URI, BATTERY_CONFIG_URI);
    private final List<String> supportedItems = List.of(CONFIG_ITEM_DEFAULT_PERSISTENCE,
            CONFIG_ITEM_PV_POWER_PERSISTENCE, CONFIG_ITEM_PV_POWER_FORECAST_PERSISTENCE,
            CONFIG_ITEM_HOUSEHOLD_POWER_PERSISTENCE, CONFIG_ITEM_HOME2GRID_POWER_PERSISTENCE,
            CONFIG_ITEM_GRID2HOME_POWER_PERSISTENCE, CONFIG_ITEM_BATTERY_SOC_POWER_PERSISTENCE,
            CONFIG_ITEM_BATTERY_CHARGE_POWER_PERSISTENCE, CONFIG_ITEM_BATTERY_DISCHARGE_POWER_PERSISTENCE);

    @Activate
    public PersistenceConfigProvider(final @Reference PersistenceServiceRegistry persistenceServiceRegistry,
            final @Reference LocaleProvider localeProvider) {
        this.persistenceServiceRegistry = persistenceServiceRegistry;
        this.localeProvider = localeProvider;
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (supportedURIs.contains(uri.toString())) {
            if (supportedItems.contains(param)) {
                Collection<ParameterOption> options = new ArrayList<>();
                Collection<PersistenceService> services = persistenceServiceRegistry.getAll();
                services.forEach(service -> {
                    if (service instanceof QueryablePersistenceService qService) {
                        options.add(
                                new ParameterOption(qService.getId(), qService.getLabel(localeProvider.getLocale())));
                    }
                });
                return options;
            }
        }
        return null;
    }
}
