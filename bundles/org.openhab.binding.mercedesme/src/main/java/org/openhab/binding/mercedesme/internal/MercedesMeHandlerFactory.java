/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.mercedesme.internal;

import static org.openhab.binding.mercedesme.internal.Constants.*;

import java.util.Set;

import javax.measure.Unit;
import javax.measure.quantity.Length;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mercedesme.internal.discovery.MercedesMeDiscoveryService;
import org.openhab.binding.mercedesme.internal.handler.AccountHandler;
import org.openhab.binding.mercedesme.internal.handler.VehicleHandler;
import org.openhab.binding.mercedesme.internal.utils.Mapper;
import org.openhab.binding.mercedesme.internal.utils.Utils;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.i18n.UnitProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MercedesMeHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.mercedesme", service = ThingHandlerFactory.class)
public class MercedesMeHandlerFactory extends BaseThingHandlerFactory {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_BEV, THING_TYPE_COMB,
            THING_TYPE_HYBRID, THING_TYPE_ACCOUNT);

    private final Logger logger = LoggerFactory.getLogger(MercedesMeHandlerFactory.class);
    private final HttpClient httpClient;
    private final LocaleProvider localeProvider;
    private final StorageService storageService;
    private final MercedesMeDiscoveryService discoveryService;
    private final MercedesMeCommandOptionProvider mmcop;
    private final MercedesMeStateOptionProvider mmsop;
    private final MercedesMeDynamicStateDescriptionProvider mmdsdp;
    private @Nullable ServiceRegistration<?> discoveryServiceReg;

    @Activate
    public MercedesMeHandlerFactory(@Reference HttpClientFactory hcf, @Reference StorageService storageService,
            final @Reference LocaleProvider lp, final @Reference TimeZoneProvider tzp,
            final @Reference MercedesMeCommandOptionProvider cop, final @Reference MercedesMeStateOptionProvider sop,
            final @Reference MercedesMeDynamicStateDescriptionProvider dsdp, final @Reference UnitProvider up) {
        this.storageService = storageService;

        localeProvider = lp;
        mmcop = cop;
        mmsop = sop;
        mmdsdp = dsdp;

        Utils.timeZoneProvider = tzp;
        Utils.localeProvider = lp;

        // Configure Mapper default values
        Unit<Length> lengthUnit = up.getUnit(Length.class);
        if (lengthUnit.equals(ImperialUnits.FOOT)) {
            logger.debug("Switch to ImperialUnits as default");
            // switch to imperial as default
            Mapper.defaultLengthUnit = ImperialUnits.MILE;
            Mapper.defaultPressureUnit = ImperialUnits.POUND_FORCE_SQUARE_INCH;
            Mapper.defaultTemperatureUnit = ImperialUnits.FAHRENHEIT;
            Mapper.defaultVolumeUnit = ImperialUnits.GALLON_LIQUID_US;
            Mapper.defaultSpeedUnit = ImperialUnits.MILES_PER_HOUR;
        }

        httpClient = hcf.getCommonHttpClient();
        discoveryService = new MercedesMeDiscoveryService();
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            if (discoveryServiceReg == null) {
                discoveryServiceReg = bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                        null);
            }
            return new AccountHandler((Bridge) thing, discoveryService, httpClient, localeProvider, storageService);
        } else if (THING_TYPE_BEV.equals(thingTypeUID) || THING_TYPE_COMB.equals(thingTypeUID)
                || THING_TYPE_HYBRID.equals(thingTypeUID)) {
            return new VehicleHandler(thing, mmcop, mmsop, mmdsdp);
        }
        return null;
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        super.deactivate(componentContext);
        if (discoveryServiceReg != null) {
            discoveryServiceReg.unregister();
        }
    }
}
