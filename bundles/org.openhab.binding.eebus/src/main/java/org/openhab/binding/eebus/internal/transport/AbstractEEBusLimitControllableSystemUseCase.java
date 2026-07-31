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

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.Metadata;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openmuc.jeebus.spine.api.CommunicationPartnerFeatureRequirement;
import org.openmuc.jeebus.spine.api.DataValidationException;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.api.Entity;
import org.openmuc.jeebus.spine.api.Feature;
import org.openmuc.jeebus.spine.api.NodeManagement;
import org.openmuc.jeebus.spine.api.PresenceIndication;
import org.openmuc.jeebus.spine.api.UseCasePartner;
import org.openmuc.jeebus.spine.impl.FeatureInformationService;
import org.openmuc.jeebus.spine.spi.AllowedEntityTypes;
import org.openmuc.jeebus.spine.spi.FeatureRequirement;
import org.openmuc.jeebus.spine.spi.Inject;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.utils.datatypes.ScaledNumberWrapper;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.DeviceConfigurationFeature;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueDescriptionListDataFunction;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueInitialData;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueListDataFunction;
import org.openmuc.jeebus.spine.utils.features.deviceconfiguration.RunningKeyValue;
import org.openmuc.jeebus.spine.utils.features.devicediagnosis.DeviceDiagnosisFeature;
import org.openmuc.jeebus.spine.utils.features.devicediagnosis.HeartbeatDataFunction;
import org.openmuc.jeebus.spine.utils.features.loadcontrol.LimitDescriptionFunction;
import org.openmuc.jeebus.spine.utils.features.loadcontrol.LimitListDataFunction;
import org.openmuc.jeebus.spine.utils.features.loadcontrol.LoadControlFeature;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyNameEnumType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueTypeType;
import org.openmuc.jeebus.spine.xsd.v1.DeviceConfigurationKeyValueValueType;
import org.openmuc.jeebus.spine.xsd.v1.EnergyDirectionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureAddressType;
import org.openmuc.jeebus.spine.xsd.v1.FeatureTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.FunctionEnumType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlCategoryEnumType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitDescriptionDataType;
import org.openmuc.jeebus.spine.xsd.v1.LoadControlLimitTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.RoleType;
import org.openmuc.jeebus.spine.xsd.v1.ScaledNumberType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared implementation of the LPC/LPP "Controllable System" (Server-role) use case - see
 * {@link EEBusLpcServerUseCase} (Limitation of Power Consumption) and
 * {@link EEBusLppServerUseCase} (Limitation of Power Production), which are thin subclasses
 * supplying the direction-specific constants. CONCEPT.md §5.4.2/§7: implements all three
 * mandatory scenarios for the Controllable System actor (Table 2 of
 * {@code EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf}, confirmed structurally
 * identical for LPP) - scenario 4 ("Constraints") is only Recommended for this actor and is
 * deliberately not implemented.
 *
 * <p>
 * <strong>Wire-format actor strings</strong> ("EnergyGuard"/"ControllableSystem", not the
 * human-readable catalog names "Energy Guard"/"Controllable System") verified against
 * {@code EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf}: "in the LPC Use Case, the 'EnergyGuard'
 * (Client Actor) hosts a Server Feature to provide its own heartbeat to the
 * 'ControllableSystem' (Server Actor)" - this quote also confirms the mutual-heartbeat design
 * below (both actors expose their own {@code DeviceDiagnosis} Heartbeat).
 * </p>
 *
 * <p>
 * <strong>Scenario 1 (Limit):</strong> a {@code LoadControl} Server feature with a single limit
 * entry ({@code limitId=0}, {@code MAX_VALUE_LIMIT}, {@code OBLIGATION}). The Energy Guard's
 * writes are observed via {@code LimitListDataFunction#addUseCaseWriteDataListener} - this also
 * fires on the framework's own automatic deactivation when a limit's {@code timePeriod} expires
 * (verified in {@code LimitListDataFunction} source: {@code writeData()} calls
 * {@code scheduleStartAndExpiration}, which re-fires the same listeners on expiry) - so this
 * class does not need to reimplement duration-expiry timing itself.
 * </p>
 *
 * <p>
 * <strong>Scenario 2 (Failsafe values):</strong> <em>two</em> {@code DeviceConfiguration}
 * key/value entries - {@code FailsafeConsumptionActivePowerLimit}/
 * {@code FailsafeProductionActivePowerLimit} (subclass-specific) and
 * {@code FailsafeDurationMinimum} (shared) - both writable, wired via the
 * {@link KeyValueInitialData}/{@link RunningKeyValue} helper API (the "raw" path of directly
 * calling {@code addData}/{@code updateData} on the list functions is not meant to be used
 * directly per the source, which manages key IDs and write-permission bookkeeping itself).
 * </p>
 *
 * <p>
 * <strong>Scenario 3 (Heartbeat):</strong> mutual. This entity's own outgoing heartbeat is
 * fully handled by {@link HeartbeatDataFunction#startHeartbeat()} (self-perpetuating, verified
 * in its source - no polling loop needed here). The Energy Guard's incoming heartbeat is
 * watched by detecting the partner via {@code NodeManagement#addUseCaseListener} (exactly the
 * pattern demonstrated in jeebus.spine's demo {@code ExampleUseCase}) and subscribing to its
 * {@code DeviceDiagnosis} feature; every notification resets
 * {@link EEBusLimitControlStateMachine}'s Heartbeat watchdog.
 * </p>
 *
 * <p>
 * <strong>Pragmatic simplification:</strong> only the first detected Energy Guard partner is
 * tracked - the LPC/LPP spec does not clearly define multi-Energy-Guard behavior either, and
 * v1's motivating scenario (CONCEPT.md §1) has exactly one.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@AllowedEntityTypes({ EntityTypeEnumType.CEM })
public abstract class AbstractEEBusLimitControllableSystemUseCase implements UseCase {

    private static final String ACTOR_ENERGY_GUARD = "EnergyGuard";
    private static final String ACTOR_CONTROLLABLE_SYSTEM = "ControllableSystem";

    private static final long LIMIT_ID = 0L;

    /** Data point shared by both LPC and LPP (CONCEPT.md §4.2 data-point table). */
    static final String DATA_POINT_FAILSAFE_DURATION = "failsafeDurationMinimum";
    static final String DATA_POINT_STATE = "state";

    /**
     * Pragmatic default (2 minutes) - the spec leaves the exact value to the CS; the Energy
     * Guard may write a different one at runtime (scenario 2).
     */
    private static final long DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS = 120;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final EEBusMetadataService metadataService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private @Nullable Entity entity;

    private @Nullable FeatureAddressType address;
    private @Nullable Device device;
    private @Nullable EEBusLimitControlStateMachine stateMachine;
    private @Nullable LimitListDataFunction limitListDataFunction;
    private @Nullable ScaledNumberType lastWrittenLimitValue;
    private volatile long failsafeDurationMinimumSeconds = DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS;

    protected AbstractEEBusLimitControllableSystemUseCase(EEBusMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /** @return the metadata short code used for Item lookups, e.g. {@code "LPC"} */
    protected abstract String getShortCode();

    /**
     * @return the SPINE use case name, e.g. {@code "LimitationOfPowerConsumption"} (exact
     *         wire-format casing not independently verified against the primary spec - same caveat as
     *         {@code EEBusMpcServerUseCase#getName()}).
     */
    protected abstract String getUseCaseName();

    /** @return {@code "consumptionLimit"}/{@code "productionLimit"} - the scenario-1 data point. */
    protected abstract String getLimitDataPoint();

    /** @return {@code "failsafeConsumptionLimit"}/{@code "failsafeProductionLimit"}. */
    protected abstract String getFailsafeLimitDataPoint();

    protected abstract EnergyDirectionEnumType getLimitDirection();

    protected abstract DeviceConfigurationKeyNameEnumType getFailsafeLimitKeyName();

    @Override
    public String getActor() {
        return ACTOR_CONTROLLABLE_SYSTEM;
    }

    @Override
    public String getName() {
        return getUseCaseName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // UseCase (jeebus.spine) is an unannotated legacy interface (no package-info.java null
    // defaults) - its getScenarioSupport()/getFeatureRequirements() are therefore unconstrained.
    // @NonNullByDefault({}) opts these two overrides out of this class's default non-null
    // constraint so the return type matches the super method exactly (otherwise ecj reports
    // "mismatching null constraints" since it would otherwise infer List<@NonNull Long>/
    // Set<@NonNull FeatureRequirement> against the unconstrained super signature).
    @Override
    @NonNullByDefault({})
    public List<Long> getScenarioSupport() {
        return List.of(1L, 2L, 3L);
    }

    @Override
    public FeatureAddressType getAddress() {
        FeatureAddressType currentAddress = address;
        if (currentAddress == null) {
            throw new IllegalStateException("getAddress() called before setup()");
        }
        return currentAddress;
    }

    @Override
    @NonNullByDefault({})
    public Set<FeatureRequirement> getFeatureRequirements(EntityTypeEnumType entityType) {
        return Set.of(FeatureRequirement.GENERIC_CLIENT,
                new FeatureRequirement(FeatureTypeEnumType.LOAD_CONTROL, RoleType.SERVER,
                        LimitDescriptionFunction.class, LimitListDataFunction.class),
                new FeatureRequirement(FeatureTypeEnumType.DEVICE_CONFIGURATION, RoleType.SERVER,
                        KeyValueDescriptionListDataFunction.class, KeyValueListDataFunction.class),
                new FeatureRequirement(FeatureTypeEnumType.DEVICE_DIAGNOSIS, RoleType.SERVER,
                        HeartbeatDataFunction.class));
    }

    @Override
    public void setup() {
        Entity localEntity = this.entity;
        if (localEntity == null) {
            throw new IllegalStateException("Entity was not injected before setup()");
        }
        this.address = new FeatureAddressType().withDevice(localEntity.getStaticAddress().getDevice())
                .withEntity(localEntity.getStaticAddress().getEntity());
        this.device = localEntity.getDevice();

        setupLoadControl(localEntity);
        setupDeviceConfiguration(localEntity);
        setupDeviceDiagnosis(localEntity);

        this.stateMachine = new EEBusLimitControlStateMachine(scheduler, () -> failsafeDurationMinimumSeconds,
                this::onStateChanged);

        localEntity.getDevice().getNodeManagement().addUseCaseListener(this::onEnergyGuardFound, getName(),
                ACTOR_ENERGY_GUARD,
                Map.of(1L, PresenceIndication.MANDATORY, 2L, PresenceIndication.MANDATORY, 3L,
                        PresenceIndication.MANDATORY),
                Set.of(new CommunicationPartnerFeatureRequirement(FeatureTypeEnumType.DEVICE_DIAGNOSIS, Map.of(
                        FunctionEnumType.DEVICE_DIAGNOSIS_HEARTBEAT_DATA, Map.of(3L, PresenceIndication.MANDATORY)))));
    }

    private Feature findFeature(Entity localEntity, FeatureTypeEnumType type) {
        return localEntity.getFeatures().stream().filter(f -> f.getType() == type && f.getRole() == RoleType.SERVER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        type + " server feature missing - Entity#addUseCase() should have added it from "
                                + "getFeatureRequirements()"));
    }

    private void setupLoadControl(Entity localEntity) {
        Feature rawFeature = findFeature(localEntity, FeatureTypeEnumType.LOAD_CONTROL);
        LoadControlFeature loadControlFeature = (LoadControlFeature) FeatureInformationService.getInstance()
                .createFeatureWrapper(rawFeature);
        LimitDescriptionFunction descriptionFunction = loadControlFeature.addLimitDescriptionFunction();
        LimitListDataFunction limitFunction = loadControlFeature.addLimitListDataFunction();
        this.limitListDataFunction = limitFunction;

        try {
            descriptionFunction.addData(new LoadControlLimitDescriptionDataType().withLimitId(LIMIT_ID)
                    .withLimitType(LoadControlLimitTypeEnumType.MAX_VALUE_LIMIT.value())
                    .withLimitCategory(LoadControlCategoryEnumType.OBLIGATION.value())
                    .withLimitDirection(getLimitDirection().value()).withUnit("W"));
            limitFunction.addData(new LoadControlLimitDataType().withLimitId(LIMIT_ID).withIsLimitChangeable(true)
                    .withIsLimitActive(false));
        } catch (DataValidationException e) {
            logger.warn("Failed to register {} limit description/initial data", getShortCode(), e);
        }

        limitFunction.addUseCaseWriteDataListener((data, updateType, idx) -> onLimitWritten(data));
    }

    private void onLimitWritten(LoadControlLimitDataType data) {
        boolean hasValue = data.getValue() != null;
        if (hasValue) {
            this.lastWrittenLimitValue = data.getValue();
            double watts = new ScaledNumberWrapper(data.getValue()).toDouble();
            metadataService.find(getShortCode(), getLimitDataPoint(), null).ifPresent(metadata -> metadataService
                    .sendCommand(EEBusMetadataService.itemNameOf(metadata), new QuantityType<>(watts, Units.WATT)));
        }
        boolean active = Boolean.TRUE.equals(data.getIsLimitActive()) && hasValue;
        EEBusLimitControlStateMachine machine = this.stateMachine;
        if (machine != null) {
            machine.onLimitWritten(active);
        }
    }

    private void setupDeviceConfiguration(Entity localEntity) {
        Feature rawFeature = findFeature(localEntity, FeatureTypeEnumType.DEVICE_CONFIGURATION);
        DeviceConfigurationFeature deviceConfigurationFeature = (DeviceConfigurationFeature) FeatureInformationService
                .getInstance().createFeatureWrapper(rawFeature);
        deviceConfigurationFeature.addKeyValueDescriptionListDataFunction();
        deviceConfigurationFeature.addKeyValueListDataFunction();

        try {
            RunningKeyValue failsafeLimit = new KeyValueInitialData()
                    .withDescription(new DeviceConfigurationKeyValueDescriptionDataType()
                            .withKeyName(getFailsafeLimitKeyName().value())
                            .withValueType(DeviceConfigurationKeyValueTypeType.SCALED_NUMBER).withUnit("W"))
                    .withData(new DeviceConfigurationKeyValueDataType().withIsValueChangeable(true)
                            .withValue(new DeviceConfigurationKeyValueValueType()
                                    .withScaledNumber(new ScaledNumberWrapper(0.0).toXsdType())))
                    .addToFeature(deviceConfigurationFeature);
            failsafeLimit.setWriteDataAllowed(true);
            failsafeLimit.addWriteDataListener((data, updateType) -> onFailsafeLimitWritten(data));

            RunningKeyValue failsafeDuration = new KeyValueInitialData()
                    .withDescription(new DeviceConfigurationKeyValueDescriptionDataType()
                            .withKeyName(DeviceConfigurationKeyNameEnumType.FAILSAFE_DURATION_MINIMUM.value())
                            .withValueType(DeviceConfigurationKeyValueTypeType.DURATION))
                    .withData(new DeviceConfigurationKeyValueDataType().withIsValueChangeable(true)
                            .withValue(new DeviceConfigurationKeyValueValueType()
                                    .withDuration(secondsToDuration(failsafeDurationMinimumSeconds))))
                    .addToFeature(deviceConfigurationFeature);
            failsafeDuration.setWriteDataAllowed(true);
            failsafeDuration.addWriteDataListener((data, updateType) -> onFailsafeDurationWritten(data));
        } catch (DataValidationException e) {
            logger.warn("Failed to register {} failsafe DeviceConfiguration key/values", getShortCode(), e);
        }
    }

    private void onFailsafeLimitWritten(DeviceConfigurationKeyValueDataType data) {
        DeviceConfigurationKeyValueValueType value = data.getValue();
        if (value == null || value.getScaledNumber() == null) {
            return;
        }
        double watts = new ScaledNumberWrapper(value.getScaledNumber()).toDouble();
        metadataService.find(getShortCode(), getFailsafeLimitDataPoint(), null).ifPresent(metadata -> metadataService
                .sendCommand(EEBusMetadataService.itemNameOf(metadata), new QuantityType<>(watts, Units.WATT)));
    }

    private void onFailsafeDurationWritten(DeviceConfigurationKeyValueDataType data) {
        DeviceConfigurationKeyValueValueType value = data.getValue();
        if (value == null || value.getDuration() == null) {
            return;
        }
        long seconds = durationToSeconds(value.getDuration());
        this.failsafeDurationMinimumSeconds = seconds;
        metadataService.find(getShortCode(), DATA_POINT_FAILSAFE_DURATION, null).ifPresent(metadata -> metadataService
                .sendCommand(EEBusMetadataService.itemNameOf(metadata), new QuantityType<>(seconds, Units.SECOND)));
    }

    private void setupDeviceDiagnosis(Entity localEntity) {
        Feature rawFeature = findFeature(localEntity, FeatureTypeEnumType.DEVICE_DIAGNOSIS);
        DeviceDiagnosisFeature deviceDiagnosisFeature = (DeviceDiagnosisFeature) FeatureInformationService.getInstance()
                .createFeatureWrapper(rawFeature);
        // "at least every 60 seconds" per LPC-005/006 (identical for LPP) - HeartbeatDataFunction
        // self-perpetuates from here on, no polling loop needed (verified in its source).
        deviceDiagnosisFeature.addHeartBeatDataFunction(60);
        deviceDiagnosisFeature.startHeartbeat();
    }

    private void onEnergyGuardFound(List<UseCasePartner> partners) {
        if (partners.isEmpty()) {
            return;
        }
        // Pragmatic: only the first detected Energy Guard is tracked, see class javadoc.
        UseCasePartner partner = partners.get(0);
        FeatureAddressType heartbeatAddress = partner.getCompleteFeatureAddress(FeatureTypeEnumType.DEVICE_DIAGNOSIS);
        if (heartbeatAddress == null) {
            logger.warn("{} partner at {} has no DeviceDiagnosis feature address, cannot watch its Heartbeat",
                    getShortCode(), partner.getCommunicationAddress());
            return;
        }
        Device localDevice = this.device;
        if (localDevice == null) {
            return;
        }
        NodeManagement nodeManagement = localDevice.getNodeManagement();
        nodeManagement.requestSubscription(heartbeatAddress, FeatureTypeEnumType.DEVICE_DIAGNOSIS,
                notification -> onHeartbeatNotification()).exceptionally(ex -> {
                    logger.warn("Failed to subscribe to Energy Guard Heartbeat for {}", getShortCode(), ex);
                    return null;
                });
    }

    private void onHeartbeatNotification() {
        EEBusLimitControlStateMachine machine = this.stateMachine;
        if (machine != null) {
            machine.onHeartbeatReceived();
        }
    }

    private void onStateChanged(EEBusLimitControlState newState) {
        LimitListDataFunction function = this.limitListDataFunction;
        if (function != null) {
            // LPC-009: the CS SHALL report the limit as activated/deactivated according to its
            // own state (only "activated" while LIMITED), independently of what was last written.
            ScaledNumberType value = lastWrittenLimitValue;
            try {
                function.updateData(0,
                        new LoadControlLimitDataType().withLimitId(LIMIT_ID).withIsLimitChangeable(true)
                                .withIsLimitActive(newState == EEBusLimitControlState.LIMITED)
                                .withValue(value != null ? value : new ScaledNumberWrapper(0.0).toXsdType()));
            } catch (DataValidationException e) {
                logger.warn("Failed to update {} limit isLimitActive for new state {}", getShortCode(), newState, e);
            }
        }
        Optional<Metadata> metadata = metadataService.find(getShortCode(), DATA_POINT_STATE, null);
        if (metadata.isPresent()) {
            metadataService.updateState(EEBusMetadataService.itemNameOf(metadata.get()),
                    new StringType(newState.name()));
        }
    }

    private static Duration secondsToDuration(long seconds) {
        return DatatypeFactory.newDefaultInstance().newDuration(true, null, null, null, null, null,
                BigDecimal.valueOf(seconds));
    }

    private static long durationToSeconds(Duration duration) {
        return duration.getTimeInMillis(new Date(0)) / 1000;
    }

    @Override
    public void close() {
        EEBusLimitControlStateMachine machine = this.stateMachine;
        if (machine != null) {
            machine.close();
        }
        scheduler.shutdownNow();
    }
}
