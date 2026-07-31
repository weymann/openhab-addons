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
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_HW_DEVICE;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_CS_ENTITY;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_DEVICE;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_EG_ENTITY;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_ENTITY;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_MPC_ENTITY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.config.EEBusConfiguration;
import org.openhab.binding.eebus.internal.config.EEBusHwDeviceConfiguration;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusSkiOptionProvider} offers selectable SKI option lists for two configuration
 * parameters, instead of requiring the 40-hex-character SKI to be typed or copy-pasted:
 * {@code eebus:oh-entity}'s/{@code eebus:oh-cs-entity}'s/{@code eebus:oh-eg-entity}'s/
 * {@code eebus:oh-mpc-entity}'s {@code ski} parameter (docs/ADR/025-oh-cs-entity-static-
 * channels.md, docs/ADR/026-oh-eg-entity-static-channels.md,
 * docs/ADR/036-oh-mpc-entity-static-channels.md: all four share the exact same logic), and
 * {@code eebus:oh-device}'s {@code trustedSkis} parameter. See
 * docs/ADR/013-oh-peer-ski-config-options-provider.md for the original decision (this was the
 * first use of {@link ConfigOptionProvider} in this binding) and
 * docs/ADR/024-oh-device-oh-entity-rename.md for why it now serves two parameters instead of
 * one: trust moved from the Entity Thing to the Bridge, so the Bridge's own {@code trustedSkis}
 * now needs the same kind of selectable-list support {@code oh-peer}'s {@code ski} used to have
 * alone.
 *
 * <p>
 * <strong>eebus:oh-device {@code trustedSkis}:</strong> options are drawn from two sources, same
 * as the original {@code oh-peer} {@code ski} provider - every known {@code eebus:hw-device}
 * Thing (a real, mDNS-discovered device) and every <em>other</em> {@code eebus:oh-device}
 * Bridge's own SKI ({@code PROPERTY_LOCAL_SKI}, for trusting two local services with each other,
 * CONCEPT.md §4.4) - excluding SKIs already present in the target Bridge's current
 * {@code trustedSkis}. {@code eebus:oh-cs-device} used to be a second source/target here before
 * it was removed (docs/ADR/027-derive-local-use-cases-from-entities.md) -
 * {@code eebus:oh-device} is the only Bridge Thing type now.
 * </p>
 *
 * <p>
 * <strong>eebus:oh-entity {@code ski}:</strong> options are the target Bridge's own currently
 * configured {@code trustedSkis} - i.e. only SKIs actually usable today, since an
 * {@code eebus:oh-entity} Thing stays offline until its {@code ski} is trusted by the parent
 * Bridge anyway (see {@code EEBusOhEntityHandler#applyStatus()}). Free text entry remains
 * possible for a SKI not yet trusted (this Thing simply stays offline until it is).
 * Deliberately does <strong>not</strong> exclude a SKI already used by a sibling
 * {@code eebus:oh-entity} Thing under the same Bridge, unlike the original {@code oh-peer}
 * provider's exclusion - more than one {@code eebus:oh-entity} Thing legitimately sharing one
 * {@code ski} (multiple SPINE Entities on the same trusted device) is now the normal case, not
 * a mistake to guard against.
 * </p>
 *
 * <p>
 * <strong>Known limitation:</strong> the target Bridge can only be determined from the
 * {@code context} argument, which - per the {@link ConfigOptionProvider} contract - is not
 * guaranteed to be populated for a brand-new Thing that does not exist in the
 * {@link ThingRegistry} yet (e.g. mid-way through the "Add Thing" wizard before the Thing is
 * created). {@link #targetBridgeUid} degrades gracefully in that case: the exclusions built on
 * knowing the target Bridge are simply skipped, and the full, unscoped option list (or, for
 * {@code oh-entity}'s {@code ski}, an empty list) is returned instead of an error. Not verified
 * against an actual openHAB core build (no compiler access in the environment this class was
 * written in) - a real build should confirm this behavior.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(service = ConfigOptionProvider.class)
public class EEBusSkiOptionProvider implements ConfigOptionProvider {

    private static final String PARAM_SKI = "ski";
    private static final String PARAM_TRUSTED_SKIS = "trustedSkis";

    private final Logger logger = LoggerFactory.getLogger(EEBusSkiOptionProvider.class);

    private final ThingRegistry thingRegistry;

    @Activate
    public EEBusSkiOptionProvider(@Reference ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (!"thing-type".equals(uri.getScheme())) {
            return null;
        }
        String schemeSpecificPart = uri.getSchemeSpecificPart();

        if ((THING_TYPE_OH_ENTITY.getAsString().equals(schemeSpecificPart)
                || THING_TYPE_OH_CS_ENTITY.getAsString().equals(schemeSpecificPart)
                || THING_TYPE_OH_EG_ENTITY.getAsString().equals(schemeSpecificPart)
                || THING_TYPE_OH_MPC_ENTITY.getAsString().equals(schemeSpecificPart)) && PARAM_SKI.equals(param)) {
            // eebus:oh-entity, eebus:oh-cs-entity, eebus:oh-eg-entity and eebus:oh-mpc-entity share
            // the same ski option logic - see docs/ADR/025-oh-cs-entity-static-channels.md,
            // docs/ADR/026-oh-eg-entity-static-channels.md and
            // docs/ADR/036-oh-mpc-entity-static-channels.md.
            return trustedSkisOfTargetBridge(context);
        }
        if (THING_TYPE_OH_DEVICE.getAsString().equals(schemeSpecificPart) && PARAM_TRUSTED_SKIS.equals(param)) {
            return knownSkisExcludingAlreadyTrusted(context);
        }
        // Not our parameter - defer to static XML options / free text (per ConfigOptionProvider
        // contract, returning null here is how a provider declines to contribute).
        return null;
    }

    /**
     * @param context as passed to {@link #getParameterOptions} for an {@code eebus:oh-entity}'s
     *            {@code ski} parameter
     * @return the target Bridge's currently configured {@code trustedSkis}, labeled from a
     *         matching {@code eebus:hw-device} Thing where one exists; empty if the target
     *         Bridge cannot be determined (see class javadoc "Known limitation")
     */
    private Collection<ParameterOption> trustedSkisOfTargetBridge(@Nullable String context) {
        ThingUID targetBridgeUid = targetBridgeUid(context);
        if (targetBridgeUid == null) {
            return List.of();
        }
        Thing bridgeThing = thingRegistry.get(targetBridgeUid);
        if (!(bridgeThing instanceof Bridge)) {
            return List.of();
        }
        List<ParameterOption> options = new ArrayList<>();
        for (String ski : bridgeThing.getConfiguration().as(EEBusConfiguration.class).trustedSkis) {
            if (!ski.isBlank()) {
                options.add(new ParameterOption(ski, labelForSki(ski)));
            }
        }
        return options;
    }

    /**
     * @param ski a SKI to find a friendly label for
     * @return the label of a matching {@code eebus:hw-device} Thing (with the SKI appended), or
     *         just {@code ski} itself if none is known
     */
    private String labelForSki(String ski) {
        for (Thing candidate : thingRegistry.getAll()) {
            if (THING_TYPE_HW_DEVICE.equals(candidate.getThingTypeUID())) {
                String candidateSki = candidate.getConfiguration().as(EEBusHwDeviceConfiguration.class).ski;
                if (ski.equals(candidateSki)) {
                    String label = candidate.getLabel();
                    return label == null || label.isBlank() ? ski : label + " (" + ski + ")";
                }
            }
        }
        return ski;
    }

    /**
     * @param context as passed to {@link #getParameterOptions} for an {@code eebus:oh-device}'s
     *            {@code trustedSkis} parameter
     * @return every known {@code eebus:hw-device} Thing's SKI, plus every <em>other</em>
     *         {@code eebus:oh-device} Bridge's own SKI, minus any SKI already present in the
     *         target Bridge's current {@code trustedSkis}
     */
    private Collection<ParameterOption> knownSkisExcludingAlreadyTrusted(@Nullable String context) {
        ThingUID targetBridgeUid = targetBridgeUid(context);
        Set<String> alreadyTrusted = targetBridgeUid == null ? Set.of() : currentTrustedSkisOf(targetBridgeUid);

        List<ParameterOption> options = new ArrayList<>();
        for (Thing candidate : thingRegistry.getAll()) {
            if (THING_TYPE_HW_DEVICE.equals(candidate.getThingTypeUID())) {
                addOption(options, candidate.getConfiguration().as(EEBusHwDeviceConfiguration.class).ski,
                        candidate.getLabel(), alreadyTrusted);
            } else if (THING_TYPE_OH_DEVICE.equals(candidate.getThingTypeUID())
                    && !candidate.getUID().equals(targetBridgeUid)) {
                addOption(options, candidate.getProperties().get(PROPERTY_LOCAL_SKI), candidate.getLabel(),
                        alreadyTrusted);
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
     * @param bridgeUid UID of the target {@code eebus:oh-device} Bridge
     * @return the Bridge's own currently configured {@code trustedSkis}, as a {@link Set}; empty
     *         if {@code bridgeUid} does not resolve to a Bridge Thing
     */
    private Set<String> currentTrustedSkisOf(ThingUID bridgeUid) {
        Thing bridgeThing = thingRegistry.get(bridgeUid);
        if (!(bridgeThing instanceof Bridge)) {
            return Set.of();
        }
        Set<String> skis = new HashSet<>();
        for (String ski : bridgeThing.getConfiguration().as(EEBusConfiguration.class).trustedSkis) {
            if (!ski.isBlank()) {
                skis.add(ski);
            }
        }
        return skis;
    }

    /**
     * Resolves {@code context} to the target {@code eebus:oh-device} Bridge's UID, if possible -
     * see class javadoc "Known limitation" for when this returns {@code null} instead.
     *
     * @param context as passed to {@link #getParameterOptions}; either the Bridge's own UID
     *            (when resolving its own {@code trustedSkis} parameter, or an already-existing
     *            {@code eebus:oh-entity}'s target Bridge), the Thing UID of an already-existing
     *            {@code eebus:oh-entity} being reconfigured, or {@code null}/unparseable
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
            ThingTypeUID contextType = contextThing.getThingTypeUID();
            if (THING_TYPE_OH_DEVICE.equals(contextType)) {
                return contextUid;
            }
            if (THING_TYPE_OH_ENTITY.equals(contextType) || THING_TYPE_OH_CS_ENTITY.equals(contextType)
                    || THING_TYPE_OH_EG_ENTITY.equals(contextType) || THING_TYPE_OH_MPC_ENTITY.equals(contextType)) {
                return contextThing.getBridgeUID();
            }
        } catch (IllegalArgumentException e) {
            logger.debug("Could not parse ConfigOptionProvider context '{}' as a ThingUID: {}", context,
                    e.getMessage());
        }
        return null;
    }
}
