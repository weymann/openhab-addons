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
package org.openhab.binding.eebus.internal.transport;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.items.events.AbstractItemEventSubscriber;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code eebus} Item metadata (CONCEPT.md §4.2, §4.5) to concrete Items, and bridges
 * Item state changes / commands to and from {@code UseCase} implementations.
 *
 * <p>
 * Value syntax: {@code eebus="<oh-device-id>:<UseCase>.<Datenpunkt>"} - Server-role data
 * points only (Client-role consumption uses dynamically generated Channels instead, see
 * CONCEPT.md §4.2). The {@code <oh-device-id>} prefix is the offering {@code eebus:oh-device}
 * Thing's ID (the segment after the second colon in its UID, e.g. {@code ems1} for
 * {@code eebus:oh-device:ems1}) - it disambiguates which Bridge a value belongs to when more
 * than one {@code eebus:oh-device} Bridge offers the same use case/datapoint (CONCEPT.md §4.5,
 * resolves the ambiguity previously left open in the §4.2 revision history). See CONCEPT.md
 * §4.2 for the full design, including the deliberate decision (§4.2.1/§7 item 7) against a
 * separate Rule-tag mechanism.
 * </p>
 *
 * <p>
 * This is the single OSGi {@code EventSubscriber} in this bundle (extends
 * {@link AbstractItemEventSubscriber}) — individual {@code UseCase} implementations do not
 * subscribe to the openHAB event bus themselves, they register a listener here instead via
 * {@link #registerItemStateListener(String, Consumer)}. This keeps event-bus wiring in one
 * place rather than duplicating it per use case.
 * </p>
 *
 * <p>
 * <strong>Package note:</strong> lives in {@code internal.transport} (not {@code
 * internal.handler}), even though it only touches openHAB core Item/Event APIs and never SPINE
 * itself - architecturally it is the adapter the transport-layer {@code UseCase} implementations
 * need to read/write openHAB Items, so it belongs with them rather than with the ThingHandler
 * layer. {@code internal.handler.EEBusHandlerFactory} depends on it (one-way), not the reverse.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
// 2026-08-21 fix: this component extends AbstractItemEventSubscriber specifically so the
// openHAB event bus can deliver ItemStateEvents to receiveUpdate() below (see that method's
// javadoc / the write-path Item-tag mechanism it drives). That delivery is entirely separate
// from dependency injection: OSGi Declarative Services only auto-registers a component under
// every interface it implements when @Component's own "service" attribute is left unset - once
// "service" is given explicitly (as it was here, just "EEBusMetadataService.class"), *only* the
// listed interface(s) are registered, and nothing else discovers this component at all. The
// event bus's own dispatcher looks specifically for services registered under
// org.openhab.core.events.EventSubscriber (see
// https://www.openhab.org/docs/developer/utils/events.html, canonical pattern:
// "@Component(immediate = true, service = EventSubscriber.class)") - with only
// "EEBusMetadataService.class" listed, this component was injectable everywhere it's
// "@Reference"d (EEBusHandler, EEBusDeviceActions, etc. - which is why findByTag()/find()/
// registerItemStateListener() all worked fine as plain method calls), but the event bus itself
// had no way to ever find it, so receiveUpdate() was *never once* invoked - not a timing issue,
// a complete, permanent gap, independent of (and masking) every other write-path fix made today.
// Listing both interfaces here keeps every existing "@Reference EEBusMetadataService" injection
// working unchanged while also making the event bus deliver to it.
@Component(immediate = true, service = { EEBusMetadataService.class, EventSubscriber.class })
public class EEBusMetadataService extends AbstractItemEventSubscriber {

    /** The metadata namespace used by this binding, see CONCEPT.md §4.2. */
    public static final String NAMESPACE = "eebus";

    private final Logger logger = LoggerFactory.getLogger(EEBusMetadataService.class);

    private final MetadataRegistry metadataRegistry;
    private final ItemRegistry itemRegistry;
    private final EventPublisher eventPublisher;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<State>>> itemStateListeners = new ConcurrentHashMap<>();

    @Activate
    public EEBusMetadataService(@Reference MetadataRegistry metadataRegistry, @Reference ItemRegistry itemRegistry,
            @Reference EventPublisher eventPublisher) {
        this.metadataRegistry = metadataRegistry;
        this.itemRegistry = itemRegistry;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Finds the {@code eebus} metadata entry matching the given offering service, use case, and
     * data point (CONCEPT.md §4.5). Server-role data points only - Client-role consumption uses
     * dynamically generated Channels instead (CONCEPT.md §4.2), never this method.
     *
     * <p>
     * A metadata entry in the {@value #NAMESPACE} namespace whose value has no
     * {@code <oh-device-id>:} prefix (i.e. no colon) never matches anything and is logged as a
     * warning the first time it is encountered during a lookup, since it can only be a
     * misconfiguration (CONCEPT.md §4.5 "server-metadata" delta spec, scenario "Metadata value
     * missing the oh-device-id prefix is ignored").
     * </p>
     *
     * @param ohServiceId the offering {@code eebus:oh-device} Thing's ID (the segment after the
     *            second colon in its UID, e.g. {@code ems1} for {@code eebus:oh-device:ems1})
     * @param useCase the use case short name, e.g. {@code "MPC"}
     * @param dataPoint the data point name, e.g. {@code "power"} (together, expected metadata
     *            value: {@code "<ohServiceId>:MPC.power"}, matching {@link Metadata#getValue()})
     * @return the matching metadata entry, if any (the first match if several Items happen to
     *         declare the same data point - not disambiguated further in v1)
     */
    public Optional<Metadata> find(String ohServiceId, String useCase, String dataPoint) {
        String expectedValue = ohServiceId + ":" + useCase + "." + dataPoint;
        return metadataRegistry.getAll().stream().filter(metadata -> NAMESPACE.equals(metadata.getUID().getNamespace()))
                .filter(metadata -> hasOhServiceIdPrefix(metadata, metadata.getValue()))
                .filter(metadata -> expectedValue.equals(metadata.getValue())).findFirst();
    }

    /**
     * @param metadata the metadata entry, used only for the Item name in the warning log
     * @param value the metadata value to check
     * @return {@code true} if {@code value} contains the {@code <oh-device-id>:} prefix
     *         required by {@link #find}; logs a warning and returns {@code false} otherwise
     */
    private boolean hasOhServiceIdPrefix(Metadata metadata, String value) {
        if (value.indexOf(':') >= 0) {
            return true;
        }
        logger.warn("eebus metadata value '{}' on Item '{}' has no <oh-device-id>: prefix - expected "
                + "\"<oh-device-id>:<UseCase>.<Datapoint>\", e.g. \"ems1:MPC.power\" (CONCEPT.md §4.5); ignoring",
                value, itemNameOf(metadata));
        return false;
    }

    /**
     * Finds an Item tagged for the Client-role write path (an openHAB Item feeding a value *to*
     * a paired {@code eebus:oh-entity}, e.g. an EnergyGuard-side LPC/LPP limit command - see
     * {@link org.openhab.binding.eebus.internal.transport.AbstractEEBusLimitEnergyGuardUseCase}).
     *
     * <p>
     * Unlike {@link #find}, this looks at plain Item <b>tags</b> ({@link Item#getTags()}), not
     * {@code eebus} namespace Metadata - the tag value is the full {@code oh-entity} Thing's
     * {@link ThingUID#getAsString()} plus {@code :<UseCase>:<Datapoint>}, e.g.
     * {@code "eebus:oh-entity:150d06a965:e554d8b410:LPC:limitActive"}. Using the full ThingUID
     * (copy-pasteable straight from the openHAB UI/YAML) rather than a hand-built short prefix
     * means uniqueness is guaranteed by openHAB itself - no separate disambiguation scheme is
     * needed here, unlike {@link #find}'s {@code <oh-device-id>:} prefix.
     * </p>
     *
     * @param peerUid the paired {@code eebus:oh-entity} Thing's UID
     * @param useCase the use case short name, e.g. {@code "LPC"} - matched case-insensitively
     *            against the tag (see below), so callers may pass either the EEBUS-standard
     *            uppercase form or a lowercase channel-group-id constant such as
     *            {@link org.openhab.binding.eebus.internal.EEBusBindingConstants#CHANNEL_GROUP_LPC}
     * @param dataPoint the data point name, e.g. {@code "limitActive"}
     * @return the name of the first Item carrying a matching tag, if any (not disambiguated
     *         further if several Items happen to carry the same tag - same v1 limitation as
     *         {@link #find})
     */
    public Optional<String> findByTag(ThingUID peerUid, String useCase, String dataPoint) {
        // 2026-08-21 fix: registerWriteListeners() passes getShortCode() here, which is the
        // *lowercase* channel-group-id constant (e.g. "lpc", "lpp" - see
        // EEBusBindingConstants#CHANNEL_GROUP_LPC/LPP) - but this method's own javadoc example,
        // and every screenshot/config the user has actually tagged Items with, uses the
        // EEBUS-standard *uppercase* use-case code ("LPC"). Before this fix, the exact-match
        // Set#contains() below silently never matched an uppercase-tagged Item against a
        // lowercase-built expectedTag - registerWriteListeners() then saw both Optionals empty
        // and returned without ever registering a write listener, with no error or warning
        // anywhere, regardless of how correctly the mDNS/Discovery/config layers underneath were
        // all working. Normalizing to uppercase here (matching the documented format, not the
        // internal channel-group-id casing) fixes that for MPC/LPC/LPP alike.
        String expectedTag = peerUid.getAsString() + ":" + useCase.toUpperCase(Locale.ROOT) + ":" + dataPoint;
        return itemRegistry.getItems().stream().filter(item -> item.getTags().contains(expectedTag)).map(Item::getName)
                .findFirst();
    }

    /**
     * @param itemName the Item name
     * @return the Item's current state, or empty if the Item does not exist
     */
    public Optional<State> readState(String itemName) {
        try {
            return Optional.of(itemRegistry.getItem(itemName).getState());
        } catch (ItemNotFoundException e) {
            logger.warn("eebus metadata references unknown Item '{}'", itemName);
            return Optional.empty();
        }
    }

    /**
     * Posts a command to the given Item - the write-path for Server-role data points that
     * receive a SPINE write from a peer (e.g. an LPC consumption limit).
     *
     * @param itemName the Item name
     * @param command the command to send
     */
    public void sendCommand(String itemName, Command command) {
        eventPublisher.post(ItemEventFactory.createCommandEvent(itemName, command));
    }

    /**
     * Posts a state update to the given Item - the read/subscription-path for Client-role data
     * points, i.e. a value received from a peer's SPINE feature (e.g. a remote MPC.power
     * measurement) is pushed into the local Item's state. Uses
     * {@link ItemEventFactory#createStateEvent(String, State)} (verified against openHAB Core
     * javadoc), which is distinct from {@link #sendCommand} - a received measurement is a state
     * fact, not a command to act on.
     *
     * @param itemName the Item name
     * @param state the new state to post
     */
    public void updateState(String itemName, State state) {
        eventPublisher.post(ItemEventFactory.createStateEvent(itemName, state));
    }

    /**
     * Registers a listener that is notified whenever the given Item's state changes. Used by
     * {@code UseCase} implementations to push new values into their SPINE feature's data cache
     * (see e.g. {@code EEBusMpcServerUseCase}) - SPINE itself is a pull/cache model
     * ({@code ReadListFeatureFunction} serves reads from locally held data, see
     * {@code updateData}/{@code addData}), so this binding is responsible for proactively
     * keeping that cache in sync with the mapped Item.
     *
     * @param itemName the Item name to observe
     * @param listener called with the new state on every change
     */
    public void registerItemStateListener(String itemName, Consumer<State> listener) {
        CopyOnWriteArrayList<Consumer<State>> listeners = itemStateListeners.computeIfAbsent(itemName,
                k -> new CopyOnWriteArrayList<>());
        if (listeners != null) {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered listener - {@code UseCase#close()} implementations
     * must call this to avoid leaking listeners across Bridge restarts.
     *
     * @param itemName the Item name
     * @param listener the listener instance previously passed to
     *            {@link #registerItemStateListener}
     */
    public void unregisterItemStateListener(String itemName, Consumer<State> listener) {
        CopyOnWriteArrayList<Consumer<State>> listeners = itemStateListeners.get(itemName);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    @Override
    protected void receiveUpdate(ItemStateEvent updateEvent) {
        CopyOnWriteArrayList<Consumer<State>> listeners = itemStateListeners.get(updateEvent.getItemName());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        State state = updateEvent.getItemState();
        for (Consumer<State> listener : listeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                logger.warn("eebus Item state listener for '{}' threw an exception", updateEvent.getItemName(), e);
            }
        }
    }

    /**
     * Convenience helper mirroring {@link org.openhab.core.items.MetadataKey#getItemName()} for a resolved
     * {@link Metadata}, kept here so callers don't need to import {@link org.openhab.core.items.MetadataKey}
     * separately.
     */
    public static String itemNameOf(Metadata metadata) {
        return Objects.requireNonNull(metadata.getUID().getItemName());
    }
}
