/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.eebus.internal.handler;

import static org.openhab.binding.eebus.internal.EEBusBindingConstants.PROPERTY_LOCAL_SKI;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_PEER;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_PEER;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_SERVICE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.config.EEBusOhPeerConfiguration;
import org.openhab.binding.eebus.internal.config.EEBusPeerConfiguration;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusOhPeerSkiOptionProvider} offers the {@code ski} configuration parameter of
 * {@code eebus:oh-peer} as a selectable option list, instead of requiring the 40-hex-character
 * SKI to be typed or copy-pasted. See docs/ADR/013-oh-peer-ski-config-options-provider.md for
 * the full decision; this is the first use of {@link ConfigOptionProvider} in this binding.
 *
 * <p>
 * Options are drawn from two sources:
 * </p>
 * <ul>
 * <li>every known {@code eebus:peer} Thing (a real, mDNS-discovered device) - label and SKI;</li>
 * <li>every other {@code eebus:service} Bridge's own SKI ({@code PROPERTY_LOCAL_SKI}) - for
 * pairing two local services with each other (CONCEPT.md §4.4), which normal mDNS-based Inbox
 * discovery never surfaces (ADR-008's {@code isOwnService} filter excludes it deliberately).</li>
 * </ul>
 *
 * <p>
 * SKIs already belonging to a sibling {@code eebus:oh-peer} Thing under the same target
 * {@code eebus:service} Bridge are excluded (avoids offering a duplicate pairing); SKIs already
 * paired under a <em>different</em> Bridge remain offered, since a device or service may
 * legitimately be paired with more than one local service at once.
 * </p>
 *
 * <p>
 * <strong>Known limitation:</strong> the target Bridge can only be determined from the
 * {@code context} argument, which - per the {@link ConfigOptionProvider} contract - is not
 * guaranteed to be populated for a brand-new Thing that does not exist in the
 * {@link ThingRegistry} yet (e.g. mid-way through the "Add Thing" wizard before the Thing is
 * created). {@link #targetBridgeUid} degrades gracefully in that case: the "already paired
 * under the target Bridge" exclusion and the "exclude the target Bridge's own identity"
 * exclusion are simply skipped, and the full, unscoped option list is returned instead of an
 * error. Not verified against an actual openHAB core build - see
 * {@code EEBusOhPeerActions}'s class javadoc for the same caveat.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(service = ConfigOptionProvider.class)
public class EEBusOhPeerSkiOptionProvider implements ConfigOptionProvider {

    private static final String PARAM_SKI = "ski";

    private final Logger logger = LoggerFactory.getLogger(EEBusOhPeerSkiOptionProvider.class);

    private final ThingRegistry thingRegistry;

    @Activate
    public EEBusOhPeerSkiOptionProvider(@Reference ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        boolean isOhPeerSkiUri = "thing-type".equals(uri.getScheme())
                && THING_TYPE_OH_PEER.getAsString().equals(uri.getSchemeSpecificPart());
        if (!isOhPeerSkiUri || !PARAM_SKI.equals(param)) {
            // Not our parameter - defer to static XML options / free text (per
            // ConfigOptionProvider contract, returning null here is how a provider declines to
            // contribute).
            return null;
        }

        ThingUID targetBridgeUid = targetBridgeUid(context);
        Set<String> alreadyPairedUnderTarget = targetBridgeUid == null ? Set.of()
                : skisAlreadyPairedUnder(targetBridgeUid);

        List<ParameterOption> options = new ArrayList<>();
        for (Thing candidate : thingRegistry.getAll()) {
            if (THING_TYPE_PEER.equals(candidate.getThingTypeUID())) {
                addOption(options, candidate.getConfiguration().as(EEBusPeerConfiguration.class).ski,
                        candidate.getLabel(), alreadyPairedUnderTarget);
            } else if (THING_TYPE_SERVICE.equals(candidate.getThingTypeUID())
                    && !candidate.getUID().equals(targetBridgeUid)) {
                addOption(options, candidate.getProperties().get(PROPERTY_LOCAL_SKI), candidate.getLabel(),
                        alreadyPairedUnderTarget);
            }
        }
        return options;
    }

    private void addOption(List<ParameterOption> options, @Nullable String ski, @Nullable String label,
            Set<String> excluded) {
        if (ski == null || ski.isBlank() || excluded.contains(ski)) {
            return;
        }
        String resolvedLabel = label == null || label.isBlank() ? ski : label + " (" + ski + ")";
        options.add(new ParameterOption(ski, resolvedLabel));
    }

    /**
     * @param bridgeUid UID of the target {@code eebus:service} Bridge
     * @return the {@code ski} of every child {@code eebus:oh-peer} Thing currently configured
     *         under {@code bridgeUid}, regardless of whether it is actually paired yet - this
     *         method is about avoiding a duplicate <em>configuration</em>, not about trust state
     *         (see docs/changes/decouple-oh-peer-config-from-pairing/specs/thing-model/spec.md,
     *         Scenario "Options exclude a SKI already paired under the target service")
     */
    private Set<String> skisAlreadyPairedUnder(ThingUID bridgeUid) {
        Thing bridgeThing = thingRegistry.get(bridgeUid);
        if (!(bridgeThing instanceof Bridge bridge)) {
            return Set.of();
        }
        Set<String> skis = new HashSet<>();
        for (Thing child : bridge.getThings()) {
            if (THING_TYPE_OH_PEER.equals(child.getThingTypeUID())) {
                String ski = child.getConfiguration().as(EEBusOhPeerConfiguration.class).ski;
                if (!ski.isBlank()) {
                    skis.add(ski);
                }
            }
        }
        return skis;
    }

    /**
     * Resolves {@code context} to the target {@code eebus:service} Bridge's UID, if possible -
     * see class javadoc "Known limitation" for when this returns {@code null} instead.
     *
     * @param context as passed to {@link #getParameterOptions}; either the Thing UID of an
     *            already-existing {@code eebus:oh-peer} being reconfigured, the target
     *            {@code eebus:service} Bridge's own UID, or {@code null}/unparseable
     * @return the target Bridge's UID, or {@code null} if it could not be determined
     */
    private @Nullable ThingUID targetBridgeUid(@Nullable String context) {
        if (context == null || context.isBlank()) {
            return null;
        }
        try {
            ThingUID contextUid = new ThingUID(context);
            Thing contextThing = thingRegistry.get(contextUid);
            if (contextThing == null) {
                return null;
            }
            if (THING_TYPE_SERVICE.equals(contextThing.getThingTypeUID())) {
                return contextUid;
            }
            if (THING_TYPE_OH_PEER.equals(contextThing.getThingTypeUID())) {
                return contextThing.getBridgeUID();
            }
        } catch (IllegalArgumentException e) {
            logger.debug("Could not parse ConfigOptionProvider context '{}' as a ThingUID: {}", context,
                    e.getMessage());
        }
        return null;
    }
}
