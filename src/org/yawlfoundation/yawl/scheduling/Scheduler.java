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

package org.yawlfoundation.yawl.scheduling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.scheduling.persistence.DataMapper;
import org.yawlfoundation.yawl.scheduling.resource.ResourceServiceInterface;
import org.yawlfoundation.yawl.scheduling.util.PropertyReader;
import org.yawlfoundation.yawl.scheduling.util.Utils;
import org.yawlfoundation.yawl.scheduling.util.XMLUtils;

import jakarta.xml.datatype.Duration;
import java.util.*;


/**
 * (re)schedules RUPs automatically
 *
 * @author tbe
 * @version $Id$
 */
public class Scheduler implements Constants
{
	private static Logger logger = LogManager.getLogger(Scheduler.class);

	private DataMapper dataMapper;
	private ResourceServiceInterface rs;
    private final boolean debug = true;

    public Scheduler() {
        dataMapper = new DataMapper();
        rs = ResourceServiceInterface.getInstance();
    }

	/**
	 * Adds a Duration to a Date using UTC calendar to avoid DST offset errors.
	 * Duration.addTo(Date) uses the default timezone Calendar internally, which
	 * can introduce a one-hour shift when the duration spans a DST boundary.
	 */
	private static Date addDurationUtc(Date date, Duration duration) {
		Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		utcCal.setTimeInMillis(date.getTime());
		duration.addTo(utcCal);
		return utcCal.getTime();
	}


	/**
	 * Sets times of activities when FROM and DURATION are given. Reschedules
	 * colliding RUPs, defined as: all RUPs that define the same resources at
	 * the same time as the preceding RUP. Checks collisions by resource Id,
	 * Role, and Category.
	 *
	 * @param rup the resource utilisation plan document
	 * @param activity the activity element to set times for
	 * @param withValidation whether to validate during processing
	 * @param rescheduleCollidingRUPs whether to reschedule colliding RUPs
	 * @param defaultDuration fallback duration if none specified
	 */
	public boolean setTimes(Document rup, Element activity, boolean withValidation,
                            boolean rescheduleCollidingRUPs, Duration defaultDuration) {
		String caseId = XMLUtils.getCaseId(rup);
		String activityName = activity.getChildText(XML_ACTIVITYNAME);
		boolean hasRescheduledRUPs = false;

		try	{
			// 1) reschedule rup
			setTimes(rup, activity, withValidation, new ArrayList<String>(), defaultDuration);

			// rescheduling colliding rups
			if (rescheduleCollidingRUPs) {
				List<Document> allCollidingRUPs = new ArrayList<Document>();
				List<String> allCollidingRUPCaseIds = new ArrayList<String>();
				allCollidingRUPCaseIds.add(caseId);

				// 2) collect all potential colliding rups and remove their
				// reservations
				List<Document> collidingRUPs = getCollidingRups(rup, allCollidingRUPCaseIds);
				while (!collidingRUPs.isEmpty()) {
					List<Document> newCollidingRUPs = new ArrayList<Document>();
					for (Document collidingRUP : collidingRUPs)	{
						String collCaseId = null;
						try	{
							collCaseId = XMLUtils.getCaseId(collidingRUP);
							if (allCollidingRUPCaseIds.contains(collCaseId)) continue;

							// remove all reservations in RS by saving rup without
							// reservations
							Map<String, List<Element>> collRes = rs.removeReservations(collidingRUP, null);
							try	{
								logger.debug("delete reservations from collidingRUP: " + collCaseId);
								collidingRUP = rs.saveReservations(collidingRUP, false, false);
							}
							finally	{
								rs.addReservations(collidingRUP, collRes);
							}

							allCollidingRUPs.add(collidingRUP);
							allCollidingRUPCaseIds.add(collCaseId);

							newCollidingRUPs.addAll(getCollidingRups(collidingRUP, allCollidingRUPCaseIds));
						}
						catch (Exception e)	{
							logger.error("cannot collect caseId: " +
                                    (collCaseId == null ? "null" : collCaseId), e);
							XMLUtils.addErrorValue(rup.getRootElement(), withValidation,
                                    "msgRescheduleError", activityName, e.getMessage());
						}
					}
					collidingRUPs = newCollidingRUPs;
				}

				if (!allCollidingRUPs.isEmpty()) {
					// 3) save rup with new times, should not conflict with other rups
					Set<String> errors = SchedulingService.getInstance().optimizeAndSaveRup(
                            rup, "reschedulingRUP", null, false);
					logger.debug("----------------save rescheduled rup caseId: " + caseId + ", errors: "
							+ Utils.toString(errors));
				}

				// 4) sort colliding rups
				Collections.sort(allCollidingRUPs, new Comparator<Document>() {
					public int compare(Document rup1, Document rup2) {
						Date earlFrom1 = XMLUtils.getEarliestBeginDate(rup1);
						Date earlFrom2 = XMLUtils.getEarliestBeginDate(rup2);
						long timeGap = earlFrom1.getTime() - earlFrom2.getTime();
						if (timeGap > 0) return 1;
						else if (timeGap < 0) return -1;
						else return 0;
					}
				});

				// 5) find new time slot for each colliding rup and save it.
				// Searches both forward and backward within maxTimeslotPeriod.
				for (Document collidingRUP : allCollidingRUPs) {
					String collCaseId = null;
					try	{
						collCaseId = XMLUtils.getCaseId(collidingRUP);
						if (collCaseId.equals(caseId)) continue;

						hasRescheduledRUPs = findTimeSlot(collidingRUP, true) || hasRescheduledRUPs;

                        SchedulingService.getInstance().optimizeAndSaveRup(collidingRUP,
                                "reschedulingCollidingRUPs", null, false);
						logger.debug("save rescheduled colliding caseId: " + collCaseId + ", errors: "
								+ Utils.toString(XMLUtils.getErrors(collidingRUP.getRootElement())));
						logger.info("caseId: " + collCaseId + " successfully rescheduled");
					}
					catch (Exception e) {
						logger.error("cannot reschedule caseId: " +
                                (collCaseId == null ? "null" : collCaseId), e);
						XMLUtils.addErrorValue(rup.getRootElement(), withValidation,
                                "msgRescheduleError", activityName,	e.getMessage());
					}
				}
			}
		}
		catch (Exception e)	{
			logger.error("error during rescheduling caseId: " +
                    (caseId == null ? "null" : caseId), e);
			XMLUtils.addErrorValue(rup.getRootElement(), withValidation,
                    "msgRescheduleError", activityName,	e.getMessage());
		}

		return hasRescheduledRUPs;
	}

	/**
	 * Sets the TO time of an activity depending on FROM and DURATION. Sets the FROM
	 * time of previous and following activities and recurses to set their TO times.
	 *
	 * <p>Currently uses Min as a fixed value (Max is not considered), and only
	 * handles one utilisation relation per activity.
	 *
	 * @param rup the resource utilisation plan document
	 * @param activity the activity element to process
	 * @param withValidation whether to validate during processing
	 * @param activityNamesProcessed list of already processed activity names to prevent cycles
	 * @param defaultDuration fallback duration if none specified
	 */
	private void setTimes(Document rup, Element activity, boolean withValidation,
                          List<String> activityNamesProcessed, Duration defaultDuration) {
		String activityName = activity.getChildText(XML_ACTIVITYNAME);
		Element durationElem = activity.getChild(XML_DURATION);
		Duration duration = XMLUtils.getDurationValue(durationElem, withValidation);
		Element from = activity.getChild(XML_FROM);
		Date fromDate = XMLUtils.getDateValue(from, withValidation);
		Element to = activity.getChild(XML_TO);
		Date toDate = XMLUtils.getDateValue(to, withValidation); // can be null
		String requestType = activity.getChildText(XML_REQUESTTYPE);

		if (requestType.equals("EOU")) {    // calculate duration
			toDate = XMLUtils.getDateValue(to, withValidation);
			XMLUtils.setDurationValue(durationElem, toDate.getTime() - fromDate.getTime());
		}
		else if (fromDate == null) {
            if (toDate != null) {
			    if (duration != null) {
				    fromDate = addDurationUtc(toDate, duration.negate());
				    XMLUtils.setDateValue(from, fromDate);
                } else if (defaultDuration != null) {
				    fromDate = addDurationUtc(toDate, defaultDuration.negate());
				    XMLUtils.setDateValue(from, fromDate);
				}
			}
		}
		else {
			if (duration != null) {
				toDate = addDurationUtc(fromDate, duration);
				XMLUtils.setDateValue(to, toDate);
			}
			else if (defaultDuration != null) {
				toDate = addDurationUtc(fromDate, defaultDuration);
				XMLUtils.setDateValue(to, toDate);
			}
		}

		activityNamesProcessed.add(activityName);
		logger.debug(activityName + ", set from: " + from.getText() + ", to: " +
                to.getText() + ", duration: " + durationElem.getText());

		// set times of following activities
		String xpath = XMLUtils.getXPATH_ActivityElement(activityName, XML_UTILISATIONREL, null);
		List relations = XMLUtils.getXMLObjects(rup, xpath);
		for (Object o : relations) {
            Element relation = (Element) o;
			String otherActivityName = relation.getChildText(XML_OTHERACTIVITYNAME);
			if (activityNamesProcessed.contains(otherActivityName)) {
                continue; // activity has been processed already
			}

			Duration min = XMLUtils.getDurationValue(relation.getChild(XML_MIN), withValidation);
			List otherActivities = XMLUtils.getXMLObjects(rup, XMLUtils.getXPATH_Activities(otherActivityName));
			for (Object obj : otherActivities) {
                Element otherActivity = (Element) obj;
				Date thisDate;
				if (relation.getChildText(XML_THISUTILISATIONTYPE).equals(UTILISATION_TYPE_BEGIN)) {
					thisDate = new Date(fromDate.getTime());
				}
				else if (duration != null) {
					thisDate = new Date(toDate.getTime());
				}
				else {
					continue;
				}
				thisDate = addDurationUtc(thisDate, min);

				if (relation.getChildText(XML_OTHERUTILISATIONTYPE).equals(UTILISATION_TYPE_END)) {
					Duration otherDuration = XMLUtils.getDurationValue(
                            otherActivity.getChild(XML_DURATION), withValidation);
					Duration negDur = (otherDuration == null ? defaultDuration : otherDuration).negate();
					thisDate = addDurationUtc(thisDate, negDur);
				}
				XMLUtils.setDateValue(otherActivity.getChild(XML_FROM), thisDate);

				setTimes(rup, otherActivity, withValidation, activityNamesProcessed, defaultDuration);
			}
		}

		// set time of previous activities if this activity is not started
		if (!requestType.equals("POU"))	{
			return;
		}
		xpath = XMLUtils.getXPATH_ActivityElement(null, XML_UTILISATIONREL, null);
		xpath += "[" + XML_OTHERACTIVITYNAME + "/text()='" + activityName + "']";
		relations = XMLUtils.getXMLObjects(rup, xpath);
		for (Object o : relations) {
            Element relation = (Element) o;
			Element otherActivity = relation.getParentElement();
			if (activityNamesProcessed.contains(otherActivity.getChildText(XML_ACTIVITYNAME))) {
				continue; // activity has been processed already
			}

			Duration min = XMLUtils.getDurationValue(relation.getChild(XML_MIN), withValidation);
			Date otherDate;
			if (relation.getChildText(XML_OTHERUTILISATIONTYPE).equals(UTILISATION_TYPE_BEGIN))	{
				fromDate = XMLUtils.getDateValue(from, withValidation);
				otherDate = new Date(fromDate.getTime());
			}
			else if (duration != null) {
				toDate = XMLUtils.getDateValue(to, withValidation);
				otherDate = new Date(toDate.getTime());
			}
			else {
				continue;
			}
			otherDate = addDurationUtc(otherDate, min.negate());

			if (relation.getChildText(XML_THISUTILISATIONTYPE).equals(UTILISATION_TYPE_END)) {
				Duration otherDuration = XMLUtils.getDurationValue(otherActivity.getChild(XML_DURATION), withValidation);
				Duration negDur = (otherDuration == null ? defaultDuration : otherDuration).negate();
				otherDate = addDurationUtc(otherDate, negDur);
			}
			XMLUtils.setDateValue(otherActivity.getChild(XML_FROM), otherDate);

			setTimes(rup, otherActivity, withValidation, activityNamesProcessed, defaultDuration);
		}
	}

	/**
	 * find time slot for rup
	 *
	 * @param rup
	 */
	public boolean findTimeSlot(Document rup, boolean withValidation) {
		String caseId = XMLUtils.getCaseId(rup);
		logger.debug("reschedule caseId " + caseId);
		boolean isRescheduledRUP = false;

		try	{
			// Map with activities and their list of possible offsets for adding to
			// FROM
			Map<String, List<List<Long>>> actOffsets = new HashMap<String, List<List<Long>>>();
			Date searchStartDate = null;
			Date searchEndDate = null;

			// get availabilities for all resources of rup between from and to
			String xpath = XMLUtils.getXPATH_Activities();
			List<Element> activities = XMLUtils.getXMLObjects(rup, xpath);
			for (Element activity : activities)
			{
				String activityName = activity.getChildText(XML_ACTIVITYNAME);
				List<Element> reservations = activity.getChildren(XML_RESERVATION);
				if (reservations.isEmpty())
				{
					continue;
				}

				actOffsets.put(activityName, new ArrayList<List<Long>>());
				Date from = XMLUtils.getDateValue(activity.getChild(XML_FROM), withValidation);
				Date to = XMLUtils.getDateValue(activity.getChild(XML_TO), withValidation);

				// compute search window for free timeslots: from
				// maxTimeslotPeriod hours before earliest FROM to
				// maxTimeslotPeriod hours after earliest FROM
				if (searchEndDate == null)
				{
					Integer maxTimeslotPeriod =
                            PropertyReader.getInstance().getIntProperty(
                                    PropertyReader.SCHEDULING, "maxTimeslotPeriod");

					Calendar calEnd = Calendar.getInstance();
					calEnd.setTime(from);
					calEnd.add(Calendar.HOUR_OF_DAY, maxTimeslotPeriod);
					searchEndDate = calEnd.getTime();

					// also search backward for earlier timeslots
					Calendar calStart = Calendar.getInstance();
					calStart.setTime(from);
					calStart.add(Calendar.HOUR_OF_DAY, -maxTimeslotPeriod);
					// don't search before current time
					Date now = new Date();
					searchStartDate = calStart.getTime().before(now) ? now : calStart.getTime();
				}

				long duration = to.getTime() - from.getTime();

				// Track consumed workload per resource+timeslot to prevent
				// over-counting when multiple reservations use the same resource
				Map<String, Map<Integer, Integer>> consumedWorkloadMap =
						new HashMap<String, Map<Integer, Integer>>();

				for (Element reservation : reservations)
				{
					Element resource = null;
					try
					{
						resource = reservation.getChild(XML_RESOURCE);
						String resourceId = resource.getChildText(XML_ID);
						int workload = XMLUtils.getIntegerValue(reservation.getChild(XML_WORKLOAD), withValidation);
						List<Element> timeslots = rs.getAvailabilities(resource, searchStartDate, searchEndDate);

						// initialise consumed map for this resource if needed
						if (!consumedWorkloadMap.containsKey(resourceId)) {
							consumedWorkloadMap.put(resourceId, new HashMap<Integer, Integer>());
						}
						Map<Integer, Integer> consumed = consumedWorkloadMap.get(resourceId);

						for (int i = 0; i < timeslots.size(); i++)
						{
							Element timeslot = timeslots.get(i);
							Date tsFrom = XMLUtils.getDateValue(timeslot.getChild("start"), withValidation);
							Date tsTo = XMLUtils.getDateValue(timeslot.getChild("end"), withValidation);
							long tsDuration = tsTo.getTime() - tsFrom.getTime();
							long offsetMin = tsFrom.getTime() - from.getTime();
							long offsetMax = tsTo.getTime() - to.getTime();
							int tsAvailability = XMLUtils.getIntegerValue(timeslot.getChild("availability"), withValidation);

							// subtract already consumed workload for this resource+timeslot
							int alreadyConsumed = consumed.containsKey(i) ? consumed.get(i) : 0;
							int tsAvailabilityDiff = tsAvailability - alreadyConsumed - workload;

							// try to pass activity into timeslot (negative offsets
							// represent earlier timeslots)
							if (tsAvailabilityDiff >= 0 && duration <= tsDuration)
							{
								// record consumed workload for this timeslot
								consumed.put(i, alreadyConsumed + workload);

								List<Long> offset = new ArrayList<Long>();
								offset.add(offsetMin);
								offset.add(offsetMax);
								List<List<Long>> offsets = actOffsets.get(activityName);
								offsets.add(offset);
							}
						}
					}
					catch (Exception e)
					{
						logger.error("error get availability for resource:" + Utils.element2String(resource, true), e);
					}
				}
			}

			// get average of offsets of each activity
			List<List<Long>> offsetsAvg = new ArrayList<List<Long>>();
			for (String activityName : actOffsets.keySet())
			{
				List<List<Long>> offsets = actOffsets.get(activityName);
				if (offsetsAvg.isEmpty())
				{
					offsetsAvg.addAll(offsets);
				}
				else
				{
					xpath = XMLUtils.getXPATH_Activities(activityName);
					Element activity = (Element) XMLUtils.getXMLObjects(rup, xpath).get(0);
					Date from = XMLUtils.getDateValue(activity.getChild(XML_FROM), withValidation);
					Date to = XMLUtils.getDateValue(activity.getChild(XML_TO), withValidation);
					long duration = to.getTime() - from.getTime();

					List<List<Long>> offsetsAvgNew = new ArrayList<List<Long>>();
					for (List<Long> offset : offsets)
					{
						for (List<Long> offsetAvg : offsetsAvg)
						{
							Long offsetMin = Math.max(offset.get(0), offsetAvg.get(0));
							Long offsetMax = Math.min(offset.get(1), offsetAvg.get(1));
							long offsetDuration = offsetMax - offsetMin;
							if (offsetDuration >= duration)
							{
								List<Long> offsetAvgNew = new ArrayList<Long>();
								offsetAvgNew.add(offsetMin);
								offsetAvgNew.add(offsetMax);
								offsetsAvgNew.add(offsetAvgNew);
							}
						}
					}
					offsetsAvg = offsetsAvgNew;
				}
			}
			logger.debug("caseId: " + caseId + ", offsetsAvg: " + Utils.toString(offsetsAvg));

			// add min of first offset to each activity
			if (offsetsAvg.isEmpty())
			{
				logger.error("no timeslot available for case: " + caseId);
				XMLUtils.addErrorValue(rup.getRootElement(), withValidation, "msgTimeslotUnavailable");
				isRescheduledRUP = true;
			}
			else
			{
				// pick the offset with the smallest absolute value (least disruption)
				long offsetAvgMin = Long.MAX_VALUE;
				for (List<Long> offsetAvg : offsetsAvg)
				{
					long candidate = offsetAvg.get(0);
					if (Math.abs(candidate) < Math.abs(offsetAvgMin)) {
						offsetAvgMin = candidate;
					}
				}
				logger.info("timeslot for case: " + caseId + " found, offset=" + offsetAvgMin + " ("
						+ Utils.date2String(new Date(Math.abs(offsetAvgMin)), Utils.TIME_PATTERN, TimeZone.getTimeZone("GMT")) + " h)");
				if (offsetAvgMin != 0)
				{
					for (Element activity : activities)
					{
						String activityName = activity.getChildText(XML_ACTIVITYNAME);

						Element from = activity.getChild(XML_FROM);
						Date fromDate = XMLUtils.getDateValue(from, false);
						fromDate = new Date(fromDate.getTime() + offsetAvgMin);
						XMLUtils.setDateValue(from, fromDate);

						Element to = activity.getChild(XML_TO);
						Date toDate = XMLUtils.getDateValue(to, false);
						toDate = new Date(toDate.getTime() + offsetAvgMin);
						XMLUtils.setDateValue(to, toDate);
						isRescheduledRUP = true;
					}
				}
			}
		}
		catch (Exception e)
		{
			logger.error("cannot found timeslot for case: " + caseId, e);
			XMLUtils.addErrorValue(rup.getRootElement(), withValidation, "msgTimeslotError", e.getMessage());
		}

		return isRescheduledRUP;
	}

	/**
	 * Finds potentially colliding RUPs, defined as: all RUPs that define the
	 * same resources at the same time as the preceding RUP. Checks collisions
	 * by resource Id, Role, and Category.
	 *
	 * @param rup the resource utilisation plan to check collisions for
	 * @param excludedCaseIds case IDs to exclude from the collision search
	 * @return list of potentially colliding RUP documents
	 */
	private List<Document> getCollidingRups(Document rup, List<String> excludedCaseIds) {

		// find earliest FROM and end-of-range TO time of rup
		Date beginBound = XMLUtils.getEarliestBeginDate(rup);
		Date endBound = XMLUtils.getLatestEndDate
				(rup);

		// collect resource Ids, Roles and Categories from this RUP
		Set<String> rupResourceIds = new HashSet<String>();
		Set<String> rupRoleIds = new HashSet<String>();
		Set<String> rupCategoryIds = new HashSet<String>();
		String resXpath = XMLUtils.getXPATH_ActivitiesElement(null, XML_RESERVATION, null);
		List<Element> rupReservations = XMLUtils.getXMLObjects(rup, resXpath);
		for (Element reservation : rupReservations) {
			Element resource = reservation.getChild(XML_RESOURCE);
			if (resource != null) {
				String resId = resource.getChildText(XML_ID);
				String roleId = resource.getChildText(XML_ROLE);
				String categoryId = resource.getChildText(XML_CATEGORY);
				if (resId != null && !resId.isEmpty()) rupResourceIds.add(resId);
				if (roleId != null && !roleId.isEmpty()) rupRoleIds.add(roleId);
				if (categoryId != null && !categoryId.isEmpty()) rupCategoryIds.add(categoryId);
			}
		}

        List<Case> collidingCases = dataMapper.getRupsByInterval(
                beginBound, endBound, excludedCaseIds, true);
        List<Document> allCandidateRups = SchedulingService.getInstance().getRupList(collidingCases);

		// filter candidates to those that actually share a resource by Id, Role, or Category
		List<Document> collidingRups = new ArrayList<Document>();
		for (Document candidateRup : allCandidateRups) {
			if (hasResourceOverlap(candidateRup, rupResourceIds, rupRoleIds, rupCategoryIds)) {
				collidingRups.add(candidateRup);
			}
		}

		if (debug) {
			List<String> caseIds = new ArrayList<String>();
			for (Document collidingRUP : collidingRups)	{
				caseIds.add(XMLUtils.getCaseId(collidingRUP));
			}
			logger.debug("found " + collidingRups.size() +
                    " potential colliding rups: " + Utils.getCSV(caseIds));
		}
		return collidingRups;
	}

	/**
	 * Checks whether a candidate RUP has any resource overlap with the given
	 * resource Ids, Roles, or Categories.
	 */
	private boolean hasResourceOverlap(Document candidateRup,
			Set<String> resourceIds, Set<String> roleIds, Set<String> categoryIds) {
		String resXpath = XMLUtils.getXPATH_ActivitiesElement(null, XML_RESERVATION, null);
		List<Element> reservations = XMLUtils.getXMLObjects(candidateRup, resXpath);
		for (Element reservation : reservations) {
			Element resource = reservation.getChild(XML_RESOURCE);
			if (resource == null) continue;

			String resId = resource.getChildText(XML_ID);
			if (resId != null && !resId.isEmpty() && resourceIds.contains(resId)) {
				return true;
			}

			String roleId = resource.getChildText(XML_ROLE);
			if (roleId != null && !roleId.isEmpty() && roleIds.contains(roleId)) {
				return true;
			}

			String categoryId = resource.getChildText(XML_CATEGORY);
			if (categoryId != null && !categoryId.isEmpty() && categoryIds.contains(categoryId)) {
				return true;
			}
		}
		return false;
	}

}
