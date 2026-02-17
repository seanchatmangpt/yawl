/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
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

package org.yawlfoundation.yawl.procletService.blockType;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.procletService.SingleInstanceClass;
import org.yawlfoundation.yawl.procletService.connect.Trigger;
import org.yawlfoundation.yawl.procletService.interactionGraph.InteractionArc;
import org.yawlfoundation.yawl.procletService.interactionGraph.InteractionArc.ArcState;
import org.yawlfoundation.yawl.procletService.interactionGraph.InteractionGraph;
import org.yawlfoundation.yawl.procletService.interactionGraph.InteractionGraphs;
import org.yawlfoundation.yawl.procletService.interactionGraph.InteractionNode;
import org.yawlfoundation.yawl.procletService.models.procletModel.ProcletBlock;
import org.yawlfoundation.yawl.procletService.models.procletModel.ProcletModel;
import org.yawlfoundation.yawl.procletService.models.procletModel.ProcletModels;
import org.yawlfoundation.yawl.procletService.persistence.DBConnection;
import org.yawlfoundation.yawl.procletService.persistence.Item;
import org.yawlfoundation.yawl.procletService.persistence.StoredItem;
import org.yawlfoundation.yawl.procletService.selectionProcess.ProcessEntityMID;
import org.yawlfoundation.yawl.procletService.util.EntityMID;

import java.util.ArrayList;
import java.util.List;

public class CompleteCaseDeleteCase {

	private final String procletID;

	private final Logger myLog = LogManager.getLogger(CompleteCaseDeleteCase.class);

	public CompleteCaseDeleteCase (String procletID) {
		this.procletID = procletID;
	}

	public String getClassIDfromGraphs () {
		for (var graph : InteractionGraphs.getInstance().getGraphs()) {
			for (var node : graph.getNodes()) {
				if (node.getProcletID().equals(this.procletID)) {
					return node.getClassID();
				}
			}
		}
		myLog.warn("No class ID found in interaction graphs for proclet '{}'", this.procletID);
		return null;
	}

	public void removeFromGraphs (String procletID) {
		for (var graph : InteractionGraphs.getInstance().getGraphs()) {
			List<InteractionNode> remNodes = new ArrayList<>();
			for (var node : graph.getNodes()) {
				if (node.getProcletID().startsWith(procletID)) {
					remNodes.add(node);
				}
			}
			for (var node : remNodes) {
				graph.deleteNode(node);
			}
		}
	}

	// first calculateFailingArcs before REMOVAL!
	public List<InteractionArc> calcFailingArcs () {
		myLog.debug("CALCFAILINGARCS");
		List<InteractionArc> failingArcs = new ArrayList<>();
		for (var graph : InteractionGraphs.getInstance().getGraphs()) {
			for (var arc : graph.getArcs()) {
				myLog.debug("arc:" + arc);
				if (arc.getHead().getProcletID().equals(this.procletID)
						&& (arc.getArcState().equals(ArcState.UNPRODUCED) ||
								arc.getArcState().equals(ArcState.SENT))) {
					failingArcs.add(arc);
				}
				if (arc.getTail().getProcletID().equals(this.procletID) &&
						arc.getArcState().equals(ArcState.UNPRODUCED)) {
					failingArcs.add(arc);
				}
				if (arc.getTail().getProcletID().equals(this.procletID)
						&& (arc.getArcState().equals(ArcState.EXECUTED_NONE) ||
								arc.getArcState().equals(ArcState.EXECUTED_SOURCE))
						&& arc.getHead().getProcletID().equals(this.procletID)) {
					failingArcs.add(arc);
				}
			}
		}
		return failingArcs;
	}

	public List<EntityMID> emidsFromArcsNoDupl(List<InteractionArc> arcs) {
		List<EntityMID> emids = new ArrayList<>();
		for (var arc : arcs) {
			var emid = arc.getEntityID().getEmid();
			boolean exists = false;
			for (var check : emids) {
				if (check.getValue().equals(emid.getValue())) {
					exists = true;
				}
			}
			if (!exists) {
				emids.add(emid);
			}
		}
		return emids;
	}

	public List<Object> exceptionCase (List<EntityMID> emids, String classID) {
		List<Object> returnList = new ArrayList<>();

		// get exception block
		var pmodel = ProcletModels.getInstance().getProcletClass(classID);
		var blockExc = pmodel.getBlock("exception");
		var wir = new WorkItemRecord(procletID, "exception", classID, "");

		returnList.add(emids);
		returnList.add(wir);
		returnList.add(blockExc);
		return returnList;
	}

	public void removalCaseCompletionCase () {
		// check first if deletion is allowed
		while (SingleInstanceClass.getInstance().isCaseBlocked(procletID)) {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				myLog.error("Thread interrupted while waiting for case block in CompleteCaseDeleteCase", e);
			}
		}
		SingleInstanceClass.getInstance().blockCase(procletID);
		var classID = this.getClassIDfromGraphs();
		var failingArcs = this.calcFailingArcs();

		// set state of failing arcs to FAILED
		for (var arc : failingArcs) {
			arc.setArcState(ArcState.FAILED);
		}
		InteractionGraphs.getInstance().persistGraphs();

		myLog.debug("failing arcs:" + failingArcs);
		if (!failingArcs.isEmpty() && classID != null) {
			myLog.debug("work needs to be done");
			var emids = this.emidsFromArcsNoDupl(failingArcs);
			myLog.debug("emids:" + emids);
			var exceptionCase = this.exceptionCase(emids, classID);
			myLog.debug("exception case:" + exceptionCase);
			publishException(exceptionCase);

			// take on from here
			myLog.debug("handleException:" + classID + "," + this.procletID + "," + emids);
			handleException(classID, this.procletID, "exception", emids);

			// persist the graphs
			InteractionGraphs.getInstance().persistGraphs();
		} else {
			// nothing to be done
			myLog.debug("nothing to be done");
			// only remove nodes from the graph
			InteractionGraphs.getInstance().persistGraphs();
			myLog.debug("no affected emids because of completion case / removal case");
		}

		// remove the temp graphs again
		InteractionGraphs.getNewInstance().deleteTempGraphs();
		InteractionGraphs.getNewInstance().deleteTempGraphsFromDB();
		SingleInstanceClass.getInstance().unblockCase(procletID);
	}

	private void handleException(String classID, String procletID, String blockID, List<EntityMID> emids) {
		WorkItemRecord wir = null;
		boolean ignore = false;
		while (true) {
			try {
				Thread.sleep(500);
				if (isExceptionCaseSelectedUser(classID, procletID, blockID)) {
					break;
				} else if (isExceptionCaseSelectedUser("none", "none", blockID)) {
					ignore = true;
					break;
				}
			}
			catch (Exception e) {
				myLog.error("Exception in CompleteCaseDeleteCase processing", e);
			}
		}
		boolean firstPass = false;
		if (!emids.isEmpty() && !ignore) {

			// first connect
			var trigger = new Trigger();
			trigger.initiate();
			while (true) {
				if (!firstPass) {
					deleteAvailableEmidsCaseExceptionToUser(classID, procletID, blockID);
					pushAvailableEmidsCaseExceptionToUser(classID, procletID, blockID, emids);
				}
				trigger.send("BLAAT3");

				// time check for selection
				var selectedEmidStr = trigger.receive();

				// take selected one - enter phase of editing graph
				if (!selectedEmidStr.equals("EXIT")) {
					EntityMID emidSel = null;
					for (var emid : emids) {
						if (emid.getValue().equals(selectedEmidStr)) {
							emidSel = emid;
						}
					}
					var id = BlockCP.getUniqueID();
					wir = new WorkItemRecord(procletID, blockID, classID, "");
					var pmodel = ProcletModels.getInstance().getProcletClass(classID);
					var block = pmodel.getBlock(blockID);
					var pemid = new ProcessEntityMID(wir, block, emidSel, id);

					// process user request
					pemid.initialGraphs(true);
					InteractionGraphs.getInstance().persistGraphs();
					while (true) {
						var result = pemid.generateNextOptions(true);
						var ncrBlocks = pemid.determineOptionsNonCrBlocks(result.get(1));
						List<List<List>> options = new ArrayList<>();
						options.add(result.get(0));
						options.add(ncrBlocks);

						// send this to user together with an update of the graph
						ProcessEntityMID.deleteOptionsFromDB();
						ProcessEntityMID.sendOptionsToDB(options);

						// inform user that options are sent
						trigger.send("something");

						// get answer back: either commit or finish selection
						var userDecision = trigger.receive();

						if (userDecision.equals("commit")) {
							boolean checks = pemid.doChecks();
							if (checks) {
								// checks ok
								deleteEmidCaseExceptionToUser(selectedEmidStr);

								// delete emid also from list
								EntityMID emidToRemove = null;
								for (var emid : emids) {
									if (emid.getValue().equals(selectedEmidStr)) {
										emidToRemove = emid;
									}
								}
								emids.remove(emidToRemove);
								firstPass = true;

								// commit graphs - remove CP nodes with no incoming or outgoing arcs
								var igraphs = InteractionGraphs.getInstance();
								for (var graph : igraphs.getGraphs()) {
									if (graph.getEntityMID().getValue().equals(emidToRemove.getValue() + "TEMP")) {
										List<InteractionNode> nodesRemove = new ArrayList<>();
										for (var node : graph.getNodes()) {
											boolean found = false;
											for (var arc : graph.getArcs()) {
												if ((arc.getTail().getClassID().equals(node.getClassID()) &&
														arc.getTail().getProcletID().equals(node.getProcletID()) &&
														arc.getTail().getBlockID().equals(node.getBlockID())) ||
														(arc.getHead().getClassID().equals(node.getClassID()) &&
														arc.getHead().getProcletID().equals(node.getProcletID()) &&
														arc.getHead().getBlockID().equals(node.getBlockID()))) {
													found = true;
													break;
												}
											}
											if (!found) {
												nodesRemove.add(node);
											}
										}
										for (var node : nodesRemove) {
											graph.deleteNode(node);
										}
									}
								}

								// done removing nodes
								igraphs.commitTempGraphEmid(emidToRemove.getValue());

								// delete everything from DB
								ProcessEntityMID.deleteOptionsFromDB();
								ProcessEntityMID.deleteDecisionsFromDB();
								trigger.send("ok");
								break;
							} else {
								// user continues or graphs are wrong - put old graphs back
								var igraphs = InteractionGraphs.getInstance();
								igraphs.deleteTempGraphs();
								igraphs.deleteTempGraphsFromDB();
								igraphs.persistGraphs();

								// delete everything from DB
								ProcessEntityMID.deleteOptionsFromDB();
								ProcessEntityMID.deleteDecisionsFromDB();
								trigger.send("nok");
								break;
							}
						} else if (userDecision.equals("finishSelection")) {
							// finish button pressed
							var decisions = ProcessEntityMID.getDecisionsFromDB();
							try {
								pemid.extendGraph(decisions);

								// commit temp graph
								InteractionGraphs.getInstance().persistGraphs();
								ProcessEntityMID.deleteDecisionsFromDB();
								trigger.send("something");
						}
						catch (Exception e) {
							myLog.error("Exception in CompleteCaseDeleteCase graph extension", e);
						}
							// and continue building
						}
						// else: nothing to be done
					}
					// get new id and update db
					var newID = pemid.getUID();
					BlockCP.updateUniqueID(newID);
					pemid.commitGraphs();
				} else {
					myLog.debug("EXIT received");
					// "EXIT" received -> done - delete everything from DB
					InteractionGraphs.getInstance().commitTempGraphs();
					ProcessEntityMID.sendPerformatives(true, wir);
					ProcessEntityMID.deleteOptionsFromDB();
					ProcessEntityMID.deleteDecisionsFromDB();
					trigger.close();
					CompleteCaseDeleteCase.deleteExceptionCaseSelected(classID, procletID, blockID);
					CompleteCaseDeleteCase.deleteAllEmidsCaseExceptionToUser();
					break;
				}
			} // while true
		} // end emids
		if (ignore) {
			CompleteCaseDeleteCase.deleteExceptionCaseSelected("none", "none", "exception");
		}
	}

	public static void publishException (List ec) {
		List<EntityMID> emids = (List<EntityMID>) ec.get(0);
		var sb = new StringBuilder();
		for (var emid : emids) {
			if (!sb.isEmpty()) sb.append(',');
			sb.append(emid.getValue());
		}
		DBConnection.insert(new StoredItem((WorkItemRecord) ec.get(1), sb.toString(),
				Item.ExceptionCase));
	}

	public static void deleteException (String classID, String procletID, String blockID) {
		var item = DBConnection.getStoredItem(classID, procletID, blockID, Item.ExceptionCase);
		if (item != null) DBConnection.delete(item);
	}

	public static List getExceptions () {
		List resultFin = new ArrayList();
		List items = DBConnection.getStoredItems(Item.ExceptionCase);
		for (Object o : items) {
			var item = (StoredItem) o;
			var emidsStr = item.getEmid();

			// split the string
			List<EntityMID> emids = new ArrayList<>();
			for (var t : emidsStr.split(",")) {
				emids.add(new EntityMID(t));
			}
			List result = new ArrayList();
			result.add(emids);
			result.add(item.getClassID());
			result.add(item.getProcletID());
			result.add(item.getBlockID());
			resultFin.add(result);
		}
		return resultFin;
	}

	public static List<InteractionNode> getExceptionCasesSelected () {
		List<InteractionNode> nodes = new ArrayList<>();
		List items = DBConnection.getStoredItems(Item.ExceptionCaseSelection);
		for (Object o : items) {
			var item = (StoredItem) o;
			if (item.isSelected()) {
				nodes.add(item.newInteractionNode());
			}
		}
		return nodes;
	}

	public static void publishExceptionCase(String classID, String procletID, String blockID) {
		var item = new StoredItem(classID, procletID, blockID, Item.ExceptionCaseSelection);
		item.setSelected(true);
		DBConnection.insert(item);
	}

	public static boolean isExceptionCaseSelectedUser(String classID, String procletID, String blockID) {
		var item = DBConnection.getSelectedStoredItem(classID, procletID, blockID,
				Item.ExceptionCaseSelection);
		return (item != null);
	}

	public static void setExceptionCaseSelected(String classID, String procletID, String blockID) {
		DBConnection.setStoredItemSelected(classID, procletID, blockID,
				Item.ExceptionCaseSelection);
	}

	public static InteractionNode getExceptionCaseSelected() {
		List items = DBConnection.getStoredItems(Item.ExceptionCaseSelection);
		for (Object o : items) {
			if (((StoredItem) o).isSelected()) {
				return ((StoredItem) o).newInteractionNode();
			}
		}
		return null;
	}

	public static void deleteExceptionCaseSelected(String classID, String procletID, String blockID) {
		var item = DBConnection.getStoredItem(classID, procletID, blockID,
				Item.ExceptionCaseSelection);
		if (item != null) DBConnection.delete(item);
	}

	private void pushAvailableEmidsCaseExceptionToUser(String classID, String procletID,
	                                                    String blockID, List<EntityMID> emids) {
		for (var emid : emids) {
			DBConnection.insert(new StoredItem(classID, procletID, blockID, emid.getValue(),
					Item.EmidExceptionCaseSelection));
		}
	}

	private void deleteEmidCaseExceptionToUser(String emidStr) {
		var query = "delete from StoredItem as s where s.emid='" + emidStr +
				"' and s.itemType=" + Item.EmidExceptionCaseSelection.ordinal();
		DBConnection.execUpdate(query);
	}

	public static void deleteAllEmidsCaseExceptionToUser() {
		DBConnection.deleteAll(Item.EmidExceptionCaseSelection);
	}

	private void deleteAvailableEmidsCaseExceptionToUser(String classID, String procletID, String blockID) {
		var item = DBConnection.getStoredItem(classID, procletID, blockID,
				Item.EmidExceptionCaseSelection);
		if (item != null) DBConnection.delete(item);
	}

	public static List<EntityMID> getAvailableEmidsCaseExceptionToUser() {
		List<EntityMID> emidList = new ArrayList<>();
		List items = DBConnection.getStoredItems(Item.EmidExceptionCaseSelection);
		for (Object o : items) {
			emidList.add(((StoredItem) o).newEntityMID());
		}
		return emidList;
	}

	public static void setEmidSelectedCaseException(EntityMID emid) {
		List items = DBConnection.getStoredItems(Item.EmidExceptionCaseSelection);
		for (Object o : items) {
			var item = (StoredItem) o;
			if (item.getEmid().equals(emid.getValue())) {
				item.setSelected(true);
				DBConnection.update(item);
			}
		}
	}


	public static void main(String [] args) {
		CompleteCaseDeleteCase.deleteException("visit", "p2", "exception");
		List exc = CompleteCaseDeleteCase.getExceptions();
		LogManager.getLogger(CompleteCaseDeleteCase.class).info("Exceptions: {}", exc);
	}

}
