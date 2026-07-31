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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.config.EEBusConfiguration;
import org.openhab.binding.eebus.internal.config.EEBusOhEntityConfiguration;
import org.openhab.binding.eebus.internal.transport.EEBusLimitControlState;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusOhEntityHandler} represents exactly one SPINE Entity on a device trusted by
 * the parent Bridge ({@code eebus:oh-entity} Thing), identified by the device's SKI and this
 * Entity's address.
 *
 * <p>
 * <strong>Also serves {@code eebus:oh-cs-entity}</strong> (docs/ADR/025-oh-cs-entity-static-
 * channels.md), the exclusive Controllable System counterpart used under {@code eebus:oh-cs-
 * device} - same config class ({@link EEBusOhEntityConfiguration}), same trust-status
 * derivation, same {@link #applyLimitStatus}/{@link #applyFailsafeStatus} methods. The only
 * difference is structural, in thing-types.xml: {@code eebus:oh-cs-entity} declares its
 * {@code lpc}/{@code lpp} Channel Groups statically (via {@code <channel-groups>}), so by the
 * time {@link #ensureChannel} is called for one of its Channels, the Channel already exists and
 * {@link #ensureChannel}'s creation path is simply a no-op - no separate handler class or
 * type-specific branch needed here.
 * </p>
 *
 * <p>
 * <strong>Also serves {@code eebus:oh-eg-entity}</strong> (docs/ADR/026-oh-eg-entity-static-
 * channels.md), an alternative Entity type under {@code eebus:oh-device} intended for the
 * EnergyGuard (LPC/LPP Client role) case - same config class, same trust-status derivation, same
 * {@link #applyLimitStatus}/{@link #applyFailsafeStatus} methods, same statically-declared-
 * Channels/{@link #ensureChannel}-no-op mechanism as {@code eebus:oh-cs-entity} above. Unlike
 * {@code eebus:oh-cs-entity}, it is not exclusive under its parent Bridge - {@code eebus:oh-
 * device} remains generic/checkbox-driven, so {@code eebus:oh-entity} is equally valid there for
 * any other/mixed use-case combination, and these statically-declared Channels only populate
 * once the Bridge's {@code supportedUseCasesClient} config actually includes LPC/LPP.
 * </p>
 *
 * <p>
 * <strong>Also serves {@code eebus:oh-mpc-entity}</strong> (docs/ADR/036-oh-mpc-entity-static-
 * channels.md), an alternative Entity type under {@code eebus:oh-device} dedicated to the MPC
 * Client role (the receiving side that reads a paired peer's total power measurement) - same
 * config class, same trust-status derivation, same {@link #applyMpcPower} method, same
 * statically-declared-Channel/{@link #ensureChannel}-no-op mechanism as {@code eebus:oh-cs-
 * entity} above. Not exclusive, same as {@code eebus:oh-eg-entity}: {@code eebus:oh-entity}
 * remains equally valid under {@code eebus:oh-device} for any other/mixed use-case combination.
 * Unlike {@code eebus:oh-eg-entity}, this Thing's Channel is always statically present - its
 * mere presence unconditionally activates MPC Client (docs/ADR/027-derive-local-use-cases-from-
 * entities.md), no checkbox to forget.
 * </p>
 *
 * <p>
 * <strong>Trust model (revised, docs/ADR/024-oh-device-oh-entity-rename.md, superseding
 * docs/ADR/012-pairing-trust-property-and-actions.md; Bridge Actions removed by
 * docs/ADR/027-derive-local-use-cases-from-entities.md Decision 6):</strong> trust is not this
 * Thing's own concern at all. {@link EEBusOhEntityConfiguration#ski} only identifies which
 * already-trusted device this Entity lives on; whether that device is actually trusted lives
 * entirely on the parent {@link EEBusHandler} Bridge's {@code trustedSkis} Thing config. This
 * Thing's status simply reflects that Bridge-level decision - see {@link #applyStatus()} and
 * {@link EEBusHandler#isTrusted(String)}. There is no {@code pair()}/{@code unpair()} Thing
 * Action, and no per-Thing trust property, on this class any more; nor does the parent Bridge
 * expose a live {@code trust()}/{@code untrust()} Thing Action any more - a trust change is
 * edited as Thing config like any other, and picked up by the uniform full-rebuild path ADR-027
 * introduced.
 * </p>
 *
 * <p>
 * <strong>Origin (CONCEPT.md §4.5, §7(15)/ADR-020, re-scoped by docs/ADR/024):</strong> split out
 * of the former single {@code eebus:peer} Thing type - this class takes over the (now
 * Entity-scoped) channel/metadata responsibilities previously described for {@code eebus:peer}
 * when configured under {@code eebus:oh-device}. The passive, real-device-record half of that
 * former type lives in {@link EEBusHwDeviceHandler} ({@code eebus:hw-device}, bridgeless). This
 * class itself was originally {@code EEBusOhPeerHandler} and represented an entire paired
 * device; docs/ADR/024-oh-device-oh-entity-rename.md re-scoped it to one SPINE Entity instead.
 * </p>
 *
 * <p>
 * <strong>Architecture note (carried over from the pre-split {@code EEBusPeerHandler}):</strong>
 * Client-role use-case detection ({@code NodeManagement#addUseCaseListener(...)}) does
 * <em>not</em> live here per-Entity. SPINE ties {@code addUseCaseListener} to the local
 * {@code CEM} entity's UseCase list (added once via {@code Device.getBuilder()...withUseCases(...)},
 * exactly like Server-role use cases - verified against jeebus.spine's demo
 * {@code ExampleUseCase}), not to an individual peer. The callback receives all matching
 * {@code UseCasePartner}s across every trusted device at once, so registration and per-Entity
 * routing both happen centrally in {@link EEBusHandler#startShipSpine} (see
 * {@code EEBusMpcClientUseCase} for the first implementation, CONCEPT.md §7.3/§8). This
 * Thing/handler stays a thin per-Entity status holder; it does not itself talk to jeebus.spine.
 * </p>
 *
 * <p>
 * <strong>Dynamic Client-role Channels (docs/ADR/014-dynamic-client-role-channels.md,
 * CONCEPT.md §4.2.2):</strong> once a Client-role {@code UseCase} (e.g. {@code
 * EEBusMpcClientUseCase}) detects and resolves a value for this Entity, it calls a use-case-
 * specific method here (e.g. {@link #applyMpcPower(double)}) that creates the corresponding
 * Channel on first use via {@link #ensureChannel} - idempotent, safe to call on every
 * notification - and updates its state. Created Channels are never removed by a trust change or
 * connectivity loss; they retain their last known state (CONCEPT.md §4.2.2 "Channels bleiben
 * bestehen").
 * </p>
 * <p>
 * <strong>Resolved</strong> (CONCEPT.md §7 items 2 and 9): {@code UseCasePartner
 * #getCommunicationAddress()} is confirmed to be an {@code "ip:port"} string, not the SKI.
 * {@link org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser} (owned by the parent
 * {@link EEBusHandler}, reachable via {@link EEBusHandler#getMdnsBrowser()}) maintains the
 * matching {@code communicationAddress -> SKI} map by browsing {@code _ship._tcp.local.}
 * directly; {@link EEBusHandler#ohEntityHandlerForSki} completes the chain to this Thing's
 * handler.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusOhEntityHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EEBusOhEntityHandler.class);

    private @Nullable EEBusOhEntityConfiguration config;
    /** Set by {@link #dispose()}: late callbacks from use cases must not trigger channels of a disposed handler. */
    private volatile boolean disposed;

    /**
     * Last state sent via {@link #updateState(ChannelUID, State)} for each dynamically created
     * Client-role Channel ({@link #applyMpcPower}, {@link #applyLimitStatus}), keyed by
     * {@link ChannelUID}. These Channels only ever get a fresh push when SPINE actually notifies
     * a new/changed value (event-driven, no polling) - an Item linked *after* the last such push
     * (or an openHAB restart) would otherwise stay at {@code NULL} indefinitely, since
     * {@link #handleCommand} previously ignored {@link RefreshType} entirely. Populated by
     * {@link #updateCachedState}, replayed on {@link RefreshType} in {@link #handleCommand}.
     */
    private final Map<ChannelUID, State> lastKnownState = new ConcurrentHashMap<>();

    /**
     * Energy Guard (Client role) write hook, see {@link #registerLimitChannelWriter}: receives the
     * last commanded states of {@code limit-active}/{@code limit-value}/{@code limit-duration} of
     * one Channel Group ({@code lpc}/{@code lpp}) whenever any of the three is commanded.
     */
    @FunctionalInterface
    public interface LimitChannelWriter {
        void write(@Nullable State active, @Nullable State value, @Nullable State duration);
    }

    private final Map<String, LimitChannelWriter> limitChannelWriters = new ConcurrentHashMap<>();

    /**
     * Registers the write hook for the given Channel Group ({@code lpc}/{@code lpp}) of this
     * Energy Guard Thing. Called by the Client-role LPC/LPP use case.
     */
    public void registerLimitChannelWriter(String groupId, LimitChannelWriter writer) {
        limitChannelWriters.put(groupId, writer);
    }

    public void unregisterLimitChannelWriter(String groupId, LimitChannelWriter writer) {
        limitChannelWriters.remove(groupId, writer);
    }

    public EEBusOhEntityHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        this.disposed = false;
        EEBusOhEntityConfiguration cfg = getConfigAs(EEBusOhEntityConfiguration.class);
        this.config = cfg;

        if (!ensureSkiConfigured(cfg)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "ski not set - auto-selected once the parent Bridge trusts exactly one device, otherwise pick one manually from its Trusted SKIs");
            return;
        }

        // Configuring/creating this Thing never grants trust by itself - see class javadoc and
        // EEBusHandler#isTrusted(String). Use-case detection itself is wired centrally in
        // EEBusHandler#startShipSpine, not here - see class javadoc "Architecture note".
        logger.debug("EEBus OH entity '{}' configured with SKI {}", thing.getUID(), cfg.ski);

        applyStatus();
        notifyBridgeOfChange();
    }

    @Override
    public void dispose() {
        this.disposed = true;
        // See docs/ADR/027-derive-local-use-cases-from-entities.md: every child lifecycle event -
        // added, removed, or reconfigured (openHAB always runs dispose() before the matching
        // initialize() on a config change) - triggers a full rebuild of the parent Bridge's local
        // SPINE Device, so its derived Use-Case set never goes stale. No local state to tear down
        // here beyond this notification - this Thing itself owns no SHIP/SPINE resources (see
        // class javadoc "Architecture note").
        notifyBridgeOfChange();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        // Retries auto-assignment (see #ensureSkiConfigured) on every Bridge status change, not
        // just once at #initialize(): the Bridge may not have trusted any device yet when this
        // Thing was first added (ski left blank, config parameter made optional for exactly this
        // reason), and may later settle on exactly one trusted SKI once the user grants trust -
        // this is the only other point in this handler's lifecycle where that could happen
        // without the user reconfiguring this Thing itself.
        EEBusOhEntityConfiguration cfg = this.config;
        if (cfg != null && !ensureSkiConfigured(cfg)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "ski not set - auto-selected once the parent Bridge trusts exactly one device, otherwise pick one manually from its Trusted SKIs");
            return;
        }
        applyStatus();
    }

    /**
     * Notifies the parent Bridge handler (if any, and if it implements
     * {@link EEBusEntityChangeListener}) that this Entity Thing changed - see
     * docs/ADR/027-derive-local-use-cases-from-entities.md. Called from both
     * {@link #initialize()} and {@link #dispose()}, i.e. on every add/remove/reconfigure of this
     * Thing. A missing or not-yet-handled Bridge is silently ignored - e.g. during openHAB
     * startup ordering, or after this Thing's own removal once the Bridge link is already gone.
     */
    private void notifyBridgeOfChange() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof EEBusEntityChangeListener listener) {
            listener.onEntityChanged();
        }
    }

    /**
     * Derives this Thing's status from the parent Bridge's status and trust decision:
     * {@code OFFLINE}/{@code BRIDGE_OFFLINE} if the Bridge is not online,
     * {@code OFFLINE}/{@code CONFIGURATION_PENDING} if the Bridge is online but does not
     * currently trust this Thing's configured {@link EEBusOhEntityConfiguration#ski}
     * ({@link EEBusHandler#isTrusted(String)}), otherwise {@code ONLINE}.
     *
     * <p>
     * Package-private (not {@code private}): called from this Thing's own lifecycle
     * ({@link #initialize()}, {@link #bridgeStatusChanged}). A trust change made via the parent
     * Bridge's {@code trustedSkis} Thing config is picked up the same way any other Bridge
     * config change is - the uniform full-rebuild path (docs/ADR/027-derive-local-use-cases-
     * from-entities.md) disposes/reinitializes the Bridge, which cascades a standard
     * {@link #bridgeStatusChanged} call back to this Thing, re-running this method - no direct
     * Bridge-to-child call is needed any more (there used to be one, from the now-removed
     * {@code recomputeTrustedSkis()}).
     * </p>
     */
    void applyStatus() {
        Bridge bridge = getBridge();
        if (bridge == null || bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is not online");
            return;
        }
        EEBusOhEntityConfiguration cfg = this.config;
        String ski = cfg == null ? "" : cfg.ski;
        boolean trusted = bridge.getHandler() instanceof EEBusHandler bridgeHandler && bridgeHandler.isTrusted(ski);
        if (!trusted) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "SKI not yet trusted by the parent Bridge - see its Trusted SKIs config");
            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * If {@link EEBusOhEntityConfiguration#ski} is not yet configured, tries to auto-assign it
     * from the parent Bridge's currently configured {@code trustedSkis} Thing config: if the
     * Bridge trusts exactly one device, there is nothing to choose between, so this Thing can be
     * pointed at it without asking the user. This is why {@code ski} is an <em>optional</em>
     * config parameter (not required) as of this change - the "Add Thing" wizard cannot offer a
     * working scoped dropdown for a Thing that does not exist yet
     * ({@link EEBusSkiOptionProvider}'s class javadoc, "Known limitation": {@code context} never
     * carries a per-instance Bridge UID), so this handler resolves the common single-device case
     * itself once the Thing actually exists and is attached to its Bridge, instead of blocking
     * Thing creation on a dropdown that would always be empty.
     *
     * <p>
     * Reads the Bridge Thing's configuration directly (like
     * {@link EEBusSkiOptionProvider#trustedSkisOfTargetBridge}) rather than going through
     * {@link EEBusHandler}, since the Bridge Thing's config exists as soon as the Bridge Thing
     * does, independent of whether its handler has finished {@link EEBusHandler#initialize()}
     * yet - important because this Entity's own {@link #initialize()} is not guaranteed to run
     * after the Bridge's.
     * </p>
     *
     * <p>
     * Deliberately does <strong>not</strong> auto-assign when the Bridge currently trusts zero
     * or more than one SKI: zero means there is nothing to pick yet, more than one means a real
     * choice exists that only the user can make. Called again from {@link #bridgeStatusChanged}
     * on every Bridge status change, so a Bridge that starts with zero (or several) trusted SKIs
     * and later settles on exactly one still gets auto-assigned without the user having to
     * reconfigure this Thing.
     * </p>
     *
     * @param cfg this Thing's current configuration, as resolved by {@link #initialize()}
     * @return {@code true} if {@code cfg.ski} is non-blank after this call (whether it already
     *         was, or was just auto-assigned and persisted via
     *         {@link #updateConfiguration(Configuration)}); {@code false} if it is still blank
     */
    private boolean ensureSkiConfigured(EEBusOhEntityConfiguration cfg) {
        if (!cfg.ski.isBlank()) {
            return true;
        }
        Bridge bridge = getBridge();
        if (bridge == null) {
            return false;
        }
        List<String> trusted = new ArrayList<>();
        for (String ski : bridge.getConfiguration().as(EEBusConfiguration.class).trustedSkis) {
            if (!ski.isBlank()) {
                trusted.add(ski);
            }
        }
        if (trusted.size() != 1) {
            return false;
        }
        String ski = trusted.get(0);
        cfg.ski = ski;
        Configuration configuration = editConfiguration();
        configuration.put("ski", ski);
        updateConfiguration(configuration);
        logger.info("EEBus OH entity '{}': auto-selected SKI {} - the parent Bridge '{}' trusts exactly one device",
                thing.getUID(), ski, bridge.getUID());
        return true;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Every dynamically created Client-role Channel so far (mpc#power, lpc/lpp#limit-active,
        // lpc/lpp#limit-value) is read-only - see class javadoc "Dynamic Client-role Channels".
        // The only Command handled is REFRESH: resend the last known value (see
        // #lastKnownState), so an Item linked after the last SPINE-driven push isn't stuck at
        // NULL until the peer's underlying value happens to change again.
        if (command instanceof RefreshType) {
            State cached = lastKnownState.get(channelUID);
            if (cached != null) {
                updateState(channelUID, cached);
            }
            return;
        }
        handleLimitChannelCommand(channelUID, command);
    }

    /**
     * Energy Guard (value provider) write path via Channels: a command on an lpc/lpp
     * {@code limit-active}/{@code limit-value}/{@code limit-duration} Channel is remembered, echoed
     * back as the Channel state, and handed - together with the last commanded value of the other
     * two - to the registered {@link LimitChannelWriter} of that group, which sends one combined
     * SPINE write to every bound partner. The Controllable System's variants of these Channels are
     * read-only, so openHAB never sends commands for them.
     */
    private void handleLimitChannelCommand(ChannelUID channelUID, Command command) {
        String group = channelUID.getGroupId();
        String id = channelUID.getIdWithoutGroup();
        if (group == null
                || !(EEBusBindingConstants.CHANNEL_GROUP_LPC.equals(group)
                        || EEBusBindingConstants.CHANNEL_GROUP_LPP.equals(group))
                || !(EEBusBindingConstants.CHANNEL_LIMIT_ACTIVE.equals(id)
                        || EEBusBindingConstants.CHANNEL_LIMIT_VALUE.equals(id)
                        || EEBusBindingConstants.CHANNEL_LIMIT_DURATION.equals(id))
                || !(command instanceof State state)) {
            return;
        }
        lastKnownState.put(channelUID, state);
        updateState(channelUID, state);
        LimitChannelWriter writer = limitChannelWriters.get(group);
        if (writer == null) {
            logger.debug("{}: limit command on {} received, but no LPC/LPP Client use case is registered "
                    + "(no partner bound yet) - value remembered only", thing.getUID(), channelUID);
            return;
        }
        ThingUID uid = thing.getUID();
        writer.write(lastKnownState.get(new ChannelUID(uid, group, EEBusBindingConstants.CHANNEL_LIMIT_ACTIVE)),
                lastKnownState.get(new ChannelUID(uid, group, EEBusBindingConstants.CHANNEL_LIMIT_VALUE)),
                lastKnownState.get(new ChannelUID(uid, group, EEBusBindingConstants.CHANNEL_LIMIT_DURATION)));
    }

    /**
     * Creates the {@code mpc#power} Channel on first call (idempotent, see {@link #ensureChannel})
     * and updates its state to {@code watts}. Called from {@code EEBusMpcClientUseCase} on every
     * resolved/notified MPC Scenario 1 measurement - see docs/ADR/014-dynamic-client-role-
     * channels.md. Kept as its own method, unchanged signature, rather than folded into
     * {@link #applyMpcMeasurement} at every call site: {@code power} is the one mandatory MPC
     * data point, and this exact signature is what {@code EEBusOhEntityHandlerTest}'s existing
     * tests call (docs/ADR/037-mpc-additional-datapoints.md) - it now simply delegates.
     *
     * @param watts the peer's total active power, in Watts
     */
    public void applyMpcPower(double watts) {
        applyMpcMeasurement(EEBusBindingConstants.CHANNEL_MPC_POWER, EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_POWER,
                "Number:Power", "Power", watts, Units.WATT);
    }

    /**
     * Generic counterpart of {@link #applyMpcPower} for the twelve additional MPC Client-role
     * data points (docs/ADR/037-mpc-additional-datapoints.md) - creates the given {@code mpc}
     * Channel on first call (idempotent, see {@link #ensureChannel}) and updates its state.
     * Called from {@code EEBusMpcClientUseCase} on every resolved/notified measurement whose id
     * matched one of the additional data points; {@link #applyMpcPower} delegates here too, for
     * {@code power} itself.
     *
     * @param channelId the Channel ID within {@link EEBusBindingConstants#CHANNEL_GROUP_MPC}, e.g.
     *            {@link EEBusBindingConstants#CHANNEL_MPC_CURRENT_PHASE_A}
     * @param channelTypeUid the matching {@code channel-type} declared in thing-types.xml
     * @param acceptedItemType the accepted item type, e.g. {@code "Number:ElectricCurrent"}
     * @param label the Channel's label
     * @param value the peer's reported value, in {@code unit}
     * @param unit the physical unit {@code value} is expressed in
     */
    public void applyMpcMeasurement(String channelId, ChannelTypeUID channelTypeUid, String acceptedItemType,
            String label, double value, Unit<?> unit) {
        ChannelUID channelUID = new ChannelUID(thing.getUID(), EEBusBindingConstants.CHANNEL_GROUP_MPC, channelId);
        ensureChannel(channelUID, channelTypeUid, acceptedItemType, label);
        updateCachedState(channelUID, new QuantityType<>(value, unit));
    }

    /**
     * Creates the {@code mgcp#total-active-power} Channel on first call (idempotent, see
     * {@link #ensureChannel}) and updates its state to {@code watts}. Called from
     * {@code EEBusMgcpClientUseCase} on every resolved/notified MGCP Scenario 2 measurement - see
     * docs/ADR/040-mgcp-client-usecase.md Decision 4. Deliberately a fixed-signature sibling of
     * {@link #applyMpcPower} rather than reusing the generic {@link #applyMpcMeasurement} shape:
     * this MVP has exactly one MGCP data point, so a generic multi-parameter method would add
     * indirection without a second caller to justify it (see
     * docs/changes/mgcp-client-usecase/proposal.md "Open Questions").
     *
     * @param watts the peer's total active power at the Grid Connection Point, in Watts - follows
     *            the same load convention {@link #applyMpcPower}'s value already does (positive =
     *            consumption, negative = feed-in)
     */
    public void applyMgcpMeasurement(double watts) {
        ChannelUID channelUID = new ChannelUID(thing.getUID(), EEBusBindingConstants.CHANNEL_GROUP_MGCP,
                EEBusBindingConstants.CHANNEL_MGCP_TOTAL_ACTIVE_POWER);
        ensureChannel(channelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_MGCP_TOTAL_ACTIVE_POWER, "Number:Power",
                "Total Active Power");
        updateCachedState(channelUID, new QuantityType<>(watts, Units.WATT));
    }

    /**
     * Creates (if needed) and updates the {@code limit-active}/{@code limit-value}/
     * {@code limit-duration} Channels under {@code channelGroup} (either
     * {@link EEBusBindingConstants#CHANNEL_GROUP_LPC} or
     * {@link EEBusBindingConstants#CHANNEL_GROUP_LPP} - LPC/LPP are structurally identical, so
     * one method serves both, per docs/ADR/015-lpc-lpp-client-role-channels.md). Since
     * docs/ADR/031-remove-energyguard-monitoring-channels.md, the only production caller is
     * {@code AbstractEEBusLimitControllableSystemUseCase} (Server/Controllable-System role) - the
     * EnergyGuard/Client-role read path this was originally built for was removed. {@code
     * limit-duration} added by docs/ADR/033-lpc-lpp-limit-duration-channel.md.
     *
     * @param channelGroup the Channel Group ID to create/update the Channels under - normalized to
     *            lowercase (see {@link #normalizeChannelGroup}) before use, since the Server-role
     *            {@code getShortCode()} implementations (unlike their Client-role counterparts) return
     *            the EEBUS-standard uppercase use-case code ("LPC"/"LPP") - without this, a Server-role
     *            caller would silently create a second, orphaned, wrongly-cased Channel Group instead
     *            of updating the statically-declared lowercase "lpc"/"lpp" one (found live 2026-08-26).
     * @param active whether the peer currently has an active limit ({@code isLimitActive})
     * @param watts the peer's currently reported limit value, in Watts
     * @param durationSeconds the limit's remaining relative duration of validity
     *            ({@code timePeriod.endTime}), in seconds, or {@code null} if absent/removed. Per
     *            LimitationOfPowerConsumption TS V1.0.0 §3.1.8.2/[LPC-004], this SPINE field is
     *            already a relative duration, not an absolute timestamp, so no {@code now}-based
     *            derivation is applied here. {@code null} means the limit is currently unbounded
     *            and is shown as {@link UnDefType#UNDEF}, distinct from an explicit "0 seconds"
     *            duration - see docs/ADR/033-lpc-lpp-limit-duration-channel.md.
     */
    public void applyLimitStatus(String channelGroup, boolean active, double watts, @Nullable Long durationSeconds) {
        String group = normalizeChannelGroup(channelGroup);
        ChannelUID activeChannelUID = new ChannelUID(thing.getUID(), group, EEBusBindingConstants.CHANNEL_LIMIT_ACTIVE);
        ensureChannel(activeChannelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_LIMIT_ACTIVE, "Switch", "Limit Active");
        updateCachedState(activeChannelUID, OnOffType.from(active));

        ChannelUID valueChannelUID = new ChannelUID(thing.getUID(), group, EEBusBindingConstants.CHANNEL_LIMIT_VALUE);
        ensureChannel(valueChannelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_LIMIT_VALUE, "Number:Power", "Limit");
        updateCachedState(valueChannelUID, new QuantityType<>(watts, Units.WATT));

        ChannelUID durationChannelUID = new ChannelUID(thing.getUID(), group,
                EEBusBindingConstants.CHANNEL_LIMIT_DURATION);
        ensureChannel(durationChannelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_LIMIT_DURATION, "Number:Time",
                "Limit Duration");
        updateCachedState(durationChannelUID,
                durationSeconds != null ? new QuantityType<>(durationSeconds, Units.SECOND) : UnDefType.UNDEF);
    }

    /**
     * Creates (if needed) and updates the read-only {@code state} Channel under
     * {@code channelGroup} with the Controllable System state machine's current state, encoded
     * as a plain {@code Number} - {@link EEBusLimitControlState#ordinal()} - so it is externally
     * visible without requiring an Item to be tagged first (the state machine was previously only
     * observable indirectly, through {@code LPC.state}/{@code LPP.state} metadata Items a user had
     * to tag and link manually). {@code channel-type}'s {@code <options>} give each ordinal a
     * readable label (see {@code thing-types.xml}'s {@code state} {@code channel-type}, which must
     * be kept in sync with {@link EEBusLimitControlState}'s declaration order - see that enum's
     * javadoc).
     *
     * @param channelGroup the Channel Group ID to create/update the Channel under - see
     *            {@link #applyLimitStatus}'s javadoc on {@link #normalizeChannelGroup}
     * @param state the state machine's current state
     */
    public void applyLimitControlState(String channelGroup, EEBusLimitControlState state) {
        String group = normalizeChannelGroup(channelGroup);
        ChannelUID channelUID = new ChannelUID(thing.getUID(), group, EEBusBindingConstants.CHANNEL_STATE);
        ensureChannel(channelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_STATE, "Number", "State");
        updateCachedState(channelUID, new DecimalType(state.ordinal()));
    }

    /**
     * Creates (if needed) and updates the read-only {@code failsafe-limit-value}/
     * {@code failsafe-duration-minimum} Channels under {@code channelGroup} - purely informational,
     * showing what the paired Energy Guard has configured over EEBus (Scenario 2, Failsafe values).
     * Deliberately no corresponding write path from openHAB (no Item, no metadata, no Rule can change
     * these) - only a real Energy Guard writing over EEBus does, per explicit user decision - see
     * docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md. Called from
     * {@code AbstractEEBusLimitControllableSystemUseCase} whenever the Energy Guard writes a new
     * failsafe limit/duration, and once immediately after this peer is resolved so the Channels do not
     * lag behind a value that was already known before resolution.
     *
     * @param channelGroup the Channel Group ID to create/update the Channels under - see
     *            {@link #applyLimitStatus}'s javadoc on {@link #normalizeChannelGroup}
     * @param limitWatts the currently configured failsafe limit value, in Watts
     * @param durationSeconds the currently configured failsafe duration minimum, in seconds
     */
    public void applyFailsafeStatus(String channelGroup, double limitWatts, long durationSeconds) {
        String group = normalizeChannelGroup(channelGroup);
        ChannelUID limitChannelUID = new ChannelUID(thing.getUID(), group,
                EEBusBindingConstants.CHANNEL_FAILSAFE_LIMIT_VALUE);
        ensureChannel(limitChannelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_FAILSAFE_LIMIT_VALUE, "Number:Power",
                "Failsafe Limit");
        updateCachedState(limitChannelUID, new QuantityType<>(limitWatts, Units.WATT));

        ChannelUID durationChannelUID = new ChannelUID(thing.getUID(), group,
                EEBusBindingConstants.CHANNEL_FAILSAFE_DURATION_MINIMUM);
        ensureChannel(durationChannelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_FAILSAFE_DURATION_MINIMUM,
                "Number:Time", "Failsafe Duration");
        updateCachedState(durationChannelUID, new QuantityType<>(durationSeconds, Units.SECOND));
    }

    /**
     * Fires the {@code heartbeat} trigger Channel under {@code channelGroup} - called once per
     * Heartbeat notification received from the paired Energy Guard's {@code DeviceDiagnosis}
     * feature. Unlike {@link #applyLimitStatus}/{@link #applyFailsafeStatus}, this Channel has no
     * persistent state to cache - a trigger Channel only ever fires an event, there is nothing for
     * a later {@link RefreshType} to replay.
     *
     * @param channelGroup the Channel Group ID to fire the Channel under - see
     *            {@link #applyLimitStatus}'s javadoc on {@link #normalizeChannelGroup}
     * @param heartbeatCounter the Heartbeat's {@code heartbeatCounter} value, sent as the trigger
     *            event payload so a Rule can detect a skipped Heartbeat (a gap in the counter), or
     *            {@code null} if the peer's notification did not carry one
     */
    public void triggerHeartbeat(String channelGroup, @Nullable BigInteger heartbeatCounter) {
        if (disposed) {
            return;
        }
        String group = normalizeChannelGroup(channelGroup);
        ChannelUID channelUID = new ChannelUID(thing.getUID(), group, EEBusBindingConstants.CHANNEL_HEARTBEAT);
        ensureTriggerChannel(channelUID, EEBusBindingConstants.CHANNEL_TYPE_UID_HEARTBEAT, "Heartbeat");
        triggerChannel(channelUID, heartbeatCounter != null ? heartbeatCounter.toString() : "");
    }

    /**
     * Normalizes a Channel Group ID to lowercase, matching the {@code lpc}/{@code lpp}
     * {@code channel-group-type} ids declared in {@code thing-types.xml}.
     *
     * <p>
     * Needed because {@code getShortCode()} is dual-purpose across the two roles that call
     * {@link #applyLimitStatus}/{@link #applyFailsafeStatus}: the Client-role use cases
     * ({@code EEBusLpcClientUseCase}/{@code EEBusLppClientUseCase}) already return the lowercase
     * {@link EEBusBindingConstants#CHANNEL_GROUP_LPC}/{@link EEBusBindingConstants#CHANNEL_GROUP_LPP}
     * constants, but the Server-role use cases ({@code EEBusLpcServerUseCase}/
     * {@code EEBusLppServerUseCase}) deliberately return the EEBUS-standard uppercase code
     * ("LPC"/"LPP") instead, since {@code getShortCode()} doubles as the segment matched
     * case-sensitively by {@link org.openhab.binding.eebus.internal.transport.EEBusMetadataService#find}
     * against existing {@code eebus} Metadata Item values (documented there with uppercase examples,
     * e.g. {@code "ems1:MPC.power"}) - changing {@code getShortCode()} itself to lowercase would
     * silently stop matching those. Normalizing only here (the Channel-Group construction point),
     * mirrors the same asymmetry {@link org.openhab.binding.eebus.internal.transport.EEBusMetadataService
     * #findByTag} already resolves in the opposite direction (uppercasing the use-case segment there).
     * Before this fix, a Server-role caller passing "LPC"/"LPP" made {@link #ensureChannel} silently
     * create a second, orphaned Channel Group with the wrong case instead of updating the
     * statically-declared one - found live 2026-08-26 on an {@code oh-cs-entity} Thing.
     * </p>
     *
     * @param channelGroup the raw Channel Group ID as passed by the calling {@code UseCase}
     * @return {@code channelGroup} lowercased
     */
    private static String normalizeChannelGroup(String channelGroup) {
        return channelGroup.toLowerCase(Locale.ROOT);
    }

    /**
     * Records that a Client-role use case was detected for this peer, as a Thing property keyed
     * by the use case's lowercase name (e.g. {@link EEBusBindingConstants#USE_CASE_KEY_MPC}) -
     * see docs/changes/dynamic-client-role-channels/proposal.md "Open Questions" for why the
     * value is the actor role the <em>peer</em> plays, not openHAB's own role.
     *
     * @param useCaseKey the use case's lowercase name, e.g. {@code "mpc"}
     * @param actorRole the actor role the peer plays for this use case, e.g. {@code "server"}
     */
    public void recordDetectedUseCase(String useCaseKey, String actorRole) {
        updateProperty(useCaseKey, actorRole);
    }

    /**
     * Calls {@link #updateState(ChannelUID, State)} and records {@code state} in
     * {@link #lastKnownState}, so a later {@link RefreshType} command for the same Channel (e.g.
     * from an Item linked afterwards) can replay it - see {@link #lastKnownState}'s javadoc.
     *
     * @param channelUID the Channel to update
     * @param state the new state to push and cache
     */
    private void updateCachedState(ChannelUID channelUID, State state) {
        lastKnownState.put(channelUID, state);
        updateState(channelUID, state);
    }

    /**
     * Creates {@code channelUID} on this Thing via {@link org.openhab.core.thing.binding.builder.ThingBuilder}/
     * {@link ChannelBuilder} if it does not already exist; otherwise a no-op. Safe to call
     * repeatedly (e.g. once per measurement notification) without special-casing "already
     * created" at call sites - see docs/ADR/014-dynamic-client-role-channels.md.
     *
     * @param channelUID the Channel to create if missing
     * @param channelTypeUID the {@code channel-type} declared in {@code thing-types.xml} this
     *            Channel is an instance of
     * @param acceptedItemType the accepted item type, e.g. {@code "Number:Power"}
     * @param label the Channel's label
     */
    private synchronized void ensureChannel(ChannelUID channelUID, ChannelTypeUID channelTypeUID,
            String acceptedItemType, String label) {
        if (thing.getChannel(channelUID) != null) {
            return;
        }
        Channel channel = ChannelBuilder.create(channelUID, acceptedItemType).withType(channelTypeUID).withLabel(label)
                .build();
        updateThing(editThing().withChannel(channel).build());
    }

    /**
     * Same as {@link #ensureChannel}, for a trigger Channel instead of a state Channel - a trigger
     * Channel has no accepted item type to pass {@link ChannelBuilder#create(ChannelUID, String)},
     * so this uses the item-type-less {@link ChannelBuilder#create(ChannelUID)} overload and sets
     * {@link ChannelKind#TRIGGER} explicitly instead.
     *
     * @param channelUID the trigger Channel to create if missing
     * @param channelTypeUID the {@code channel-type} declared in {@code thing-types.xml} this
     *            Channel is an instance of
     * @param label the Channel's label
     */
    private synchronized void ensureTriggerChannel(ChannelUID channelUID, ChannelTypeUID channelTypeUID, String label) {
        if (thing.getChannel(channelUID) != null) {
            return;
        }
        Channel channel = ChannelBuilder.create(channelUID).withType(channelTypeUID).withKind(ChannelKind.TRIGGER)
                .withLabel(label).build();
        updateThing(editThing().withChannel(channel).build());
    }
}
