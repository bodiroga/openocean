/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openocean.internal.eep.Generic;

import static org.openhab.binding.openocean.OpenOceanBindingConstants.PARAMETER_EEPID;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.PointType;
import org.eclipse.smarthome.core.library.types.RewindFastforwardType;
import org.eclipse.smarthome.core.library.types.StringListType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.transform.actions.Transformation;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openocean.internal.config.OpenOceanChannelTransformationConfig;
import org.openhab.binding.openocean.internal.eep.EEP;
import org.openhab.binding.openocean.internal.messages.ERP1Message;
import org.openhab.binding.openocean.internal.transceiver.Helper;

/**
 *
 * @author Daniel Weber - Initial contribution
 */
public class GenericEEP extends EEP {

    final List<Class<? extends State>> supportedStates = Collections
            .unmodifiableList(new LinkedList<Class<? extends State>>() {

                private static final long serialVersionUID = 1L;

                {
                    add(DateTimeType.class);
                    add(DecimalType.class);
                    add(HSBType.class);
                    add(OnOffType.class);
                    add(OpenClosedType.class);
                    add(PercentType.class);
                    add(PlayPauseType.class);
                    add(PointType.class);
                    add(RewindFastforwardType.class);
                    add(StringListType.class);
                    add(StringType.class);
                    add(UpDownType.class);
                }
            });

    public GenericEEP() {
        super();
    }

    public GenericEEP(ERP1Message packet) {
        super(packet);
    }

    @Override
    protected void convertFromCommandImpl(Command command, String channelId, State currentState, Configuration config) {
        if (config != null) {
            OpenOceanChannelTransformationConfig transformationInfo = config
                    .as(OpenOceanChannelTransformationConfig.class);

            String input = channelId + "|" + command.toString();
            String output = Transformation.transform(transformationInfo.transformationType,
                    transformationInfo.transformationFuntion, input);

            if (output != null && !output.isEmpty() && !input.equals(output)) {
                try {
                    setData(Helper.hexStringToBytes(output));
                } catch (Exception e) {
                    logger.debug("Command {} could not transformed", command.toString());
                }
            }
        }
    }

    @Override
    protected State convertToStateImpl(String channelId, State currentState, Configuration config) {
        if (config != null) {

            OpenOceanChannelTransformationConfig transformationInfo = config
                    .as(OpenOceanChannelTransformationConfig.class);

            String payload = Helper.bytesToHexString(bytes);
            String input = channelId + "|" + payload;
            String output = Transformation.transform(transformationInfo.transformationType,
                    transformationInfo.transformationFuntion, input);

            if (output != null && !output.isEmpty() && !input.equals(output)) {
                String[] parts = output.split("\\|");

                if (parts.length == 2) {
                    Class<? extends State> state = supportedStates.stream().filter(s -> s.getName().contains(parts[0]))
                            .findFirst().orElse(null);

                    if (state != null) {
                        if (state.isEnum()) {

                            for (State s : state.getEnumConstants()) {
                                if (s.toString().equalsIgnoreCase(parts[1])) {
                                    return s;
                                }
                            }
                            logger.debug("Could not find value '{}' for state '{}'", parts[1], parts[0]);
                        } else {
                            try {
                                return state.getConstructor(String.class).newInstance(parts[1]);
                            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                                logger.debug("Could not create state '{}' with value '{}'", parts[0], parts[1]);
                            }
                        }
                    } else {
                        logger.debug("State '{}' not found", parts[0]);
                    }
                } else {
                    logger.debug("Transformation result malformed: {}", output);
                }
            }
        }

        return UnDefType.UNDEF;
    }

    @Override
    protected int getDataLength() {
        if (packet != null) {
            return packet.getPayload().length - SenderIdLength - RORGLength - StatusLength;
        } else {
            return bytes.length;
        }
    }

    @Override
    protected boolean validateData(int[] bytes) {
        return true;
    }

    @Override
    public void addConfigPropertiesTo(DiscoveryResultBuilder discoveredThingResultBuilder) {
        discoveredThingResultBuilder.withProperty(PARAMETER_EEPID, getEEPType().getId());
    }
}
