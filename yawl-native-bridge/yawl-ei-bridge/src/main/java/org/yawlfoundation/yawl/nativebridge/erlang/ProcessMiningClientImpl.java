/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.nativebridge.erlang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of ProcessMiningClient that delegates to ErlangNode.
 * This class provides the bridge between the domain API and the underlying Erlang RPC calls.
 */
public class ProcessMiningClientImpl implements ProcessMiningClient {

    private final ErlangNode erlangNode;
    private final boolean autoConnect;
    private static final String PM_MODULE = "process_mining";
    private static final long DEFAULT_TIMEOUT = 30000; // 30 seconds

    /**
     * Creates a new ProcessMiningClientImpl with the given Erlang node.
     *
     * @param erlangNode The Erlang node to connect to
     */
    public ProcessMiningClientImpl(ErlangNode erlangNode) {
        this(erlangNode, true);
    }

    /**
     * Creates a new ProcessMiningClientImpl with the given Erlang node and auto-connect option.
     *
     * @param erlangNode The Erlang node to connect to
     * @param autoConnect Whether to auto-connect on first operation
     */
    public ProcessMiningClientImpl(ErlangNode erlangNode, boolean autoConnect) {
        if (erlangNode == null) {
            throw new IllegalArgumentException("Erlang node cannot be null");
        }
        this.erlangNode = erlangNode;
        this.autoConnect = autoConnect;
    }

    @Override
    public ErlTerm discoverProcessModel(List<EventLogEntry> eventLog) throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            // Convert event log to Erlang list of tuples
            ErlTerm[] eventLogTerms = eventLog.stream()
                    .map(this::convertEventLogEntry)
                    .toArray(ErlTerm[]::new);

            ErlList eventLogList = ErlList.of(eventLogTerms);
            ErlList arguments = ErlList.of(
                    ErlAtom.atom("discover"),
                    eventLogList
            );

            return erlangNode.rpc(PM_MODULE, "process_operations", arguments);

        } catch (Exception e) {
            throw new ErlangException("Failed to discover process model", e);
        }
    }

    @Override
    public ConformanceResult conformanceCheck(ErlTerm processModel, List<EventLogEntry> eventLog) throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            // Convert process model to Erlang term
            ErlTerm[] eventLogTerms = eventLog.stream()
                    .map(this::convertEventLogEntry)
                    .toArray(ErlTerm[]::new);

            ErlList eventLogList = ErlList.of(eventLogTerms);
            ErlList arguments = ErlList.of(
                    ErlAtom.atom("conformance_check"),
                    processModel,
                    eventLogList
            );

            ErlTerm result = erlangNode.rpc(PM_MODULE, "process_operations", arguments);

            // Parse result tuple {Fitness, Precision, Deviations}
            if (result instanceof ErlTuple) {
                ErlTuple resultTuple = (ErlTuple) result;
                if (resultTuple.getArity() == 3) {
                    ErlTerm fitnessTerm = resultTuple.getElement(0);
                    ErlTerm precisionTerm = resultTuple.getElement(1);
                    ErlTerm deviationsTerm = resultTuple.getElement(2);

                    double fitness = extractDouble(fitnessTerm);
                    double precision = extractDouble(precisionTerm);
                    List<ErlTerm> deviations = extractList(deviationsTerm);

                    return new ConformanceResult(fitness, precision, deviations);
                }
            }

            throw new ErlangException("Unexpected conformance result format: " + result);

        } catch (Exception e) {
            throw new ErlangException("Failed to perform conformance check", e);
        }
    }

    @Override
    public PerformanceResult analyzePerformance(ErlTerm processModel, List<EventLogEntry> eventLog) throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            ErlTerm[] eventLogTerms = eventLog.stream()
                    .map(this::convertEventLogEntry)
                    .toArray(ErlTerm[]::new);

            ErlList eventLogList = ErlList.of(eventLogTerms);
            ErlList arguments = ErlList.of(
                    ErlAtom.atom("analyze_performance"),
                    processModel,
                    eventLogList
            );

            ErlTerm result = erlangNode.rpc(PM_MODULE, "process_operations", arguments);

            // Parse result tuple {AvgCycleTime, Throughput, ActivityDurations}
            if (result instanceof ErlTuple) {
                ErlTuple resultTuple = (ErlTuple) result;
                if (resultTuple.getArity() == 3) {
                    ErlTerm avgCycleTimeTerm = resultTuple.getElement(0);
                    ErlTerm throughputTerm = resultTuple.getElement(1);
                    ErlTerm durationsTerm = resultTuple.getElement(2);

                    double avgCycleTime = extractDouble(avgCycleTimeTerm);
                    double throughput = extractDouble(throughputTerm);
                    Map<String, Double> activityDurations = extractMap(durationsTerm);

                    return new PerformanceResult(avgCycleTime, throughput, activityDurations);
                }
            }

            throw new ErlangException("Unexpected performance result format: " + result);

        } catch (Exception e) {
            throw new ErlangException("Failed to analyze performance", e);
        }
    }

    @Override
    public ProcessInstanceStats getProcessInstanceStats(String processInstanceId) throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            ErlList arguments = ErlList.of(
                    ErlAtom.atom("get_instance_stats"),
                    ErlAtom.atom(processInstanceId)
            );

            ErlTerm result = erlangNode.rpc(PM_MODULE, "instance_operations", arguments);

            // Parse result tuple {TotalActivities, Duration, StartTime, EndTime}
            if (result instanceof ErlTuple) {
                ErlTuple resultTuple = (ErlTuple) result;
                if (resultTuple.getArity() == 4) {
                    ErlTerm totalActivitiesTerm = resultTuple.getElement(0);
                    ErlTerm durationTerm = resultTuple.getElement(1);
                    ErlTerm startTimeTerm = resultTuple.getElement(2);
                    ErlTerm endTimeTerm = resultTuple.getElement(3);

                    int totalActivities = extractInt(totalActivitiesTerm);
                    long duration = extractLong(durationTerm);
                    long startTime = extractLong(startTimeTerm);
                    long endTime = extractLong(endTimeTerm);

                    return new ProcessInstanceStats(processInstanceId, totalActivities, duration, startTime, endTime);
                }
            }

            throw new ErlangException("Unexpected instance stats format: " + result);

        } catch (Exception e) {
            throw new ErlangException("Failed to get process instance stats", e);
        }
    }

    @Override
    public List<String> listProcessModels() throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            ErlList arguments = ErlList.of(ErlAtom.atom("list_models"));
            ErlTerm result = erlangNode.rpc(PM_MODULE, "model_operations", arguments);

            // Parse list of atom strings
            List<String> modelIds = new ArrayList<>();
            if (result instanceof ErlList) {
                ErlList resultList = (ErlList) result;
                ErlTerm[] elements = resultList.getElements();
                for (ErlTerm element : elements) {
                    if (element instanceof ErlAtom) {
                        modelIds.add(((ErlAtom) element).getValue());
                    }
                }
            }

            return modelIds;

        } catch (Exception e) {
            throw new ErlangException("Failed to list process models", e);
        }
    }

    @Override
    public ValidationResult validateProcessModel(ErlTerm processModel) throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            ErlList arguments = ErlList.of(
                    ErlAtom.atom("validate"),
                    processModel
            );

            ErlTerm result = erlangNode.rpc(PM_MODULE, "model_operations", arguments);

            // Parse result tuple {IsValid, Warnings, Errors}
            if (result instanceof ErlTuple) {
                ErlTuple resultTuple = (ErlTuple) result;
                if (resultTuple.getArity() == 3) {
                    ErlTerm isValidTerm = resultTuple.getElement(0);
                    ErlTerm warningsTerm = resultTuple.getElement(1);
                    ErlTerm errorsTerm = resultTuple.getElement(2);

                    boolean isValid = extractBoolean(isValidTerm);
                    List<String> warnings = extractStringList(warningsTerm);
                    List<String> errors = extractStringList(errorsTerm);

                    return new ValidationResult(isValid, warnings, errors);
                }
            }

            throw new ErlangException("Unexpected validation result format: " + result);

        } catch (Exception e) {
            throw new ErlangException("Failed to validate process model", e);
        }
    }

    @Override
    public ErlTerm executeQuery(String query, Map<String, ErlTerm> parameters) throws ErlangException {
        if (!erlangNode.isConnected() && autoConnect) {
            erlangNode.connect();
        }

        try {
            // Convert parameters to Erlang list of tuples
            ErlTerm[] parameterTerms = parameters.entrySet().stream()
                    .map(entry -> ErlTuple.of(
                            ErlAtom.atom(entry.getKey()),
                            entry.getValue()
                    ))
                    .toArray(ErlTerm[]::new);

            ErlList parametersList = ErlList.of(parameterTerms);
            ErlList arguments = ErlList.of(
                    ErlAtom.atom("execute_query"),
                    ErlAtom.atom(query),
                    parametersList
            );

            return erlangNode.rpc(PM_MODULE, "query_operations", arguments);

        } catch (Exception e) {
            throw new ErlangException("Failed to execute query", e);
        }
    }

    /**
     * Converts an EventLogEntry to an Erlang tuple.
     */
    private ErlTerm convertEventLogEntry(EventLogEntry entry) {
        ErlTerm[] tupleElements = new ErlTerm[4];
        tupleElements[0] = ErlAtom.atom(entry.getCaseId());
        tupleElements[1] = ErlAtom.atom(entry.getActivity());
        tupleElements[2] = ErlLong.longValue(entry.getTimestamp());

        // Convert attributes to Erlang list
        ErlTerm[] attrElements = entry.getAttributes().entrySet().stream()
                .map(attr -> ErlTuple.of(
                        ErlAtom.atom(attr.getKey()),
                        ErlAtom.atom(attr.getValue())
                ))
                .toArray(ErlTerm[]::new);
        tupleElements[3] = ErlList.of(attrElements);

        return ErlTuple.of(tupleElements);
    }

    /**
     * Helper method to extract double from Erlang term.
     */
    private double extractDouble(ErlTerm term) {
        if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        }
        throw new IllegalArgumentException("Expected number but got: " + term);
    }

    /**
     * Helper method to extract int from Erlang term.
     */
    private int extractInt(ErlTerm term) {
        if (term instanceof ErlLong) {
            return (int) ((ErlLong) term).getValue();
        }
        throw new IllegalArgumentException("Expected integer but got: " + term);
    }

    /**
     * Helper method to extract long from Erlang term.
     */
    private long extractLong(ErlTerm term) {
        if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        }
        throw new IllegalArgumentException("Expected long but got: " + term);
    }

    /**
     * Helper method to extract boolean from Erlang term.
     */
    private boolean extractBoolean(ErlTerm term) {
        if (term instanceof ErlAtom) {
            String value = ((ErlAtom) term).getValue();
            return "true".equals(value);
        }
        throw new IllegalArgumentException("Expected boolean but got: " + term);
    }

    /**
     * Helper method to extract list of ErlTerms.
     */
    private List<ErlTerm> extractList(ErlTerm term) {
        if (term instanceof ErlList) {
            List<ErlTerm> result = new ArrayList<>();
            ErlTerm[] elements = ((ErlList) term).getElements();
            for (ErlTerm element : elements) {
                if (!(element instanceof ErlAtom) || !"nil".equals(((ErlAtom) element).getValue())) {
                    result.add(element);
                }
            }
            return result;
        }
        throw new IllegalArgumentException("Expected list but got: " + term);
    }

    /**
     * Helper method to extract map from Erlang term.
     */
    private Map<String, Double> extractMap(ErlTerm term) {
        Map<String, Double> result = new HashMap<>();
        List<ErlTerm> elements = extractList(term);

        for (ErlTerm element : elements) {
            if (element instanceof ErlTuple) {
                ErlTuple tuple = (ErlTuple) element;
                if (tuple.getArity() == 2) {
                    ErlTerm keyTerm = tuple.getElement(0);
                    ErlTerm valueTerm = tuple.getElement(1);

                    if (keyTerm instanceof ErlAtom && valueTerm instanceof ErlLong) {
                        String key = ((ErlAtom) keyTerm).getValue();
                        double value = ((ErlLong) valueTerm).getValue();
                        result.put(key, value);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Helper method to extract list of strings from Erlang term.
     */
    private List<String> extractStringList(ErlTerm term) {
        List<String> result = new ArrayList<>();
        List<ErlTerm> elements = extractList(term);

        for (ErlTerm element : elements) {
            if (element instanceof ErlAtom) {
                result.add(((ErlAtom) element).getValue());
            }
        }

        return result;
    }

    /**
     * Closes the underlying Erlang node connection.
     *
     * @throws ErlangException if closing fails
     */
    public void close() throws ErlangException {
        erlangNode.close();
    }
}