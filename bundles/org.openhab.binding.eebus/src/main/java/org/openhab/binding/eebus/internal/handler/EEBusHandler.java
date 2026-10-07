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

import static org.openmuc.jeebus.shipspine.ShipCommunication.ConnectClientsTo.NONE;
import static org.openmuc.jeebus.shipspine.ShipCommunication.ConnectClientsTo.TRUSTED;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.config.EEBusConfiguration;
import org.openhab.binding.eebus.internal.config.EEBusOhEntityConfiguration;
import org.openhab.binding.eebus.internal.transport.AbstractEEBusLimitControllableSystemUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusEvccClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusEvcemClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusEvseccClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusLpcClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusLpcServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusLppClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusLppServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser;
import org.openhab.binding.eebus.internal.transport.EEBusMetadataService;
import org.openhab.binding.eebus.internal.transport.EEBusMgcpClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMpcClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMpcServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusPortPool;
import org.openhab.core.OpenHAB;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.mdns.MDNSClient;
import org.openhab.core.net.CidrAddress;
import org.openhab.core.net.NetUtil;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openmuc.jeebus.ship.api.ConfigBuilder;
import org.openmuc.jeebus.ship.api.cert.KeyStoreCertificateStorage;
import org.openmuc.jeebus.ship.node.ShipConfig;
import org.openmuc.jeebus.shipspine.ShipCommunication;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.xsd.v1.DeviceTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusHandler} is the Bridge handler for the {@code eebus:oh-device}/
 * {@code eebus:oh-cs-device} Thing types (renamed from {@code eebus:service}/
 * {@code eebus:cs-service} by docs/ADR/024-oh-device-oh-entity-rename.md). It represents
 * exactly one local SHIP/SPINE service instance: one certificate/SKI, one mDNS-advertised
 * presence, one SPINE {@code Device}.
 *
 * <p>
 * <strong>Naming note:</strong> this class is the Bridge handler (what CONCEPT.md calls
 * {@code EEBusBridgeHandler}). It kept the archetype's original file/class name
 * {@code EEBusHandler} to avoid an extra rename step; feel free to rename via your IDE's
 * refactor tool if you'd rather match the CONCEPT.md naming exactly.
 * </p>
 *
 * <p>
 * <strong>Trust model</strong> (see CONCEPT.md §5.2, §6 decision 4, §4.5, the 2026-08-23
 * addendum, docs/ADR/024-oh-device-oh-entity-rename.md - superseding docs/ADR/012-pairing-
 * trust-property-and-actions.md - and docs/ADR/027-derive-local-use-cases-from-entities.md
 * Decision 6): trust is a Bridge-level concept - this Bridge's
 * {@link EEBusConfiguration#trustedSkis} config list is the source of truth
 * {@link #currentTrustedSkis()} reads, not a property on any child Thing. Editable only as
 * Thing config now: the live, no-restart {@code trust(String)}/{@code untrust(String)} Thing
 * Actions this class used to also expose ({@code EEBusDeviceActions}) are removed - a trust
 * change is just another config change, and every config change now uniformly triggers a full
 * {@link #dispose()}/{@link #initialize()} rebuild (ADR-027), so the separate live-push path
 * added nothing once that was true. A child {@code eebus:oh-entity} Thing's own status reflects
 * whether its configured {@code ski} is currently trusted ({@link #isTrusted(String)}) -
 * creating/configuring it never grants trust by itself, and removing it never revokes trust
 * either (trust is entirely independent of which {@code eebus:oh-entity} Things happen to
 * exist).
 * </p>
 *
 * <p>
 * <strong>Verified</strong> (CONCEPT.md §7 item 1, resolved; re-verified for
 * docs/ADR/049-configbuilder-ship-node-construction.md): {@code KeyStoreCertificateStorage}'s
 * file-backed constructor does auto-create the certificate/keystore if none exists at the given
 * path, confirmed against the actual {@code org.openmuc.jeebus:ship:3.0.1} source
 * (github.com/openmuc/jeebus.ship, tag {@code v3.0.1}) - same auto-create guarantee the
 * previously-used, now-removed {@code ShipNodeConfiguration} certPath constructor made.
 * </p>
 *
 * <p>
 * <strong>Certificate persistence</strong> (docs/ADR/010-storageservice-keystore-mirror.md): the
 * keystore file above remains the operational source of truth handed to
 * {@code KeyStoreCertificateStorage} (docs/ADR/049-configbuilder-ship-node-construction.md).
 * ship 3.0.0 made {@code CertificateStorage} pluggable - a {@code StorageService}-backed
 * implementation that would let {@link #restoreKeystoreFromStorage}/
 * {@link #persistKeystoreToStorage} below be retired entirely is now possible, but that is a
 * separate, larger change not yet scoped (see
 * docs/changes/ship-spine-configbuilder-migration/proposal.md's "Out of scope"). Until then, the
 * same pragmatic middle ground as before stands: {@link #restoreKeystoreFromStorage} /
 * {@link #persistKeystoreToStorage} mirror the file's bytes into openHAB's
 * {@code StorageService} (java-coding-rules.md's standard persistence mechanism) around every
 * start, so the identity is not solely dependent on a loose file under userdata surviving.
 * </p>
 *
 * <p>
 * <strong>Package note:</strong> this class still imports jEEBus.SHIP/SPINE types directly
 * (starting/tearing down {@link ShipCommunication}, building the local SPINE {@link Device}) -
 * it is not yet a pure openHAB-facing class despite living in {@code internal.handler}. A
 * follow-up could extract that session-management logic into a dedicated
 * {@code internal.transport} class so {@code internal.handler} becomes fully free of SPINE/SHIP
 * imports; not done in this pass to avoid a larger, unreviewed behavioral refactor. See
 * docs/ADR/002-package-split-transport-handler.md.
 * </p>
 *
 * <p>
 * <strong>Start/dispose concurrency</strong> (see docs/ADR/005-supersede-stale-start-on-dispose.md):
 * {@link #startShipSpine} runs asynchronously on a background task scheduled by
 * {@link #initialize()} and performs the SHIP server's actual port bind
 * ({@code communication.connect()}) without holding any lock, since that call can block for a
 * moment and must never stall {@link #dispose()}. Instead, {@link #initialize()} and
 * {@link #dispose()} each bump a generation counter kept in {@link #LIFECYCLES}, a static map
 * keyed by {@link ThingUID}; right after connecting, {@link #startShipSpine} re-checks that
 * counter before publishing {@link #shipCommunication} etc. If it has been superseded in the
 * meantime, it shuts the SHIP server it just bound back down instead of publishing it - so a
 * stale attempt can still finish binding a moment after {@link #dispose()} ran, but it always
 * cleans up after itself rather than leaking the port. {@link #dispose()} itself never blocks
 * waiting for an in-flight start attempt. The counter is keyed by {@link ThingUID} rather than
 * kept as a plain instance field because a live reproduction showed {@code startShipSpine()}
 * running twice for the same Thing with neither attempt detecting the other - i.e. openHAB can
 * end up with more than one {@link EEBusHandler} object for the same Thing, each with its own
 * instance state; only state shared by Thing identity catches that case. See ADR-005 for the full
 * history, including why an earlier, join-based design and a plain instance-field generation
 * counter were both rejected.
 * </p>
 *
 * <p>
 * <strong>Per-Thing serialization</strong> (see docs/ADR/028-serialize-startshipspine-per-thing.md):
 * the paragraph above still holds - {@link #dispose()} never blocks - but {@link #startShipSpine}'s
 * actual body ({@link #startShipSpineLocked}) is now additionally serialized per Thing via
 * {@code Lifecycle#startLock}, so two overlapping generations for the same Thing never touch the
 * SHIP keystore file or bind a port concurrently (previously possible, and confirmed live as an
 * {@code OverlappingFileLockException} in a 2026-08-26 startup trace - see project memory).
 * {@link #onEntityChanged()} is now also debounced ({@code ENTITY_CHANGE_DEBOUNCE_MILLIS}),
 * coalescing a burst of child-Entity change events into one rebuild instead of one
 * dispose()/initialize() cycle per event.
 * </p>
 *
 * <p>
 * <strong>Retry on transient teardown race</strong> (see
 * docs/ADR/029-retry-startshipspine-on-transient-teardown-race.md): serializing generations
 * per Thing (previous paragraph) stops two overlapping *new* generations from racing each
 * other, but a live full-restart retest (2026-08-26 22:33) showed a *new* generation can
 * still race the *previous* generation's own asynchronous SHIP-layer teardown tail - its
 * {@code disconnect()}/{@code close()} does not release the port/keystore file lock
 * synchronously, and {@link #dispose()} deliberately does not wait for it (see above). This
 * surfaced as a {@link BindException} ("Address already in use") whose own "stopping SHIP
 * server" log line only appeared *after* the failed bind attempt, and, in an earlier trace,
 * as the keystore {@code OverlappingFileLockException}. {@link #startShipSpine} now retries
 * {@link #startShipSpineLocked} a bounded number of times with a short backoff specifically
 * for those two exception types (see {@link #isTransientTeardownRaceFailure}, which - fixed
 * 2026-09-02 after a live trace showed the retry never actually firing - unwraps the full
 * cause chain rather than matching only the caught exception's own top-level type, since
 * {@code jeebus.ship}'s {@code ShipNodeImpl} constructor wraps the keystore-lock failure as a
 * plain {@link RuntimeException}), giving the previous generation's teardown time to actually
 * finish; any other exception, or the generation being superseded while retrying, is not
 * retried.
 * </p>
 *
 * <p>
 * <strong>Topology phase gate</strong> (see docs/ADR/043-topology-phase-gate.md): all of the
 * above concurrency/retry machinery deals with generations racing each other once
 * {@link #startShipSpine} is allowed to run at all - it does not stop *how often* it runs. A
 * live trace on 2026-09-04 showed 5 full Device rebuilds in 11 minutes during a setup/change
 * phase, which - combined with a confirmed {@code jeebus.spine} client-side subscription-cache
 * desync (see project memory) - can silently break Heartbeat delivery. {@link #initialize()}
 * now checks this Bridge's persisted {@link EEBusTopologyPhase} first: while {@code ASSEMBLING}
 * (the default), it returns immediately after config validation, before reserving a port or
 * touching SHIP/SPINE at all, regardless of how many times {@code initialize()} itself is
 * called. A human moves it to {@code SETTLED} - once, explicitly, persisted across restarts -
 * via the new {@code settleTopology()} Thing Action ({@link EEBusTopologyActions}), once the
 * intended topology (which Bridges, which attached {@code eebus:oh-entity} Things) is actually
 * in place. This is a mitigation for the exposure window, not a fix for either underlying cause.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusHandler extends BaseBridgeHandler implements EEBusEntityChangeListener {

    /**
     * Per-Thing lifecycle state for the generation-counter scheme described in the class
     * javadoc and ADR-005. Kept in a static map keyed by {@link ThingUID} - not as a plain
     * instance field - specifically because two separate {@link EEBusHandler} objects for the
     * same Thing were observed to race each other in a live reproduction; instance fields would
     * not be shared between them, but this map is.
     */
    private static final class Lifecycle {
        final Object lock = new Object();
        long generation;

        /**
         * Serializes {@link #startShipSpineLocked}'s body (keystore file access, port bind,
         * SPINE {@code Device.build()}) across every generation for this Thing - see
         * docs/ADR/028-serialize-startshipspine-per-thing.md. Without this, two overlapping
         * generations for the same Thing could load/lock the same {@code .jks} keystore file
         * concurrently (a JVM-wide file-lock conflict - {@code java.nio.channels.FileLock} is
         * scoped per JVM, not per {@code FileChannel} - confirmed live as an
         * {@code OverlappingFileLockException} in a 2026-08-26 startup trace) or otherwise race
         * on other shared per-Thing resources. Deliberately a separate lock object from
         * {@link #lock}: {@link #lock} only ever guards brief, in-memory field reads/writes and
         * must stay that way, while this one is held across slow I/O for the whole duration of a
         * start attempt - and, per ADR-005, must never be acquired by {@link EEBusHandler#dispose()}.
         */
        final ReentrantLock startLock = new ReentrantLock();

        /**
         * The pending debounce timer for {@link EEBusHandler#onEntityChanged()}, if a rebuild
         * has been scheduled but not yet run - see docs/ADR/028-serialize-startshipspine-per-
         * thing.md. {@code null} when no rebuild is currently scheduled. Guarded by {@link #lock}.
         */
        @Nullable
        ScheduledFuture<?> pendingRebuild;
    }

    private static final Map<ThingUID, Lifecycle> LIFECYCLES = new ConcurrentHashMap<>();

    /**
     * Bridges of this binding that currently have a live SHIP server in this JVM, keyed by Thing
     * UID. Used by {@link #refreshLocalPeers} (docs/ADR/055-restart-local-peer-bridges.md).
     */
    private static final Map<ThingUID, EEBusHandler> RUNNING = new ConcurrentHashMap<>();

    /** Do not refresh a local peer Bridge that itself (re)started less than this long ago. */
    private static final long PEER_REFRESH_COOLDOWN_MILLIS = 30_000;

    /** Wall-clock time this Bridge last finished binding its SHIP server; 0 if never. */
    private volatile long lastBoundMillis;

    /** SKI of this Bridge's own SHIP node, set when the SHIP server was bound; null before. */
    private volatile @Nullable String ownSki;

    /**
     * How long {@link #onEntityChanged()} waits for further child-Entity change events before
     * actually rebuilding, coalescing a burst of near-simultaneous calls into a single
     * dispose()/initialize() cycle. See docs/ADR/028-serialize-startshipspine-per-thing.md.
     */
    private static final long ENTITY_CHANGE_DEBOUNCE_MILLIS = 500;

    /**
     * Bounded number of attempts {@link #startShipSpine} makes at {@link #startShipSpineLocked}
     * before giving up, when it fails with one of the specific, known-transient exceptions
     * caused by the previous generation's SHIP-layer teardown not having finished releasing the
     * port/keystore yet. See docs/ADR/029-retry-startshipspine-on-transient-teardown-race.md.
     */
    private static final int START_RETRY_MAX_ATTEMPTS = 3;

    /**
     * Base backoff delay between retry attempts (see {@link #START_RETRY_MAX_ATTEMPTS}); the
     * Nth retry waits {@code N * START_RETRY_BACKOFF_BASE_MILLIS} (250 ms, 500 ms, 750 ms for
     * N=1,2,3), giving the previous generation's teardown progressively more time to finish.
     */
    private static final long START_RETRY_BACKOFF_BASE_MILLIS = 250;

    // DIAGNOSTICS DISABLED 2026-10-05 (dispose()/stop() latency investigation): commented out, not deleted.
    // Re-enable by uncommenting this block together with the other blocks marked the same way.
    // /**
    // * Diagnostic only (not a fix): how long a {@code communication.disconnect()}/
    // * {@code localDevice.close()}/{@code browser.close()} teardown - from either {@link
    // * #dispose()} or {@link #startShipSpineLocked}'s supersession-cleanup branch - is given
    // * before the first dump of every JVM thread's full stack to the log. Chosen just under
    // * openHAB core's own SafeCaller "dispose() takes more than 5000ms" watchdog (see ADR-005's
    // * comment in dispose()) so the first dump lands at roughly the same moment that WARN would
    // * fire, capturing what the calling thread and every Netty event-loop thread are doing at
    // * that instant. Investigating the still-open root cause documented in project memory
    // * (dispose()/stop() latency, 4x confirmed as of 2026-09-06 - including a live trace that
    // * caught the baseline ~4s of every teardown parked in JmDNS's own
    // * {@code waitForCanceled()}/{@code Semaphore} wait, third-party library code unrelated to
    // * jeebus.ship/Netty/our binding - plus the still-unexplained additional 12-55s seen in the
    // * pathological traces, which this delay alone was too short-lived to capture; see {@link
    // * #TEARDOWN_STALL_DUMP_PERIOD_MILLIS}); remove once that investigation concludes.
    // */
    // private static final long TEARDOWN_STALL_DUMP_DELAY_MILLIS = 4000;

    // /**
    // * Diagnostic only, see {@link #TEARDOWN_STALL_DUMP_DELAY_MILLIS}. Once a teardown has stalled
    // * long enough for the first dump, repeat the dump every this many milliseconds instead of
    // * dumping only once - a single snapshot only shows what is blocking at that one instant, but
    // * the pathological ~26s/~59s stalls observed in the field appear to move through multiple
    // * distinct phases (JmDNS wait, then a failed connectionClose send, then several seconds of
    // * complete log silence), so only a series of dumps across the stall's duration can show that
    // * progression instead of just its first phase.
    // */
    // private static final long TEARDOWN_STALL_DUMP_PERIOD_MILLIS = 4000;

    // /**
    // * Diagnostic only, see {@link #TEARDOWN_STALL_DUMP_DELAY_MILLIS}. Upper bound on how long the
    // * periodic dump keeps repeating for a single stalled teardown, so a teardown that never
    // * returns (as opposed to merely a slow one) does not spam the log forever. Comfortably above
    // * the worst pathological stall observed so far (~59s).
    // */
    // private static final long TEARDOWN_STALL_DUMP_MAX_MILLIS = 90000;

    /**
     * Fixed {@code brand} value this Bridge announces in its own SHIP mDNS TXT record (SHIP:7.3.2)
     * via {@code ConfigBuilder#withBrand(String)} - see docs/ADR/050-configurable-ship-registration-
     * identity.md. Previously left at {@code ConfigBuilder}'s own default ({@code "jEEBus"}); this
     * binding is not jEEBus itself, it is the openHAB integration embedding it, so it now
     * self-identifies as openHAB instead. Unrelated to {@link EEBusConfiguration#deviceBrand}, which
     * describes the physical device behind this Bridge, not this SHIP node's own announced identity.
     */
    private static final String SHIP_BRAND = "openHAB";

    /**
     * Fixed {@code type} value this Bridge announces in its own SHIP mDNS TXT record (SHIP:7.3.2)
     * via {@code ConfigBuilder#withType(String)} - see docs/ADR/050-configurable-ship-registration-
     * identity.md. Previously left at {@code ConfigBuilder}'s own default ({@code "default"}); "CEM"
     * (Customer Energy Manager) is what this Bridge actually is on the EEBUS network.
     */
    private static final String SHIP_TYPE = "CEM";

    // DIAGNOSTICS DISABLED 2026-10-05 (dispose()/stop() latency investigation): commented out, not deleted.
    // Re-enable by uncommenting this block together with the other blocks marked the same way.
    // /**
    // * Schedules the diagnostic thread-dump watchdog described on {@link
    // * #TEARDOWN_STALL_DUMP_DELAY_MILLIS} for a teardown about to start on the calling thread. The
    // * first dump fires after {@link #TEARDOWN_STALL_DUMP_DELAY_MILLIS}; if the teardown is still
    // * running, further dumps repeat every {@link #TEARDOWN_STALL_DUMP_PERIOD_MILLIS} until either
    // * the teardown finishes (the caller cancels the returned future) or {@link
    // * #TEARDOWN_STALL_DUMP_MAX_MILLIS} total has elapsed, whichever comes first. Callers must
    // * {@code cancel(false)} the returned future in a {@code finally} block the moment the
    // * teardown actually finishes, so a normal fast teardown never logs anything extra.
    // *
    // * @param context
    // * short label identifying which teardown call site this is (e.g. {@code
    // * "dispose()"} or {@code "startShipSpineLocked() supersession teardown"}), so the
    // * eventual dump (if any) is unambiguous about where it came from
    // */
    // private ScheduledFuture<?> scheduleTeardownStallWatchdog(ThingUID uid, long generation, String context) {
    // Thread callingThread = Thread.currentThread();
    // AtomicInteger dumpCount = new AtomicInteger(0);
    // AtomicReference<@Nullable ScheduledFuture<?>> selfRef = new AtomicReference<>();
    // ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
    // int count = dumpCount.incrementAndGet();
    // long stalledForMillis = TEARDOWN_STALL_DUMP_DELAY_MILLIS
    // + (long) (count - 1) * TEARDOWN_STALL_DUMP_PERIOD_MILLIS;
    // logTeardownStallThreadDump(uid, generation, callingThread.getName(), context, count, stalledForMillis);
    // if (stalledForMillis >= TEARDOWN_STALL_DUMP_MAX_MILLIS) {
    // @Nullable
    // ScheduledFuture<?> self = selfRef.get();
    // if (self != null) {
    // self.cancel(false);
    // }
    // }
    // }, TEARDOWN_STALL_DUMP_DELAY_MILLIS, TEARDOWN_STALL_DUMP_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    // selfRef.set(future);
    // return future;
    // }

    private static final String PROPERTY_IDENTITY_VENDOR_CODE = "identityVendorCode";
    private static final String PROPERTY_IDENTITY_DEVICE_MODEL = "identityDeviceModel";
    private static final String PROPERTY_IDENTITY_SERIAL_NUMBER = "identitySerialNumber";

    private final Logger logger = LoggerFactory.getLogger(EEBusHandler.class);

    private @Nullable EEBusConfiguration config;
    private @Nullable ShipCommunication shipCommunication;
    private @Nullable Device device;
    private @Nullable EEBusMdnsBrowser mdnsBrowser;

    /**
     * The port taken out of {@link #portPool} by {@link #initialize()} (either the configured
     * port or a free one assigned by the pool) - released back to the pool in
     * {@link #handleRemoval()}, never in {@link #dispose()}. See {@link EEBusPortPool}'s class
     * javadoc for the lifecycle contract.
     */
    private @Nullable Integer reservedPort;

    private final EEBusMetadataService metadataService;
    private final MDNSClient mdnsClient;
    private final EEBusPortPool portPool;
    private final StorageService storageService;
    private final NetworkAddressService networkAddressService;

    /**
     * Per-Thing {@link Storage} obtained in {@link #initialize()}, holding a base64-encoded
     * mirror of the SHIP keystore file ({@link #getKeystoreFile()}) under
     * {@link #KEYSTORE_STORAGE_KEY}. See {@link #restoreKeystoreFromStorage(File)}/
     * {@link #persistKeystoreToStorage(File)} for why this exists alongside the file, and
     * {@link #handleRemoval()} for why - unlike the usual {@code StorageService} pattern
     * (java-coding-rules.md) - this entry is deliberately not removed there.
     */
    private @Nullable Storage<String> keystoreStorage;

    /**
     * Per-Thing {@link Storage} obtained in {@link #initialize()}, holding this Bridge's
     * persisted {@link EEBusTopologyPhase} under {@link #TOPOLOGY_PHASE_STORAGE_KEY} - see
     * docs/ADR/043-topology-phase-gate.md. Deliberately a separate {@link Storage} instance from
     * {@link #keystoreStorage} (a different {@code storageService.getStorage(...)} namespace),
     * purely so the two persisted concerns stay visibly independent - there is no functional
     * reason they could not share one.
     */
    private @Nullable Storage<String> topologyStorage;

    public EEBusHandler(Bridge bridge, EEBusMetadataService metadataService, MDNSClient mdnsClient,
            EEBusPortPool portPool, StorageService storageService, NetworkAddressService networkAddressService) {
        super(bridge);
        this.metadataService = metadataService;
        this.mdnsClient = mdnsClient;
        this.portPool = portPool;
        this.storageService = storageService;
        this.networkAddressService = networkAddressService;
    }

    /**
     * @return the {@link Lifecycle} shared by every {@link EEBusHandler} object that has ever
     *         existed for this Thing's UID, creating it on first use.
     */
    private Lifecycle lifecycle() {
        // Map#computeIfAbsent is annotated as returning @Nullable (the mapping function is
        // allowed to return null, in which case no mapping is added) even though our mapping
        // function here never does - same pattern as EEBusMetadataService#itemNameOf.
        return Objects.requireNonNull(LIFECYCLES.computeIfAbsent(thing.getUID(), uid -> new Lifecycle()));
    }

    @Override
    public void initialize() {
        // Bump the generation before doing anything else, so that any background start task
        // from a previous initialize() (that has not yet reached the check in startShipSpine())
        // - whether scheduled by this object or, per the class javadoc, a different EEBusHandler
        // object for the same Thing - is guaranteed to see itself as superseded. See ADR-005.
        Lifecycle lifecycle = lifecycle();
        long generation;
        synchronized (lifecycle.lock) {
            generation = ++lifecycle.generation;
        }

        // Diagnostic instrumentation for the still-open "why does initialize() run more than once
        // for the same Thing without an intervening dispose()" question (see ADR-005's "Negative"
        // section). Deliberately no trailing Throwable here (an earlier version printed one purely
        // to capture the caller's stack trace, with message "no error" - dropped since it reads
        // like a real exception in the log and was confusing to read at a glance).
        logger.debug("initialize() called for {} (handler={}, generation={}, thread={})", thing.getUID(),
                System.identityHashCode(this), generation, Thread.currentThread().getName());

        // See java-coding-rules.md's StorageService lifecycle pattern: obtained here in
        // initialize(), not removed in handleRemoval() (deliberate deviation, see the field
        // javadoc on keystoreStorage and the comment in handleRemoval() for why).
        keystoreStorage = storageService.getStorage(thing.getUID().toString(), String.class.getClassLoader());
        topologyStorage = storageService.getStorage(thing.getUID().toString() + "-topology",
                String.class.getClassLoader());

        EEBusConfiguration cfg = getConfigAs(EEBusConfiguration.class);
        this.config = cfg;

        if (cfg.vendorCode.isBlank() || cfg.deviceBrand.isBlank() || cfg.deviceModel.isBlank()
                || cfg.serialNumber.isBlank() || cfg.mdnsServiceInstance.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "vendorCode, deviceBrand, deviceModel, serialNumber and mdnsServiceInstance are required");
            return;
        }

        // See docs/ADR/043-topology-phase-gate.md: while this Bridge's persisted topology phase
        // is still the default ASSEMBLING, deliberately do nothing SPINE-related at all - no
        // port reservation, no keystore/certificate work, no Device build/connect - no matter
        // how many times initialize() itself runs (config edits, child eebus:oh-entity Things
        // attaching, or openHAB's own still-unexplained duplicate-initialize() calls, ADR-005).
        // A human must explicitly call the settleTopology() Thing Action
        // (EEBusTopologyActions) once the intended topology (which Bridges, which attached
        // entities) is actually in place; only then - and on every subsequent restart, since the
        // phase persists - does this Bridge proceed past this point.
        if (currentTopologyPhase() == EEBusTopologyPhase.ASSEMBLING) {
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Topology is ASSEMBLING - call the settleTopology() Thing Action on this Bridge once "
                            + "the intended topology (Bridges/entities) is in place to start the SPINE "
                            + "connection");
            return;
        }

        Optional<Integer> resolvedPortOrEmpty = resolvePort(cfg);
        if (resolvedPortOrEmpty.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "No free port available in the EEBus port pool (" + EEBusPortPool.PORT_RANGE_START + "-"
                            + EEBusPortPool.PORT_RANGE_END + "); configure a port explicitly");
            return;
        }
        int resolvedPort = resolvedPortOrEmpty.get();
        this.reservedPort = resolvedPort;

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            try {
                if (startShipSpine(cfg, resolvedPort, lifecycle, generation)) {
                    updateStatus(ThingStatus.ONLINE);
                }
                // else: superseded while starting - startShipSpine() already shut back down
                // whatever it just bound; whichever newer attempt is current now owns the
                // ThingStatus.
            } catch (Exception e) {
                boolean stillCurrent;
                synchronized (lifecycle.lock) {
                    stillCurrent = generation == lifecycle.generation;
                }
                if (stillCurrent) {
                    logger.warn("Failed to start EEBus service instance '{}' (generation={}, handler={})",
                            thing.getUID(), generation, System.identityHashCode(this), e);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                } else {
                    logger.debug(
                            "EEBus service instance '{}' failed to start after being superseded by a newer "
                                    + "attempt; ignoring (generation={}, handler={}): {}",
                            thing.getUID(), generation, System.identityHashCode(this), e.getMessage());
                }
            }
        });
    }

    /**
     * Resolves the port to bind the local SHIP server to, taking it out of {@link #portPool}: the
     * Thing's explicitly configured port if one was set (occupied via
     * {@link EEBusPortPool#reservePort(int)}), otherwise a free port assigned by the pool
     * ({@link EEBusPortPool#acquireFreePort()}) - which is then immediately persisted back into
     * the Thing's configuration ({@link #persistAutoAssignedPort(int)}), so that a later
     * {@code dispose()}/{@code initialize()} cycle (e.g. a restart) resolves the exact same port
     * again here instead of possibly drawing a different one from the pool. Called once from
     * {@link #initialize()} - the resolved port is only released back to the pool in
     * {@link #handleRemoval()}, never {@link #dispose()} (see {@link EEBusPortPool}'s class
     * javadoc for why).
     *
     * @param cfg this Thing's configuration
     * @return the resolved port; empty if no port was configured and the pool
     *         ({@value EEBusPortPool#PORT_RANGE_START}-{@value EEBusPortPool#PORT_RANGE_END}) is
     *         exhausted
     */
    private Optional<Integer> resolvePort(EEBusConfiguration cfg) {
        Integer configuredPort = cfg.port;
        if (configuredPort != null) {
            portPool.reservePort(configuredPort);
            return Optional.of(configuredPort);
        }
        Optional<Integer> freePort = portPool.acquireFreePort();
        freePort.ifPresent(this::persistAutoAssignedPort);
        return freePort;
    }

    /**
     * Writes an auto-assigned port back into the Thing's persisted configuration under
     * {@link EEBusConfiguration#PARAM_PORT}, so it is no longer {@code null} on the next
     * {@code initialize()} - see {@link #resolvePort} for why.
     *
     * <p>
     * Deliberately {@code editConfiguration()}/{@code updateConfiguration(Configuration)} - the
     * documented, safe way for a handler to write back a value it computed itself. This does
     * <strong>not</strong> go through {@code handleConfigurationUpdate()} (the
     * {@code dispose()}+{@code initialize()} cycle openHAB runs for configuration changes coming
     * from the UI/REST API, confirmed against the {@code ThingResource#updateConfiguration} ->
     * {@code BaseThingHandler#handleConfigurationUpdate} call chain seen in this Bridge's own
     * debug logs). Calling that path instead from inside {@code initialize()} itself would cause
     * a reentrant/looping re-initialize.
     * </p>
     *
     * @param port the port to persist
     */
    private void persistAutoAssignedPort(int port) {
        Configuration configuration = editConfiguration();
        configuration.put(EEBusConfiguration.PARAM_PORT, port);
        updateConfiguration(configuration);
    }

    /**
     * Resolves the single local address the SHIP server binds to and announces via mDNS with -
     * deliberately one address, not {@code "0.0.0.0"} (all interfaces). Binding to every local
     * interface makes {@code ServiceRegistry} (jeebus.ship) register the identical SHIP service
     * once per interface/address (one {@code JmDNS} instance each - JmDNS has no "all
     * interfaces" mode), so a dual-stack, multi-homed host gets mDNS-resolved repeatedly
     * (IPv4, link-local IPv6, ULA/global IPv6) within seconds of each other. Each resolution
     * independently triggers {@code ShipCommunication#serviceAdded}, which has no per-SKI
     * de-duplication (it caches connections by address, not by peer SKI - see
     * {@code ConnectionHandlerImpl#newConnection}), so with {@code connectToPeers} enabled this
     * redials on every single one of them, tripping jeebus.ship's double-connection detection
     * repeatedly before a session can stabilize. Restricting our own announcement to one address
     * removes the redial storm on our side - it does not fix a genuinely dual-stack remote peer
     * announcing multiple addresses itself; that would need SKI-based de-duplication in
     * jeebus.spine (out of scope here - jeebus.spine is protected, see CLAUDE.md).
     *
     * <p>
     * Preference order: if {@link EEBusConfiguration#preferIpv4} is set (recommended default as
     * of 2026-08-21 - works around a confirmed malformed-URI bug in the embedded SHIP library
     * that only affects IPv6 addresses, see its javadoc), openHAB's configured primary IPv4
     * address wins outright. Otherwise: a routable (non-link-local) IPv6 address, then any
     * IPv6 address (including link-local - accepted here since this SHIP/mDNS stack is already
     * observed handling link-local IPv6 connections successfully), then openHAB's configured
     * primary IPv4 address ({@link NetworkAddressService#getPrimaryIpv4HostAddress()}), then -
     * only if none of the above yielded anything - {@code "0.0.0.0"} as a last-resort fallback
     * to the previous behavior. IPv6 is skipped entirely if
     * {@link NetworkAddressService#isUseIPv6()} reports the user disabled it in openHAB's
     * network settings.
     * </p>
     *
     * <p>
     * If the embedded SHIP library's IPv6 URI bug is ever fixed upstream and
     * {@code preferIpv4} is no longer needed: delete the {@code if (cfg.preferIpv4)} block
     * below, the {@link EEBusConfiguration#preferIpv4} field, and the matching
     * {@code preferIpv4} parameter in {@code thing-types.xml}.
     * </p>
     *
     * @param cfg the configuration to read {@link EEBusConfiguration#preferIpv4} from
     * @return the single address to bind/announce the SHIP server on
     */
    private String resolveBindAddress(EEBusConfiguration cfg) {
        if (cfg.preferIpv4) {
            @Nullable
            String ipv4 = networkAddressService.getPrimaryIpv4HostAddress();
            if (ipv4 != null) {
                return ipv4;
            }
            logger.warn("{}: preferIpv4 is enabled but NetworkAddressService found no primary IPv4 address - "
                    + "falling back to the normal IPv6-first preference", thing.getUID());
        }

        if (networkAddressService.isUseIPv6()) {
            Optional<String> ipv6 = preferredIpv6Address();
            if (ipv6.isPresent()) {
                return ipv6.get();
            }
        }
        @Nullable
        String ipv4 = networkAddressService.getPrimaryIpv4HostAddress();
        if (ipv4 != null) {
            return ipv4;
        }
        logger.warn("{}: no usable IPv6 or IPv4 address found via NetworkAddressService - falling back to 0.0.0.0 "
                + "(all interfaces), which may reintroduce repeated mDNS announcements across multiple addresses",
                thing.getUID());
        return "0.0.0.0";
    }

    /**
     * @return a non-loopback local IPv6 address, preferring a routable (non-link-local) one over
     *         a link-local one if both exist; empty if this host has no IPv6 address at all
     */
    private Optional<String> preferredIpv6Address() {
        List<Inet6Address> candidates = NetUtil.getAllInterfaceAddresses().stream().map(CidrAddress::getAddress)
                .filter(Inet6Address.class::isInstance).map(Inet6Address.class::cast)
                .filter(address -> !address.isLoopbackAddress()).toList();

        return candidates.stream().filter(address -> !address.isLinkLocalAddress()).findFirst()
                .or(() -> candidates.stream().findFirst()).map(InetAddress::getHostAddress);
    }

    /**
     * @param cfg
     *            the configuration to start the SHIP/SPINE service instance with
     * @param resolvedPort
     *            the port to bind the SHIP server to, as resolved by {@link #resolvePort} in
     *            {@link #initialize()}
     * @param lifecycle
     *            this Thing's shared {@link Lifecycle}, see ADR-005
     * @param myGeneration
     *            the {@code lifecycle.generation} value captured by the caller when this attempt
     *            was scheduled - used to detect being superseded, see ADR-005
     * @return {@code true} if this attempt published itself as the active instance
     *         ({@link #shipCommunication} etc. were set); {@code false} if it was superseded while
     *         connecting and shut itself back down instead
     */
    /**
     * Derives this Bridge's local Use-Case set from its currently attached children - the
     * replacement for the removed Bridge-level {@code supportedUseCasesClient}/
     * {@code supportedUseCasesServer} checkboxes and the removed {@code eebus:oh-cs-device}
     * special case, see docs/ADR/027-derive-local-use-cases-from-entities.md. Called from
     * {@link #startShipSpine} every time the local SPINE {@code Device} is (re)built - i.e. on
     * every {@link #initialize()}, including ones triggered by {@link #onEntityChanged()}.
     *
     * <ul>
     * <li>Each {@code eebus:oh-entity} child contributes its own configured
     * {@code supportedUseCasesClient}/{@code supportedUseCasesServer} (same free choice, same
     * abbreviations, as before - just moved from this Bridge's config to the child's, see
     * {@link EEBusOhEntityConfiguration}).</li>
     * <li>Each {@code eebus:oh-cs-entity} child unconditionally contributes LPC+LPP Server -
     * seeded from the <em>first</em> such child's own failsafe config fields if more than one
     * exists (no longer structurally exclusive under this Bridge, see docs/ADR/025's revision
     * note - a second one is an unusual but not rejected configuration).</li>
     * <li>Each {@code eebus:oh-eg-entity} child unconditionally contributes LPC+LPP Client.</li>
     * <li>Each {@code eebus:oh-mpc-entity} child unconditionally contributes MPC Client
     * (docs/ADR/036-oh-mpc-entity-static-channels.md).</li>
     * <li>The {@code entityType} to build the shared local Entity with is resolved from the
     * same children's {@link EEBusOhEntityConfiguration#entityType} - the first non-default
     * ({@code "CEM"}) value found wins, with no check that the winning value is actually
     * spec-permitted for whatever Use Case combination this method derives
     * (docs/ADR/042-configurable-entitytype.md).</li>
     * </ul>
     *
     * <p>
     * Use-Case abbreviations are unioned into a {@link Set} per role before any {@link UseCase}
     * object is built, so two children both contributing e.g. {@code "LPC"} Server (an
     * {@code eebus:oh-entity} with the checkbox set, alongside an {@code eebus:oh-cs-entity})
     * still only produce a single {@link EEBusLpcServerUseCase} instance - SPINE's local CEM
     * Entity is built with one Use-Case implementation per abbreviation, not one per contributing
     * child.
     * </p>
     *
     * @param ohServiceId this Bridge's Thing ID, passed through to Server-role UseCase
     *            constructors exactly as before this ADR
     * @return the combined list of local {@link UseCase} implementations, and the resolved
     *         {@code entityType}, to build the local SPINE {@code Device}'s single shared Entity
     *         with (docs/ADR/042-configurable-entitytype.md)
     */
    private record LocalUseCaseDerivation(List<UseCase> useCases, EntityTypeEnumType entityType) {
    }

    private LocalUseCaseDerivation deriveLocalUseCases(String ohServiceId) {
        Set<String> serverUseCaseKeys = new HashSet<>();
        Set<String> clientUseCaseKeys = new HashSet<>();

        double consumptionSeedWatts = 0.0;
        double productionSeedWatts = 0.0;
        long durationSeedSeconds = AbstractEEBusLimitControllableSystemUseCase.DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS;
        boolean sawCsEntity = false;
        EntityTypeEnumType resolvedEntityType = EntityTypeEnumType.CEM;
        boolean sawNonDefaultEntityType = false;

        for (Thing child : getThing().getThings()) {
            ThingTypeUID childType = child.getThingTypeUID();
            if (EEBusBindingConstants.THING_TYPE_OH_HEMS_ENTITY.equals(childType)) {
                // docs/ADR/053: the HEMS child builds its own Entities, see deriveHemsEntities().
                // Its entityType/seed config must not leak into the legacy shared Entity.
                continue;
            }
            // docs/ADR/042-configurable-entitytype.md: every child Thing type shares
            // EEBusOhEntityConfiguration, so this fetch is valid regardless of childType - all
            // Things allowed as children of eebus:oh-device declare it in their
            // supported-bridge-type-refs. The first non-default entityType found across all
            // children wins; no check that it is actually spec-permitted for the Use Case
            // combination this method ends up deriving, or consistent with a sibling child's own
            // choice - deliberately deferred, see the ADR's "Consequences".
            EEBusOhEntityConfiguration childCfg = child.getConfiguration().as(EEBusOhEntityConfiguration.class);
            if (!sawNonDefaultEntityType && !childCfg.entityType.isBlank() && !"CEM".equals(childCfg.entityType)) {
                try {
                    resolvedEntityType = EntityTypeEnumType.fromValue(childCfg.entityType);
                    sawNonDefaultEntityType = true;
                } catch (IllegalArgumentException e) {
                    logger.warn(
                            "{}: child Thing '{}' configured an unknown entityType '{}' - ignoring, "
                                    + "falling back to CEM unless a later child configures a valid one",
                            thing.getUID(), child.getUID(), childCfg.entityType);
                }
            }
            if (EEBusBindingConstants.THING_TYPE_OH_CS_ENTITY.equals(childType)) {
                serverUseCaseKeys.add("LPC");
                serverUseCaseKeys.add("LPP");
                if (!sawCsEntity) {
                    consumptionSeedWatts = childCfg.failsafeConsumptionLimitSeedWatts;
                    productionSeedWatts = childCfg.failsafeProductionLimitSeedWatts;
                    durationSeedSeconds = childCfg.failsafeDurationMinimumSeedSeconds;
                    sawCsEntity = true;
                }
            } else if (EEBusBindingConstants.THING_TYPE_OH_EG_ENTITY.equals(childType)) {
                clientUseCaseKeys.add("LPC");
                clientUseCaseKeys.add("LPP");
            } else if (EEBusBindingConstants.THING_TYPE_OH_MPC_ENTITY.equals(childType)) {
                clientUseCaseKeys.add("MPC");
            } else if (EEBusBindingConstants.THING_TYPE_OH_ENTITY.equals(childType)) {
                serverUseCaseKeys.addAll(childCfg.supportedUseCasesServer);
                clientUseCaseKeys.addAll(childCfg.supportedUseCasesClient);
            }
        }

        // CONCEPT §5.5/§7.8: server-role UseCase implementations. Only MPC/LPC/LPP are
        // implemented so far - the other core use cases remain logged-but-unimplemented. CONCEPT
        // §4.5: each is given this Bridge's Thing ID as oh-device-id, so
        // EEBusMetadataService#find(...) can disambiguate Item metadata when more than one
        // eebus:oh-device Bridge offers the same use case/datapoint.
        List<UseCase> useCases = new ArrayList<>();
        if (serverUseCaseKeys.contains("MPC")) {
            useCases.add(new EEBusMpcServerUseCase(metadataService, ohServiceId));
        }
        if (serverUseCaseKeys.contains("LPC")) {
            useCases.add(new EEBusLpcServerUseCase(metadataService, ohServiceId,
                    this::ohEntityHandlerForCommunicationAddress, consumptionSeedWatts, durationSeedSeconds));
        }
        if (serverUseCaseKeys.contains("LPP")) {
            useCases.add(new EEBusLppServerUseCase(metadataService, ohServiceId,
                    this::ohEntityHandlerForCommunicationAddress, productionSeedWatts, durationSeedSeconds));
        }
        List<String> unimplementedServerUseCases = serverUseCaseKeys.stream()
                .filter(useCase -> !Set.of("MPC", "LPC", "LPP").contains(useCase)).sorted().toList();
        if (!unimplementedServerUseCases.isEmpty()) {
            logger.info("Configured to offer use cases {} - not yet implemented, see CONCEPT.md §7.8",
                    unimplementedServerUseCases);
        }

        // CONCEPT §5.4/§7.3: client-role UseCase implementations (peer use-case detection) are
        // attached the same way - added to the same local entity via withUseCases(...), see
        // jeebus.spine's demo ExampleUseCase (Entity#addUseCase() fills @Inject fields and calls
        // setup() regardless of client/server role). MPC (EEBusMpcClientUseCase, the
        // consumer-side counterpart of EEBusMpcServerUseCase), MGCP (EEBusMgcpClientUseCase,
        // Scenario 2/Total Active Power only - docs/ADR/040-mgcp-client-usecase.md) and LPC/LPP
        // (read-only monitoring only - docs/ADR/015-lpc-lpp-client-role-channels.md) are
        // implemented so far.
        if (clientUseCaseKeys.contains("MPC")) {
            useCases.add(new EEBusMpcClientUseCase(this::ohEntityHandlerForCommunicationAddress));
        }
        if (clientUseCaseKeys.contains("MGCP")) {
            useCases.add(new EEBusMgcpClientUseCase(this::ohEntityHandlerForCommunicationAddress));
        }
        if (clientUseCaseKeys.contains("LPC")) {
            useCases.add(new EEBusLpcClientUseCase(this::ohEntityHandlerForCommunicationAddress,
                    this::ohEgEntityWriteSourceHandler, metadataService));
        }
        if (clientUseCaseKeys.contains("LPP")) {
            useCases.add(new EEBusLppClientUseCase(this::ohEntityHandlerForCommunicationAddress,
                    this::ohEgEntityWriteSourceHandler, metadataService));
        }
        List<String> unimplementedClientUseCases = clientUseCaseKeys.stream()
                .filter(useCase -> !Set.of("MPC", "MGCP", "LPC", "LPP").contains(useCase)).sorted().toList();
        if (!unimplementedClientUseCases.isEmpty()) {
            logger.info("Configured to detect use cases {} - not yet implemented, see CONCEPT.md §7.3",
                    unimplementedClientUseCases);
        }

        return new LocalUseCaseDerivation(useCases, resolvedEntityType);
    }

    /**
     * One local SPINE Entity to build next to (or instead of) the legacy shared Entity: its
     * {@code entityType} and the use cases attached to it. See
     * docs/ADR/053-hems-convenience-entity.md.
     */
    private record LocalEntitySpec(EntityTypeEnumType entityType, List<UseCase> useCases) {
    }

    /**
     * Derives the additional local Entities an {@code eebus:oh-hems-entity} child asks for
     * (docs/ADR/053-hems-convenience-entity.md): a Monitoring Entity (MPC/MGCP Client), a
     * Controllable System Entity (LPC/LPP Server) and one Energy Guard Entity (LPC/LPP Client,
     * scoped to one partner SKI) each for the configured wallbox and heat pump. Returns an empty
     * list if this Bridge has no such child. If more than one exists, the first one found wins and a
     * WARN is logged.
     *
     * @param ohServiceId this Bridge's Thing ID, passed to the Server-role use case constructors
     * @return the HEMS Entities in build order, or an empty list
     */
    private List<LocalEntitySpec> deriveHemsEntities(String ohServiceId) {
        List<Thing> hemsChildren = new ArrayList<>();
        for (Thing child : getThing().getThings()) {
            if (EEBusBindingConstants.THING_TYPE_OH_HEMS_ENTITY.equals(child.getThingTypeUID())) {
                hemsChildren.add(child);
            }
        }
        if (hemsChildren.isEmpty()) {
            return List.of();
        }
        if (hemsChildren.size() > 1) {
            logger.warn(
                    "{}: {} eebus:oh-hems-entity Things found - only the first one ('{}') is used "
                            + "(docs/ADR/053-hems-convenience-entity.md)",
                    thing.getUID(), hemsChildren.size(), hemsChildren.get(0).getUID());
        }
        EEBusOhEntityConfiguration hemsCfg = hemsChildren.get(0).getConfiguration()
                .as(EEBusOhEntityConfiguration.class);

        List<LocalEntitySpec> specs = new ArrayList<>();
        specs.add(new LocalEntitySpec(EntityTypeEnumType.CEM,
                List.of(new EEBusMpcClientUseCase(this::ohEntityHandlerForCommunicationAddress),
                        new EEBusMgcpClientUseCase(this::ohEntityHandlerForCommunicationAddress))));
        specs.add(new LocalEntitySpec(resolveEntityType(hemsCfg.entityType, EntityTypeEnumType.CEM), List.of(
                new EEBusLpcServerUseCase(metadataService, ohServiceId, this::ohEntityHandlerForCommunicationAddress,
                        hemsCfg.failsafeConsumptionLimitSeedWatts, hemsCfg.failsafeDurationMinimumSeedSeconds),
                new EEBusLppServerUseCase(metadataService, ohServiceId, this::ohEntityHandlerForCommunicationAddress,
                        hemsCfg.failsafeProductionLimitSeedWatts, hemsCfg.failsafeDurationMinimumSeedSeconds))));
        EntityTypeEnumType egType = resolveEntityType(hemsCfg.egEntityType, EntityTypeEnumType.GRID_GUARD);
        // Both Energy Guard Entities are always built, even with a blank SKI (a blank SKI matches no partner), so a
        // wallbox or heat pump sees the EnergyGuard use case in the discovery before its SKI is known/trusted.
        {
            // docs/ADR/054: read-only EV use cases (EVSECC, EVCC, EVCEM) on the Monitoring CEM entity, scoped to the
            // wallbox
            String prefix = EEBusBindingConstants.HEMS_PREFIX_WALLBOX;
            List<UseCase> monitoringUseCases = new ArrayList<>(specs.get(0).useCases());
            monitoringUseCases.add(new EEBusEvseccClientUseCase(this::hemsEntityHandler, hemsCfg.wallboxSki, prefix,
                    this::skiForCommunicationAddress));
            monitoringUseCases.add(new EEBusEvccClientUseCase(this::hemsEntityHandler, hemsCfg.wallboxSki, prefix,
                    this::skiForCommunicationAddress));
            monitoringUseCases.add(new EEBusEvcemClientUseCase(this::hemsEntityHandler, hemsCfg.wallboxSki, prefix,
                    this::skiForCommunicationAddress));
            specs.set(0, new LocalEntitySpec(specs.get(0).entityType(), monitoringUseCases));
            specs.add(hemsEnergyGuardSpec(egType, hemsCfg.wallboxSki, EEBusBindingConstants.HEMS_PREFIX_WALLBOX));
        }
        specs.add(hemsEnergyGuardSpec(egType, hemsCfg.heatPumpSki, EEBusBindingConstants.HEMS_PREFIX_HEAT_PUMP));
        return specs;
    }

    /**
     * One Energy Guard Entity (LPC Client only - no LPP for wallbox/heat pump) scoped to a single partner SKI,
     * docs/ADR/053.
     */
    private LocalEntitySpec hemsEnergyGuardSpec(EntityTypeEnumType entityType, String partnerSki, String prefix) {
        return new LocalEntitySpec(entityType,
                List.of(new EEBusLpcClientUseCase(this::ohEntityHandlerForCommunicationAddress, this::hemsEntityHandler,
                        metadataService, partnerSki, prefix, this::skiForCommunicationAddress)));
    }

    /**
     * @return {@code value} as {@link EntityTypeEnumType}, or {@code fallback} (with a WARN) if it
     *         is blank or not a known constant
     */
    private EntityTypeEnumType resolveEntityType(String value, EntityTypeEnumType fallback) {
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return EntityTypeEnumType.fromValue(value);
        } catch (IllegalArgumentException e) {
            logger.warn("{}: configured entityType '{}' is not a known SPINE EntityTypeEnumType constant - "
                    + "falling back to {}", thing.getUID(), value, fallback.value());
            return fallback;
        }
    }

    /**
     * @return the {@link DeviceTypeEnumType} to build this Bridge's shared local SPINE
     *         {@code Device} with, resolved from {@link EEBusConfiguration#deviceType} (see
     *         docs/ADR/044-configurable-devicetype.md). Falls back to
     *         {@link DeviceTypeEnumType#ENERGY_MANAGEMENT_SYSTEM} - the value hardcoded here
     *         before that ADR - if the configured value is blank or does not match a known
     *         constant, the same fallback style {@link #deriveLocalUseCases} already uses for
     *         {@code entityType}.
     */
    private DeviceTypeEnumType resolveDeviceType(EEBusConfiguration cfg) {
        if (cfg.deviceType.isBlank()) {
            return DeviceTypeEnumType.ENERGY_MANAGEMENT_SYSTEM;
        }
        try {
            return DeviceTypeEnumType.fromValue(cfg.deviceType);
        } catch (IllegalArgumentException e) {
            logger.warn("{}: configured deviceType '{}' is not a known SPINE DeviceTypeEnumType constant - "
                    + "falling back to EnergyManagementSystem", thing.getUID(), cfg.deviceType);
            return DeviceTypeEnumType.ENERGY_MANAGEMENT_SYSTEM;
        }
    }

    private boolean startShipSpine(EEBusConfiguration cfg, int resolvedPort, Lifecycle lifecycle, long myGeneration)
            throws Exception {
        // See docs/ADR/028-serialize-startshipspine-per-thing.md: the actual work is now in
        // startShipSpineLocked(), called only while holding lifecycle.startLock, so overlapping
        // generations for the same Thing never touch the keystore file or bind a port
        // concurrently - this closes both the OverlappingFileLockException and the port-pool
        // warnings observed in the 2026-08-26 startup trace (see project memory). A generation
        // that was only queued behind an earlier attempt (never itself superseded mid-flight)
        // exits here immediately, before touching the keystore/port at all - cheaper than the
        // existing post-build supersession check inside startShipSpineLocked(), which is kept
        // unchanged for the case where a *newer* generation arrives while this one is already
        // running.
        lifecycle.startLock.lock();
        try {
            synchronized (lifecycle.lock) {
                if (myGeneration != lifecycle.generation) {
                    logger.debug(
                            "EEBus service instance '{}' was queued behind an earlier start attempt and "
                                    + "was already superseded by the time its turn came; skipping without "
                                    + "touching the keystore/port (generation={}, handler={})",
                            thing.getUID(), myGeneration, System.identityHashCode(this));
                    return false;
                }
            }
            // See docs/ADR/029-retry-startshipspine-on-transient-teardown-race.md: even
            // serialized per-Thing via startLock above, this generation's own attempt can still
            // race the *previous* generation's asynchronous SHIP-layer teardown tail, which
            // dispose() deliberately does not wait for (ADR-005) and which jeebus.ship exposes
            // no completion signal for. Retried here, bounded, with a short backoff - not fixed
            // by blocking dispose() again or by reaching into the protected jeebus.ship project.
            for (int attempt = 1;; attempt++) {
                try {
                    return startShipSpineLocked(cfg, resolvedPort, lifecycle, myGeneration);
                } catch (Exception e) {
                    // See docs/ADR/029-retry-startshipspine-on-transient-teardown-race.md and
                    // project memory (2026-08-27/2026-09-02 startup traces): jeebus.ship's
                    // ShipNodeImpl constructor wraps a keystore-lock failure as a plain
                    // RuntimeException whose cause chain is RuntimeException ->
                    // CertificateStoreException -> OverlappingFileLockException, so a bare
                    // `catch (BindException | OverlappingFileLockException e)` never matched it -
                    // the retry below never actually fired for its most likely real-world
                    // trigger. Unwrap the full cause chain instead of relying on the thrown
                    // exception's own top-level type.
                    if (!isTransientTeardownRaceFailure(e)) {
                        throw e;
                    }
                    if (attempt >= START_RETRY_MAX_ATTEMPTS) {
                        logger.warn(
                                "EEBus service instance '{}' still failing after {} attempt(s), giving up "
                                        + "(generation={}, handler={}, cause={})",
                                thing.getUID(), attempt, myGeneration, System.identityHashCode(this), e.toString());
                        throw e;
                    }
                    synchronized (lifecycle.lock) {
                        if (myGeneration != lifecycle.generation) {
                            logger.debug(
                                    "EEBus service instance '{}' was superseded while retrying a transient "
                                            + "start failure; giving up on this generation without a further "
                                            + "attempt (generation={}, handler={})",
                                    thing.getUID(), myGeneration, System.identityHashCode(this));
                            return false;
                        }
                    }
                    long backoffMillis = START_RETRY_BACKOFF_BASE_MILLIS * attempt;
                    logger.warn(
                            "EEBus service instance '{}' failed to start, likely because the previous "
                                    + "generation's SHIP server/keystore was not yet released; retrying in "
                                    + "{} ms (attempt {}/{}, generation={}, handler={}, cause={})",
                            thing.getUID(), backoffMillis, attempt + 1, START_RETRY_MAX_ATTEMPTS, myGeneration,
                            System.identityHashCode(this), e.toString());
                    Thread.sleep(backoffMillis);
                }
            }
        } finally {
            lifecycle.startLock.unlock();
        }
    }

    /**
     * Whether {@code e} - or any exception in its cause chain - is the transient teardown-race
     * failure {@link #startShipSpine}'s retry loop targets: the previous generation's SHIP
     * server port, or its keystore file lock, not yet released by the time this generation tries
     * to bind/open them itself (see
     * docs/ADR/029-retry-startshipspine-on-transient-teardown-race.md).
     * <p>
     * {@code jeebus.ship} does not always throw {@link BindException}/
     * {@link OverlappingFileLockException} directly - confirmed in a live trace, its
     * {@code ShipNodeImpl} constructor wraps a keystore lock failure as a plain
     * {@link RuntimeException} around a {@code CertificateStoreException} around the actual
     * {@link OverlappingFileLockException}, so the full cause chain has to be walked rather than
     * matching only the caught exception's own top-level type.
     *
     * @param e
     *            the exception caught from {@link #startShipSpineLocked}
     * @return {@code true} if {@code e}, or any exception in its cause chain, is a
     *         {@link BindException} or an {@link OverlappingFileLockException}
     */
    private static boolean isTransientTeardownRaceFailure(Throwable e) {
        // Bounded depth: defends against a pathological/cyclic cause chain rather than looping
        // forever - jeebus.ship's actual wrapping confirmed so far is only 2-3 levels deep.
        Throwable current = e;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getCause()) {
            if (current instanceof BindException || current instanceof OverlappingFileLockException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The actual body of {@link #startShipSpine} - kept as a separate method purely so that
     * method's wrapper (which now serializes calls to this one per-Thing via
     * {@code lifecycle.startLock}, see docs/ADR/028-serialize-startshipspine-per-thing.md) reads
     * clearly; behavior below is otherwise unchanged from before that change.
     */
    private boolean startShipSpineLocked(EEBusConfiguration cfg, int resolvedPort, Lifecycle lifecycle,
            long myGeneration) throws Exception {
        // CONCEPT §7.1 - verified against the actual org.openmuc.jeebus:ship:3.0.1 source
        // (github.com/openmuc/jeebus.ship, tag v3.0.1; originally verified against v2.2.0's
        // now-removed ShipNodeConfiguration, see docs/ADR/049-configbuilder-ship-node-
        // construction.md): KeyStoreCertificateStorage auto-creates the certificate/keystore at
        // its path if none is found there yet (see its loadOrCreateKeyStore()) - same guarantee
        // the previously-used ShipNodeConfiguration certPath constructor made.
        //
        // Persistence note (see docs/ADR/010-storageservice-keystore-mirror.md): the file at
        // getKeystoreFile() remains the actual source of truth handed to
        // KeyStoreCertificateStorage below via ConfigBuilder#withCertificateStorage (ship 3.0.0+
        // makes CertificateStorage pluggable - this binding now uses that pluggability, but still
        // with a file-backed implementation rather than a fully file-free, StorageService-backed
        // one; that would be a separate, larger change, not yet scoped). As a pragmatic middle
        // ground, restoreKeystoreFromStorage()/persistKeystoreToStorage() mirror the file's bytes
        // into openHAB's StorageService (java-coding-rules.md's standard persistence mechanism)
        // around this call, so the identity survives even where the raw file might not (e.g. a
        // userdata folder that isn't part of a backup/restore routine).
        File keystoreFile = getKeystoreFile();
        File keystoreDir = keystoreFile.getParentFile();
        if (keystoreDir != null) {
            keystoreDir.mkdirs();
        }
        restoreKeystoreFromStorage(keystoreFile);

        // docs/ADR/050 (2026-10-05 update "identity is frozen"): a paired peer (verified with a
        // Hager Energy S10) closes the connection when this Bridge's SHIP id / SPINE address
        // changes after pairing. The identity parts are therefore pinned as Thing properties
        // on the first successful start and used from there on; a differing Bridge config is
        // only warned about. Reset = remove and re-create the Thing (same UID keeps the SKI).
        String vendorCode = pinnedIdentity(PROPERTY_IDENTITY_VENDOR_CODE, cfg.vendorCode);
        String deviceModel = pinnedIdentity(PROPERTY_IDENTITY_DEVICE_MODEL, cfg.deviceModel);
        String serialNumber = pinnedIdentity(PROPERTY_IDENTITY_SERIAL_NUMBER, cfg.serialNumber);
        String shipId = vendorCode + "-" + deviceModel + "-" + serialNumber;
        String distinguishedName = "CN=" + deviceModel + "-" + serialNumber;

        // docs/ADR/050-configurable-ship-registration-identity.md (revised 2026-10-05): brand,
        // type and model of this Bridge's own SHIP registration are set explicitly, but the "id"
        // (SHIP:7.3.2 mDNS TXT record and accessMethods) is `shipId` again (vendorCode-
        // deviceModel-serialNumber, same value as the SPINE Device address "d:_n:" + shipId
        // further down) - a real Hager Energy S10 closes the connection right after the
        // accessMethods exchange when it announces the Thing UID id instead. ohServiceId (Thing
        // UID id segment) is still computed here, but only for deriveLocalUseCases().
        String ohServiceId = thing.getUID().getId();

        // docs/ADR/049-configbuilder-ship-node-construction.md: ship 3.0.0 replaced the old
        // thirteen-argument ShipNodeConfiguration constructor with a ConfigBuilder/ShipConfig
        // pair. InetAddress.getByName(...) + InetSocketAddress avoids hand-formatting the
        // "[ipv6]:port" string ConfigBuilder#withServerBindAddresses(String...) would otherwise
        // require for the IPv6-preferred path (see that ADR's "Alternative considered").
        InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName(resolveBindAddress(cfg)),
                resolvedPort);

        // KeyStoreCertificateStorage is a drop-in replacement for the old certPath/alias/
        // keyStorePassphrase/keyPairPassphrase constructor parameters - same file, same alias,
        // same (empty) passphrases. docs/ADR/010-storageservice-keystore-mirror.md's
        // restoreKeystoreFromStorage()/persistKeystoreToStorage() mirroring above is unchanged;
        // it still operates on this same keystoreFile.
        KeyStoreCertificateStorage certificateStorage = new KeyStoreCertificateStorage(keystoreFile.getAbsolutePath(),
                "eebus", new char[0], new char[0]);

        // cfg.connectToPeers defaults to true (normal SHIP behavior: dial trusted peers as soon
        // as mDNS discovers them, in addition to accepting their connections). Set to false only
        // as a diagnostic workaround when pairing two self-built instances against each other -
        // see EEBusConfiguration#connectToPeers and TEST_PAIRING.md (Test 2, "Known Bug
        // Encountered") for why: with both sides dialing out, a bug in the embedded SHIP
        // library's simultaneous-connection handling can abort the handshake.
        //
        // trustedSkis is configured here, on ConfigBuilder, rather than on ShipCommunication as
        // before ADR-049 - ship 3.0.0 removed ShipCommunication's own withTrustedSkis method.
        // Auto-accept is intentionally not configured at all (ConfigBuilder default: off) - see
        // docs/ADR/051-remove-ship-auto-accept.md.
        // withConnectClientsTo below is unaffected - spine 4.1.1 keeps that one on
        // ShipCommunication itself.
        ShipConfig shipConfig = ConfigBuilder.aShipConfig().withId(shipId).withBrand(SHIP_BRAND).withType(SHIP_TYPE)
                .withModel(OpenHAB.getVersion()).withCertificateDistinguishedName(distinguishedName)
                .withCertificateValidity(3650).withCertificateStorage(certificateStorage)
                .withMDnsServiceInstance(cfg.mdnsServiceInstance).withMDnsDomain("local.").withWssPath("/ship/")
                .withKeepAlive(true).withServerBindAddresses(Set.of(bindAddress)).withTrustedSkis(currentTrustedSkis())
                .build();

        ShipCommunication communication = new ShipCommunication(shipConfig)
                .withConnectClientsTo(cfg.connectToPeers ? TRUSTED : NONE);

        // Constructed here - before Device.build() below ever calls communication.connect() -
        // and not, as before, only after build() returns. See the 2026-08-21 "one-shot Discovery
        // notification race" investigation notes (project memory): SPINE's NodeManagement runs
        // UseCasePartner discovery on its own background thread as soon as connect() completes,
        // and calls each registered UseCase's listener exactly once, synchronously, with no
        // retry (jeebus.spine's NodeManagementImpl$Discovery#handleNodeManagementNotification is
        // an unimplemented stub - confirmed by reading that class; a jeebus.spine change
        // is out of scope here without prior approval, see CONCEPT.md). If that one-shot
        // discovery callback raced ahead of this method assigning `this.mdnsBrowser` further
        // down, ohEntityHandlerForCommunicationAddress() saw mdnsBrowser == null and permanently,
        // silently skipped registering that partner's write-path listeners for the rest of this
        // connection's lifetime - this is what produced the "Item switch macht keinerlei traces"
        // symptom even with supportedUseCasesClient/Server configured correctly. Constructing the
        // browser here instead closes that race: it now exists for the entire duration of
        // connect() and the Discovery thread it spawns, regardless of how the two threads are
        // scheduled. communication.getOwnSki() is not yet callable at this point (ShipCommunication
        // does not create its own Ship - and with it, an actual SKI - until connect() runs inside
        // build() below), so it is passed as a Supplier and only actually resolved lazily, on
        // first use, by EEBusMdnsBrowser itself - see that class's ownSkiSupplier javadoc.
        EEBusMdnsBrowser browser = new EEBusMdnsBrowser(mdnsClient, communication::getOwnSki);

        // CONCEPT §5.4/§5.5/§7.3/§7.8: local Use-Case set is no longer read from this Bridge's own
        // config at all - see docs/ADR/027-derive-local-use-cases-from-entities.md. It is derived
        // fresh, every time startShipSpine() runs, from the Entity Things currently attached as
        // this Bridge's children - see deriveLocalUseCases().
        LocalUseCaseDerivation localDerivation = deriveLocalUseCases(ohServiceId);
        List<UseCase> allUseCases = localDerivation.useCases();
        // docs/ADR/053-hems-convenience-entity.md: an eebus:oh-hems-entity child adds its own
        // local Entities next to the legacy shared one.
        List<LocalEntitySpec> hemsEntities = deriveHemsEntities(ohServiceId);

        // The actual network I/O - generates/loads the certificate and binds the SHIP server's
        // port - happens inside build() below: DeviceBuilder.build() calls
        // buildWithoutConnecting() and then device.connect(), which calls
        // Communication.connect() (= ShipCommunication.connect()) internally (confirmed by
        // decompiling the actually-embedded spine-4.0.1 classes, since the checked-out
        // jeebus.spine source did not match). No separate, explicit connect() call is needed or
        // wanted here - one used to exist right after this block, calling
        // communication.connect() a second time on the same, already-connected instance. That
        // was a genuine bug: build() already binds the port, so the second call always bound the
        // same port again and reliably failed with BindException - deterministically, regardless
        // of which port was configured, which is what eventually gave it away (see the
        // investigation that found this, referenced from the git history around this change).
        // Deliberately not holding lifecycle.lock here: this can take a moment, and holding
        // the lock across it would block dispose() (see ADR-005 for why that is unacceptable -
        // it collides with openHAB's own SafeCaller timeout). This means a concurrent dispose()
        // or newer initialize() - on this object or, per the class javadoc, a different
        // EEBusHandler object for the same Thing - can bump the generation while this call is in
        // flight; that is detected and handled immediately below.
        // Diagnostic instrumentation, see the matching comment in initialize(). Marks the moment
        // the actual port bind is attempted, so it can be correlated by generation/handler with
        // the initialize()/dispose() log lines and the eventual success/BindException outcome.
        logger.debug("{}: startShipSpine() about to bind (generation={}, handler={}, thread={})", thing.getUID(),
                myGeneration, System.identityHashCode(this), Thread.currentThread().getName());

        // All local Entities are added before build() connects the Device, so the complete
        // structure is delivered with the initial DetailedDiscovery/UseCaseDiscovery handshake
        // (docs/ADR/027, docs/ADR/053) - the live NodeManagement notification path is never used.
        // The legacy shared Entity is only built if some legacy child contributes a use case, or
        // if there is no HEMS child at all (unchanged behaviour for every pre-ADR-053 setup).
        var deviceBuilder = Device.getBuilder().withDeviceType(resolveDeviceType(cfg)).withCommunication(communication)
                .withId("d:_n:" + shipId).withDiscoverDevices(true);
        if (hemsEntities.isEmpty() || !allUseCases.isEmpty()) {
            deviceBuilder = deviceBuilder.addEntity().setType(localDerivation.entityType())
                    .withUseCases(allUseCases.toArray(new UseCase[0])).applyToDevice();
        }
        for (LocalEntitySpec hemsEntity : hemsEntities) {
            deviceBuilder = deviceBuilder.addEntity().setType(hemsEntity.entityType())
                    .withUseCases(hemsEntity.useCases().toArray(new UseCase[0])).applyToDevice();
        }
        Device localDevice = deviceBuilder.build();

        boolean supersededWhileConnecting;
        synchronized (lifecycle.lock) {
            supersededWhileConnecting = myGeneration != lifecycle.generation;
            if (!supersededWhileConnecting) {
                this.shipCommunication = communication;
                this.device = localDevice;
                // CONCEPT §4.1/§7.9: own mDNS browser for _ship._tcp.local., since jeebus.ship's
                // own mDNS classes (org.openmuc.jeebus.ship.node.service.*) are not OSGi-exported
                // and ShipCommunication does not expose raw mDNS events. Built on openHAB core's
                // shared MDNSClient service (not a private JmDNS instance) - see
                // EEBusMdnsBrowser's class javadoc. Used for (a) a future discovery inbox with
                // friendly names (requirement 3) and (b) resolving
                // UseCasePartner#getCommunicationAddress() back to a peer's SKI (§7.2).
                // Constructed further up, before build() - see the comment there for why.
                this.mdnsBrowser = browser;
                logger.debug("{}: startShipSpine() bound and published successfully (generation={}, handler={})",
                        thing.getUID(), myGeneration, System.identityHashCode(this));
            }
        }

        if (!supersededWhileConnecting) {
            this.ownSki = communication.getOwnSki();
            this.lastBoundMillis = System.currentTimeMillis();
            RUNNING.put(thing.getUID(), this);
            refreshLocalPeers();
        }

        if (supersededWhileConnecting) {
            // Superseded by a newer initialize()/dispose() while we were connecting. Do not
            // publish this instance anywhere reachable - shut the SHIP server we just bound back
            // down instead, so its port is not leaked. See ADR-005.
            //
            // 2026-09-06 FIX: the actual close() calls below used to run *inside* the
            // synchronized(lifecycle.lock) block above. A live trace that same day caught this
            // directly: a superseded generation's teardown here took ~59s end to end (jeebus.ship
            // connection-close latency under investigation, see project memory's dispose()/stop()
            // latency entry), and because lifecycle.lock was held for the whole thing, the *next*
            // generation's own startShipSpineLocked() call - which needs the same lock just to
            // get started - sat blocked for that entire ~59s before it could even attempt its own
            // bind. That is what made a plain "slow teardown of an abandoned attempt" look like
            // the new, current generation itself hanging. dispose() already avoided this exact
            // trap (see its own ADR-005 comment); this branch now does the same: decide whether we
            // were superseded, and publish or not, while holding the lock only for that quick
            // decision - then release it before doing anything that can block.
            logger.debug("EEBus service instance '{}' was disposed/reconfigured while starting; shutting the "
                    + "now-superseded SHIP server back down", thing.getUID());
            // DIAGNOSTICS DISABLED 2026-10-05 (dispose()/stop() latency investigation): commented out, not deleted.
            // Re-enable by uncommenting this block together with the other blocks marked the same way.
            // ScheduledFuture<?> stallDumpTask = scheduleTeardownStallWatchdog(thing.getUID(), myGeneration,
            // "startShipSpineLocked() supersession teardown");
            // try {
            // teardownAll(thing.getUID(), myGeneration, communication, localDevice, browser);
            // } finally {
            // stallDumpTask.cancel(false);
            // }
            teardownAll(thing.getUID(), myGeneration, communication, localDevice, browser);
            return false;
        }

        updateProperty(EEBusBindingConstants.PROPERTY_LOCAL_SKI, communication.getOwnSki());
        // Pin the identity (no-op once pinned) - see pinnedIdentity().
        updateProperty(PROPERTY_IDENTITY_VENDOR_CODE, vendorCode);
        updateProperty(PROPERTY_IDENTITY_DEVICE_MODEL, deviceModel);
        updateProperty(PROPERTY_IDENTITY_SERIAL_NUMBER, serialNumber);
        persistKeystoreToStorage(keystoreFile);
        return true;
    }

    @Override
    public void dispose() {
        // Deliberately non-blocking (see ADR-005: an earlier design that waited here for an
        // in-flight startShipSpine() to finish collided with openHAB's own SafeCaller timeout on
        // dispose(), which made things worse, not better). Bumping the generation is enough: any
        // background task - from this object or, per the class javadoc, a different EEBusHandler
        // object for the same Thing - that is still connecting will see the mismatch when it
        // re-checks right after connect() returns, and will shut itself back down instead of
        // publishing - see startShipSpine(). Here, we only ever need to close whatever this
        // object instance already published before this call started.
        @Nullable
        ShipCommunication communication;
        @Nullable
        Device localDevice;
        @Nullable
        EEBusMdnsBrowser browser;
        Lifecycle lifecycle = lifecycle();
        long generation;
        @Nullable
        ScheduledFuture<?> pendingRebuild;
        synchronized (lifecycle.lock) {
            generation = ++lifecycle.generation;
            RUNNING.remove(thing.getUID(), this);
            communication = this.shipCommunication;
            localDevice = this.device;
            browser = this.mdnsBrowser;
            this.shipCommunication = null;
            this.device = null;
            this.mdnsBrowser = null;
            pendingRebuild = lifecycle.pendingRebuild;
            lifecycle.pendingRebuild = null;
        }
        // Cancel a still-pending onEntityChanged() debounce timer (docs/ADR/028-serialize-
        // startshipspine-per-thing.md) - dispose() is about to tear everything down, so a stale
        // timer firing afterward and calling initialize() again would incorrectly resurrect a
        // Thing that is being disposed for a real reason (e.g. the user disabled/removed it),
        // not just rebuilt via onEntityChanged(). Harmless no-op if there was none, or if it
        // already started running (cancel(false) does not interrupt a running task).
        if (pendingRebuild != null) {
            pendingRebuild.cancel(false);
        }

        // Diagnostic instrumentation, see the matching comment in initialize(). "hadCommunication"
        // shows whether this dispose() actually found a published instance to tear down, or ran
        // against an object that never got that far.
        logger.debug("dispose() called for {} (handler={}, generation={}, thread={}, hadCommunication={})",
                thing.getUID(), System.identityHashCode(this), generation, Thread.currentThread().getName(),
                communication != null);

        // Diagnostic watchdog, see TEARDOWN_STALL_DUMP_DELAY_MILLIS/scheduleTeardownStallWatchdog:
        // if the teardown below is still running after TEARDOWN_STALL_DUMP_DELAY_MILLIS, dump
        // every JVM thread's full stack so we can see - the next time this fires live - exactly
        // what the calling thread and jeebus.ship's Netty event-loop thread(s) are each blocked
        // on. Cancelled in the finally block below the moment teardown actually finishes, so a
        // normal fast dispose() never logs anything extra.
        // DIAGNOSTICS DISABLED 2026-10-05 (dispose()/stop() latency investigation): commented out, not deleted.
        // Re-enable by uncommenting this block together with the other blocks marked the same way.
        // ScheduledFuture<?> stallDumpTask = scheduleTeardownStallWatchdog(thing.getUID(), generation, "dispose()");
        // try {
        // teardownAll(thing.getUID(), generation, communication, localDevice, browser);
        // } finally {
        // stallDumpTask.cancel(false);
        // }
        teardownAll(thing.getUID(), generation, communication, localDevice, browser);
    }

    /**
     * Fail-safe teardown shared by {@link #dispose()} and {@link #startShipSpineLocked}'s
     * supersession branch. Each of the three steps runs in its own try/catch, so a throwing (or
     * interrupted - openHAB's SafeCaller may interrupt a slow dispose()) {@code
     * communication.disconnect()} can no longer skip {@code localDevice.close()}: a Device that is
     * never closed keeps its HeartbeatDataFunction executor and its SHIP client connection alive,
     * so the old local device kept sending DeviceDiagnosis heartbeats to the peer long after the
     * Thing was removed. An interrupt is remembered and re-asserted on the thread at the very end,
     * after all three steps have had their chance to run.
     */
    private void teardownAll(ThingUID uid, long generation, @Nullable ShipCommunication communication,
            @Nullable Device localDevice, @Nullable EEBusMdnsBrowser browser) {
        boolean interrupted = false;
        if (communication != null) {
            try {
                communication.disconnect();
            } catch (RuntimeException t) { // must never skip the remaining teardown steps
                interrupted |= Thread.interrupted();
                logger.warn("{}: communication.disconnect() failed during teardown (generation={}); continuing", uid,
                        generation, t);
            }
        }
        if (localDevice != null) {
            try {
                // Device extends Shutdownable (AutoCloseable with a no-throws close()) - see
                // org.openmuc.jeebus.spine.api.Shutdownable.
                localDevice.close();
            } catch (RuntimeException t) {
                interrupted |= Thread.interrupted();
                logger.warn("{}: localDevice.close() failed during teardown (generation={}); continuing", uid,
                        generation, t);
            }
        }
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException t) {
                interrupted |= Thread.interrupted();
                logger.warn("{}: browser.close() failed during teardown (generation={}); continuing", uid, generation,
                        t);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // DIAGNOSTICS DISABLED 2026-10-05 (dispose()/stop() latency investigation): commented out, not deleted.
    // Re-enable by uncommenting this block together with the other blocks marked the same way.
    // /**
    // * Diagnostic only, see {@link #TEARDOWN_STALL_DUMP_DELAY_MILLIS}. Logs a full stack trace for
    // * every live JVM thread at WARN level, so a hung teardown (from {@link #dispose()} or from
    // * {@link #startShipSpineLocked}'s supersession-cleanup branch, distinguished by {@code
    // * context}) can be correlated against jeebus.ship's Netty event-loop thread(s) without
    // * needing to attach {@code jstack} live. Deliberately not filtered to a thread subset up
    // * front - Netty's default thread naming varies by version/config, so filtering here risked
    // * hiding the very thread we need to see. May be called repeatedly for the same stalled
    // * teardown, see {@link #TEARDOWN_STALL_DUMP_PERIOD_MILLIS}; {@code dumpNumber}/{@code
    // * stalledForMillis} say which repetition this is so the log makes the stall's progression
    // * over time obvious.
    // */
    // private void logTeardownStallThreadDump(ThingUID uid, long generation, String stalledThreadName, String context,
    // int dumpNumber, long stalledForMillis) {
    // ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
    // boolean withLocks = threadMxBean.isObjectMonitorUsageSupported() && threadMxBean.isSynchronizerUsageSupported();
    // ThreadInfo[] allThreads = threadMxBean.dumpAllThreads(withLocks, withLocks);
    // StringBuilder sb = new StringBuilder();
    // sb.append("EEBUS TEARDOWN STALL in ").append(context).append(" for ").append(uid).append(" (generation=")
    // .append(generation).append(", calling thread='").append(stalledThreadName)
    // .append("') has not returned after ").append(stalledForMillis).append("ms (dump #").append(dumpNumber)
    // .append(") - full JVM thread dump follows (look for '").append(stalledThreadName)
    // .append("' and for jeebus.ship's Netty event-loop thread(s)):");
    // for (ThreadInfo info : allThreads) {
    // sb.append("\n\n\"").append(info.getThreadName()).append("\" Id=").append(info.getThreadId()).append(' ')
    // .append(info.getThreadState());
    // String lockName = info.getLockName();
    // if (lockName != null) {
    // sb.append(" on ").append(lockName);
    // }
    // for (StackTraceElement element : info.getStackTrace()) {
    // sb.append("\n\tat ").append(element);
    // }
    // }
    // logger.warn("{}", sb);
    // }

    @Override
    public void handleRemoval() {
        // Only discard the shared Lifecycle when the Thing is actually deleted, not on every
        // dispose()/update cycle - see java-coding-rules.md's StorageService lifecycle pattern
        // for the same dispose()-vs-handleRemoval() distinction. LIFECYCLES otherwise grows by
        // one small entry per Thing UID ever created, which is harmless short-term but unbounded
        // over a long-running instance's lifetime.
        LIFECYCLES.remove(thing.getUID());

        // Same dispose()-vs-handleRemoval() distinction applies to the port reserved from
        // portPool in initialize()/resolvePort(): only give it back once the Thing is actually
        // deleted, not on every restart/reconfigure, or a still-running Bridge could lose its
        // port to a different Bridge mid-restart. See EEBusPortPool's class javadoc.
        Integer port = this.reservedPort;
        if (port != null) {
            portPool.releasePort(port);
            this.reservedPort = null;
        }

        // Deliberately NOT removing the keystoreStorage entry here, unlike the otherwise-standard
        // StorageService handleRemoval() pattern (java-coding-rules.md). Keeping it lets a
        // `service` Bridge later recreated with the exact same Thing ID pick its old SHIP
        // certificate/SKI back up instead of generating a new identity - see README.md ("The
        // Thing ID matters") and SKI.md ("Deleting and recreating the oh-device Bridge itself").
        // The already-existing keystore *file* on disk survives Thing deletion for the same
        // reason (see getKeystoreFile()'s javadoc reference above); this just keeps the
        // StorageService mirror consistent with that existing, intentional behavior. If a Thing
        // ID is retired for good, the stray entry is one small base64 keystore keyed by a now
        // unused UID - harmless clutter, not a correctness or security issue.
        updateStatus(ThingStatus.REMOVED);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined on the Bridge itself.
    }

    // Note (ADR-003): this Bridge no longer registers a Bridge-scoped discovery service in the
    // sense that phrase originally meant here. Real-device discovery is still binding-scoped
    // (EEBusMdnsDiscoveryParticipant, independent of this handler's lifecycle), not Bridge-scoped
    // - see CONCEPT.md §4.1. The getServices() override below is unrelated - it registers
    // EEBusTopologyActions (Thing Actions), not a discovery service.

    // getServices() override re-added by docs/ADR/043-topology-phase-gate.md, after having been
    // removed by docs/ADR/027-derive-local-use-cases-from-entities.md Decision 6 (which only
    // ever registered the old EEBusDeviceActions trust()/untrust() actions, removed there for
    // good reason - see that ADR). settleTopology() is a genuinely different case: it is a
    // deliberate, human-triggered step that config changes must NOT trigger by themselves, so a
    // live Actions class is needed again. See EEBusTopologyActions.
    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(EEBusTopologyActions.class);
    }

    // Note (ADR-003): the public pairedSkis() accessor that used to exist here was removed -
    // its only caller, the old Bridge-scoped EEBusDiscoveryService, is superseded by
    // EEBusMdnsDiscoveryParticipant, which checks all eebus:hw-device Things across the whole
    // ThingRegistry directly instead of asking one specific eebus:oh-device Bridge.

    /**
     * {@link EEBusEntityChangeListener} implementation - called by a child
     * {@link EEBusOhEntityHandler} ({@code eebus:oh-entity}/{@code eebus:oh-cs-entity}/
     * {@code eebus:oh-eg-entity}) whenever it is initialized, disposed, or removed. Schedules a
     * full rebuild of this Bridge's local SPINE {@code Device} - {@link #dispose()} then
     * {@link #initialize()}, the exact same sequence openHAB itself would run for any other
     * Thing-config change (see docs/ADR/027-derive-local-use-cases-from-entities.md, "Every
     * configuration change is now uniformly a full rebuild") - after a short debounce window
     * ({@code ENTITY_CHANGE_DEBOUNCE_MILLIS}, see docs/ADR/028-serialize-startshipspine-per-
     * thing.md) rather than immediately, so a burst of near-simultaneous calls (e.g. several
     * children attaching at startup) coalesces into a single rebuild instead of one per call.
     * Safe to call repeatedly, concurrently, or during this Bridge's own teardown: both
     * {@link #dispose()} and {@link #initialize()} are already guarded by the generation-counter
     * scheme described in the class javadoc and ADR-005, so a redundant or overlapping rebuild
     * simply gets superseded and shuts itself back down instead of corrupting state; a rebuild
     * still pending when {@link #dispose()} runs for a real (non-debounce) reason is cancelled,
     * not left to fire later (see the cancellation in {@link #dispose()}).
     */
    @Override
    public void onEntityChanged() {
        // Debounced (docs/ADR/028-serialize-startshipspine-per-thing.md): a burst of child
        // Entity Things attaching/detaching in quick succession (e.g. at startup) used to
        // trigger one full dispose()+initialize() rebuild *per* call, multiplying the number of
        // overlapping generations startShipSpineLocked() has to sort out. Coalescing them into
        // one rebuild after a short quiet window fixes that at the source, on top of (not
        // instead of) the startLock serialization in startShipSpine().
        Lifecycle lifecycle = lifecycle();
        synchronized (lifecycle.lock) {
            ScheduledFuture<?> pending = lifecycle.pendingRebuild;
            if (pending != null) {
                pending.cancel(false);
            }
            lifecycle.pendingRebuild = scheduler.schedule(() -> {
                synchronized (lifecycle.lock) {
                    lifecycle.pendingRebuild = null;
                }
                logger.debug(
                        "{}: onEntityChanged() - rebuilding local SPINE Device from current children "
                                + "(debounced, {} ms quiet window elapsed)",
                        thing.getUID(), ENTITY_CHANGE_DEBOUNCE_MILLIS);
                dispose();
                initialize();
            }, ENTITY_CHANGE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * After this Bridge (re)started, rebuilds every other Bridge of this binding in the same JVM
     * that is paired with it (either side lists the other's SKI as trusted), so no half-open
     * SHIP connection state of the previous session survives on the peer. Observed 2026-10-07:
     * restarting only the HEMS Bridge left the Energy Guard Bridge unable to reconnect (handshake
     * aborted, no further dial). A peer that itself started within
     * {@link #PEER_REFRESH_COOLDOWN_MILLIS} is skipped, which also stops the refresh from
     * ping-ponging between the two Bridges. See docs/ADR/055-restart-local-peer-bridges.md.
     */
    private void refreshLocalPeers() {
        @Nullable
        String mySki = this.ownSki;
        if (mySki == null) {
            return;
        }
        Set<String> myTrusted = currentTrustedSkis();
        long now = System.currentTimeMillis();
        for (EEBusHandler other : RUNNING.values()) {
            if (this.equals(other)) {
                continue;
            }
            @Nullable
            String otherSki = other.ownSki;
            boolean paired = other.currentTrustedSkis().contains(mySki)
                    || (otherSki != null && myTrusted.contains(otherSki));
            if (!paired || now - other.lastBoundMillis < PEER_REFRESH_COOLDOWN_MILLIS) {
                continue;
            }
            logger.info("{}: restarted - also rebuilding paired local Bridge {} to drop stale SHIP connection state",
                    thing.getUID(), other.getThing().getUID());
            other.onEntityChanged();
        }
    }

    /**
     * @return this Bridge's currently configured {@link EEBusConfiguration#trustedSkis}, minus
     *         any blank entries, as a {@link Set}. Empty if {@link #config} has not been resolved
     *         yet (before {@link #initialize()} finishes reading it).
     */
    private Set<String> currentTrustedSkis() {
        EEBusConfiguration cfg = this.config;
        if (cfg == null) {
            return Set.of();
        }
        Set<String> skis = new HashSet<>();
        for (String ski : cfg.trustedSkis) {
            if (!ski.isBlank()) {
                skis.add(ski);
            }
        }
        return skis;
    }

    /**
     * @param ski the SKI to check
     * @return {@code true} if {@code ski} is currently in this Bridge's trusted-SKI set
     *         ({@link #currentTrustedSkis()}). Used by {@code EEBusOhEntityHandler#applyStatus()}
     *         to decide whether a child {@code eebus:oh-entity} Thing configured with this SKI
     *         should be {@code ONLINE}.
     */
    boolean isTrusted(String ski) {
        return currentTrustedSkis().contains(ski);
    }

    /**
     * Resolves a SKI to the trusted {@code eebus:oh-entity} child Thing's
     * {@link EEBusOhEntityHandler}, if a Thing with that SKI is currently configured and its
     * handler is initialized. Used to compose the {@code ohEntityHandlerResolver} passed to
     * {@code EEBusMpcClientUseCase} (CONCEPT.md §7.3/§8, docs/ADR/014-dynamic-client-role-
     * channels.md): combined with {@link EEBusMdnsBrowser#skiForCommunicationAddress}, this
     * resolves a SPINE {@code communicationAddress} all the way back to the trusted
     * {@code eebus:oh-entity} Thing's live handler (CONCEPT.md §4.5), so Client-role use cases
     * can call handler methods like {@code EEBusOhEntityHandler#applyMpcPower} directly instead
     * of only knowing a UID string.
     *
     * <p>
     * <strong>Known limitation</strong> (docs/ADR/024-oh-device-oh-entity-rename.md "Out of
     * scope"): resolves by {@code ski} only (Device-level), returning the first matching child
     * Thing. If more than one {@code eebus:oh-entity} Thing shares the same {@code ski}
     * (multiple SPINE Entities configured on the same device), only that first match is
     * resolved here - {@code entityAddress} is not yet used to disambiguate between them.
     * </p>
     *
     * @param ski the SKI to look up
     * @return the matching child Thing's handler, if any
     */
    Optional<EEBusOhEntityHandler> ohEntityHandlerForSki(String ski) {
        for (Thing child : getThing().getThings()) {
            EEBusOhEntityConfiguration ohEntityConfig = child.getConfiguration().as(EEBusOhEntityConfiguration.class);
            // docs/ADR/053: a HEMS child is the Energy Guard partner's Thing for its CLS gateway SKI.
            boolean matches = ski.equals(ohEntityConfig.ski)
                    || (EEBusBindingConstants.THING_TYPE_OH_HEMS_ENTITY.equals(child.getThingTypeUID())
                            && !ohEntityConfig.gatewaySki.isBlank() && ski.equals(ohEntityConfig.gatewaySki));
            if (matches && child.getHandler() instanceof EEBusOhEntityHandler ohEntityHandler) {
                return Optional.of(ohEntityHandler);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves this Bridge's single {@code eebus:oh-eg-entity} child Thing, by Thing type -
     * <strong>not</strong> by SKI (contrast {@link #ohEntityHandlerForSki}). See docs/ADR/047-
     * fanout-energyguard-writes-to-all-partners.md: as of this ADR, one {@code oh-eg-entity}
     * Thing's tagged write-path Items are fanned out to every trusted partner this Bridge's
     * EnergyGuard (LPC/LPP Client) use cases find, instead of requiring one Thing per partner
     * SKI - so the write path needs to find "the" {@code oh-eg-entity} Thing, not "the Thing for
     * partner X". If more than one {@code oh-eg-entity} Thing exists under this Bridge (a
     * pre-ADR-047 multi-Thing setup not yet consolidated), the first one found is used - multiple
     * write-source Things is not a supported configuration going forward, but nothing here
     * actively rejects it either.
     *
     * @return the first {@code eebus:oh-eg-entity} child Thing's handler, if any is currently
     *         configured and initialized
     */
    Optional<EEBusOhEntityHandler> ohEgEntityWriteSourceHandler() {
        for (Thing child : getThing().getThings()) {
            if (EEBusBindingConstants.THING_TYPE_OH_EG_ENTITY.equals(child.getThingTypeUID())
                    && child.getHandler() instanceof EEBusOhEntityHandler ohEntityHandler) {
                return Optional.of(ohEntityHandler);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves this Bridge's {@code eebus:oh-hems-entity} child Thing by Thing type
     * (docs/ADR/053-hems-convenience-entity.md) - the write source of the target-scoped HEMS
     * Energy Guard use cases.
     *
     * @return the first {@code eebus:oh-hems-entity} child's handler, if configured and initialized
     */
    Optional<EEBusOhEntityHandler> hemsEntityHandler() {
        for (Thing child : getThing().getThings()) {
            if (EEBusBindingConstants.THING_TYPE_OH_HEMS_ENTITY.equals(child.getThingTypeUID())
                    && child.getHandler() instanceof EEBusOhEntityHandler ohEntityHandler) {
                return Optional.of(ohEntityHandler);
            }
        }
        return Optional.empty();
    }

    /**
     * @param communicationAddress a SPINE {@code UseCasePartner#getCommunicationAddress()}
     * @return the partner's SKI via the mDNS browser, or empty if the browser is not started yet or does not
     *         know the address (docs/ADR/053: used to scope a HEMS Energy Guard to one device)
     */
    private Optional<String> skiForCommunicationAddress(String communicationAddress) {
        EEBusMdnsBrowser browser = this.mdnsBrowser;
        return browser == null ? Optional.empty() : browser.skiForCommunicationAddress(communicationAddress);
    }

    /**
     * Composes {@link EEBusMdnsBrowser#skiForCommunicationAddress} with
     * {@link #ohEntityHandlerForSki} - the full {@code communicationAddress -> oh-entity handler}
     * resolver that {@code EEBusMpcClientUseCase} (and future Client-role UseCases) need to
     * route a detected {@code UseCasePartner} back to the trusted {@code eebus:oh-entity} Thing's
     * handler. Read lazily (not captured at construction time) so it keeps working regardless of
     * {@link #mdnsBrowser}'s startup order relative to Client-UseCase registration.
     */
    private Optional<EEBusOhEntityHandler> ohEntityHandlerForCommunicationAddress(String communicationAddress) {
        EEBusMdnsBrowser browser = this.mdnsBrowser;
        if (browser == null) {
            logger.debug("{}: cannot resolve communicationAddress '{}' to an oh-entity handler - mdnsBrowser not "
                    + "started yet", thing.getUID(), communicationAddress);
            return Optional.empty();
        }
        // Diagnostic instrumentation (2026-08-21, see project memory "Client-role write-path
        // resolution failure investigation"): split out the two distinct ways this resolution can
        // fail, so a live log can tell them apart instead of only ever seeing the generic
        // "could not be resolved ... skipping" line logged by the calling UseCase. Candidate
        // causes under investigation: (a) the mDNS-derived communicationAddress -> SKI map
        // genuinely has no entry for this address (see EEBusMdnsBrowser's own DEBUG logs for
        // that case), vs. (b) the SKI resolves fine, but the trusted eebus:oh-entity child Thing's
        // handler is not yet available (e.g. a startup race right after a Bridge restart, where
        // SHIP/SPINE's NodeManagement discovery can fire before the child Thing's own handler has
        // finished (re)initializing).
        Optional<String> ski = browser.skiForCommunicationAddress(communicationAddress);
        if (ski.isEmpty()) {
            logger.debug("{}: communicationAddress '{}' could not be resolved to a known SKI via mDNS (see "
                    + "EEBusMdnsBrowser DEBUG logs for detail)", thing.getUID(), communicationAddress);
            return Optional.empty();
        }
        Optional<EEBusOhEntityHandler> handler = ohEntityHandlerForSki(ski.get());
        if (handler.isEmpty()) {
            logger.debug(
                    "{}: communicationAddress '{}' resolved to SKI '{}' via mDNS, but no trusted eebus:oh-entity "
                            + "child Thing with that SKI currently has an initialized handler (child Thing not "
                            + "yet ONLINE / handler still starting up after a Bridge restart?)",
                    thing.getUID(), communicationAddress, ski.get());
        }
        return handler;
    }

    /**
     * Returns the pinned identity part stored as Thing property {@code key}, or {@code configured}
     * if none is pinned yet (first start). Logs a warning if the Bridge config differs from the
     * pinned value, because the pinned one stays in effect - see the comment at the call site.
     */
    private String pinnedIdentity(String key, String configured) {
        String pinned = thing.getProperties().get(key);
        if (pinned == null || pinned.isBlank()) {
            return configured;
        }
        if (!pinned.equals(configured)) {
            logger.warn(
                    "{}: Bridge config {} '{}' differs from the identity pinned at first start ('{}'); keeping '{}'. "
                            + "A changed identity breaks existing pairings (e.g. Hager Energy S10). To really "
                            + "change it, remove and re-create the Thing (same UID keeps the SKI) and re-pair peers.",
                    thing.getUID(), key, configured, pinned, pinned);
        }
        return pinned;
    }

    private File getKeystoreFile() {
        return new File(OpenHAB.getUserDataFolder() + File.separator + "eebus",
                thing.getUID().getAsString().replace(':', '_') + ".jks");
    }

    /** The single key used inside {@link #keystoreStorage} - one entry per Thing UID. */
    private static final String KEYSTORE_STORAGE_KEY = "keystore";

    /**
     * Restores {@code keystoreFile} from {@link #keystoreStorage}, if openHAB's StorageService
     * has a mirror of it (persisted by an earlier {@link #persistKeystoreToStorage(File)} call)
     * but the file itself is missing on disk. Called from {@link #startShipSpine} before
     * constructing {@code KeyStoreCertificateStorage}
     * (docs/ADR/049-configbuilder-ship-node-construction.md), which would otherwise treat a
     * missing file as "no certificate yet" and silently generate a brand-new one - changing this
     * node's SKI, see README.md/SKI.md.
     *
     * <p>
     * Deliberately a no-op whenever the file already exists: the file is the operational source
     * of truth (it's what {@code KeyStoreCertificateStorage} actually reads/writes), so an
     * existing file is never overwritten from a possibly-older storage snapshot.
     * </p>
     *
     * @param keystoreFile this Thing's keystore file, as returned by {@link #getKeystoreFile()}
     */
    private void restoreKeystoreFromStorage(File keystoreFile) throws IOException {
        Storage<String> storage = this.keystoreStorage;
        if (storage == null || keystoreFile.exists()) {
            return;
        }
        String encoded = storage.get(KEYSTORE_STORAGE_KEY);
        if (encoded == null) {
            // Nothing persisted yet for this Thing UID (e.g. first-ever start, or a Thing ID that
            // was never started before this change was introduced) - KeyStoreCertificateStorage
            // will create a brand-new certificate below, exactly as ShipNodeConfiguration always
            // did before docs/ADR/049-configbuilder-ship-node-construction.md.
            return;
        }
        Files.write(keystoreFile.toPath(), Base64.getDecoder().decode(encoded));
        logger.debug("Restored SHIP keystore for {} from StorageService ({} bytes)", thing.getUID(),
                keystoreFile.length());
    }

    /**
     * Mirrors {@code keystoreFile}'s current bytes into {@link #keystoreStorage}, base64-encoded.
     * Called from {@link #startShipSpine} right after a successful start, so the identity this
     * Thing just established/confirmed on disk is also available through openHAB's normal
     * StorageService persistence (java-coding-rules.md) - not only as a loose file under
     * userdata.
     *
     * @param keystoreFile this Thing's keystore file, as returned by {@link #getKeystoreFile()}
     */
    private void persistKeystoreToStorage(File keystoreFile) {
        Storage<String> storage = this.keystoreStorage;
        if (storage == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(keystoreFile.toPath());
            storage.put(KEYSTORE_STORAGE_KEY, Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            // Non-fatal: the file on disk is still fully functional for this run, this only means
            // the StorageService mirror is stale until the next successful start.
            logger.warn("Failed to mirror SHIP keystore for {} into StorageService (file at {} is unaffected)",
                    thing.getUID(), keystoreFile, e);
        }
    }

    /** The single key used inside {@link #topologyStorage}. See docs/ADR/043-topology-phase-gate.md. */
    private static final String TOPOLOGY_PHASE_STORAGE_KEY = "topologyPhase";

    /**
     * @return this Bridge's persisted {@link EEBusTopologyPhase}, defaulting to
     *         {@link EEBusTopologyPhase#ASSEMBLING} if nothing has been persisted yet (a
     *         brand-new Bridge) or if {@link #topologyStorage} is not yet available (called
     *         before {@link #initialize()} has run). See docs/ADR/043-topology-phase-gate.md.
     */
    private EEBusTopologyPhase currentTopologyPhase() {
        Storage<String> storage = this.topologyStorage;
        if (storage == null) {
            return EEBusTopologyPhase.ASSEMBLING;
        }
        String persisted = storage.get(TOPOLOGY_PHASE_STORAGE_KEY);
        if (persisted == null) {
            return EEBusTopologyPhase.ASSEMBLING;
        }
        try {
            return EEBusTopologyPhase.valueOf(persisted);
        } catch (IllegalArgumentException e) {
            logger.warn("{}: unrecognized persisted topology phase '{}', defaulting to ASSEMBLING", thing.getUID(),
                    persisted);
            return EEBusTopologyPhase.ASSEMBLING;
        }
    }

    private void persistTopologyPhase(EEBusTopologyPhase phase) {
        Storage<String> storage = this.topologyStorage;
        if (storage == null) {
            logger.warn("{}: cannot persist topology phase {} - topologyStorage not yet available "
                    + "(initialize() has not run?)", thing.getUID(), phase);
            return;
        }
        storage.put(TOPOLOGY_PHASE_STORAGE_KEY, phase.name());
    }

    /**
     * Called by {@link EEBusTopologyActions#settleTopology()} - the manual Thing Action that
     * moves this Bridge from {@link EEBusTopologyPhase#ASSEMBLING} to
     * {@link EEBusTopologyPhase#SETTLED}. See docs/ADR/043-topology-phase-gate.md.
     * <p>
     * A no-op if this Bridge is already {@code SETTLED} - deliberately does not tear down and
     * rebuild an already-running connection just because the Action was pressed again.
     * Otherwise, persists {@code SETTLED} and re-runs {@link #initialize()} directly: nothing
     * was actually started while {@code ASSEMBLING} (no port reserved, no keystore/certificate
     * touched, no {@code Device} built), so there is nothing for a {@link #dispose()} to tear
     * down first - unlike {@link #onEntityChanged()}'s debounced {@code dispose()}+
     * {@code initialize()} rebuild, which exists for the opposite case (an already-running
     * connection that must be torn down before it can be rebuilt).
     * </p>
     */
    void settleTopology() {
        if (currentTopologyPhase() == EEBusTopologyPhase.SETTLED) {
            logger.debug("{}: settleTopology() called but topology is already SETTLED; ignoring", thing.getUID());
            return;
        }
        logger.info("{}: topology settled via settleTopology() Thing Action; starting SPINE connection",
                thing.getUID());
        persistTopologyPhase(EEBusTopologyPhase.SETTLED);
        initialize();
    }

    /**
     * Called by {@link EEBusTopologyActions#unsettleTopology()} - the manual Thing Action added
     * (see docs/ADR/043-topology-phase-gate.md, "Update 2026-09-04") specifically as a
     * development/testing convenience: moves this Bridge back from
     * {@link EEBusTopologyPhase#SETTLED} to {@link EEBusTopologyPhase#ASSEMBLING}, the reverse of
     * {@link #settleTopology()}.
     * <p>
     * A no-op if this Bridge is already {@code ASSEMBLING}. Otherwise, persists
     * {@code ASSEMBLING} and runs a full {@link #dispose()} + {@link #initialize()} rebuild -
     * unlike {@link #settleTopology()}, there generally IS a live SHIP/SPINE connection to tear
     * down here (this Bridge was {@code SETTLED} and presumably connected), so {@code dispose()}
     * is needed first, exactly as {@link #onEntityChanged()}'s own rebuild does. The following
     * {@link #initialize()} then immediately re-checks the (now persisted) {@code ASSEMBLING}
     * phase and returns early, same as any other Bridge that has never been settled.
     * </p>
     */
    void unsettleTopology() {
        if (currentTopologyPhase() == EEBusTopologyPhase.ASSEMBLING) {
            logger.debug("{}: unsettleTopology() called but topology is already ASSEMBLING; ignoring", thing.getUID());
            return;
        }
        logger.info(
                "{}: topology reset to ASSEMBLING via unsettleTopology() Thing Action; stopping SPINE " + "connection",
                thing.getUID());
        persistTopologyPhase(EEBusTopologyPhase.ASSEMBLING);
        dispose();
        initialize();
    }

    /**
     * @return the SPINE device backing this Bridge, or {@code null} before/after
     *         {@link #initialize()}/{@link #dispose()}. Used by {@link EEBusOhEntityHandler}
     *         to reach {@code Device#getNodeManagement()}.
     */
    @Nullable
    Device getDevice() {
        return device;
    }

    /**
     * @return the mDNS browser backing this Bridge, or {@code null} before/after
     *         {@link #initialize()}/{@link #dispose()}, or if it failed to start (see
     *         {@link #startShipSpine}). Used by {@link EEBusOhEntityHandler} to resolve
     *         {@code UseCasePartner#getCommunicationAddress()} back to a paired peer's SKI
     *         (CONCEPT.md §7.2/§7.9).
     */
    @Nullable
    public EEBusMdnsBrowser getMdnsBrowser() {
        return mdnsBrowser;
    }
}
