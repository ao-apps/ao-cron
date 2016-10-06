/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2011, 2013, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-cron.
 *
 * ao-cron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-cron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-cron.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.cron;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Matches individual values within a Schedule.
 *
 * @see  Schedule
 *
 * @author  AO Industries, Inc.
 */
abstract public class Matcher {

	abstract int getStepOffset();

	abstract public boolean matches(int value);

	/**
	 * Parses a minute matcher.
	 */
	public static Matcher parseMinute(String str) throws IllegalArgumentException {
		Map<String,Integer> nameMap = Collections.emptyMap();
		return parseMatcher(str, 0, 59, 60, nameMap);
	}

	/**
	 * Parses a hour matcher.
	 */
	public static Matcher parseHour(String str) throws IllegalArgumentException {
		Map<String,Integer> nameMap = Collections.emptyMap();
		return parseMatcher(str, 0, 23, 24, nameMap);
	}

	/**
	 * Parses a dayOfMonth matcher.
	 */
	public static Matcher parseDayOfMonth(String str) throws IllegalArgumentException {
		Map<String,Integer> nameMap = Collections.emptyMap();
		return parseMatcher(str, 1, 31, 32, nameMap);
	}

	private static final Map<String,Integer> monthNameMap = new HashMap<String,Integer>(23 * 4 / 3 + 1);
	static {
		monthNameMap.put("Jan", 1);
		monthNameMap.put("January", 1);
		monthNameMap.put("Feb", 2);
		monthNameMap.put("February", 2);
		monthNameMap.put("Mar", 3);
		monthNameMap.put("March", 3);
		monthNameMap.put("Apr", 4);
		monthNameMap.put("April", 4);
		monthNameMap.put("May", 5);
		monthNameMap.put("Jun", 6);
		monthNameMap.put("June", 6);
		monthNameMap.put("Jul", 7);
		monthNameMap.put("July", 7);
		monthNameMap.put("Aug", 8);
		monthNameMap.put("August", 8);
		monthNameMap.put("Sep", 9);
		monthNameMap.put("September", 9);
		monthNameMap.put("Oct", 10);
		monthNameMap.put("October", 10);
		monthNameMap.put("Nov", 11);
		monthNameMap.put("November", 11);
		monthNameMap.put("Dec", 12);
		monthNameMap.put("December", 12);
	}

	/**
	 * Parses a month matcher.
	 * Note: months are 1-12 like cron, not 0-11 like Calendar.
	 */
	public static Matcher parseMonth(String str) throws IllegalArgumentException {
		return parseMatcher(str, 1, 12, 13, monthNameMap);
	}

	private static final Map<String,Integer> dayOfWeekNameMap = new HashMap<String,Integer>(14 * 4 / 3 + 1);
	static {
		dayOfWeekNameMap.put("Sun", 0);
		dayOfWeekNameMap.put("Sunday", 0);
		dayOfWeekNameMap.put("Mon", 1);
		dayOfWeekNameMap.put("Monday", 1);
		dayOfWeekNameMap.put("Tue", 2);
		dayOfWeekNameMap.put("Tuesday", 2);
		dayOfWeekNameMap.put("Wed", 3);
		dayOfWeekNameMap.put("Wednesday", 3);
		dayOfWeekNameMap.put("Thu", 4);
		dayOfWeekNameMap.put("Thursday", 4);
		dayOfWeekNameMap.put("Fri", 5);
		dayOfWeekNameMap.put("Friday", 5);
		dayOfWeekNameMap.put("Sat", 6);
		dayOfWeekNameMap.put("Saturday", 6);
	}

	/**
	 * Parses a dayOfWeek matcher.
	 * Note: Monday is 1, not 2 like Calendar.
	 */
	public static Matcher parseDayOfWeek(String str) throws IllegalArgumentException {
		return parseMatcher(str, 0, 7, 7, dayOfWeekNameMap);
	}

	private static int parseInt(String str, Map<String,Integer> nameMap) throws NumberFormatException {
		for(Map.Entry<String,Integer> entry : nameMap.entrySet()) {
			if(entry.getKey().equalsIgnoreCase(str)) return entry.getValue();
		}
		return Integer.parseInt(str);
	}

	/**
	 * Parses a cron value, supporting lists, asterisk, and ranges, and steps.
	 */
	public static Matcher parseMatcher(String str, int minimum, int maximum, int modulus, Map<String,Integer> nameMap) throws IllegalArgumentException {
		// Handle list
		if(str.indexOf(',')!=-1) {
			Collection<Matcher> list = new ArrayList<Matcher>();
			StringTokenizer st = new StringTokenizer(str, ",");
			while(st.hasMoreTokens()) list.add(parseMatcher(st.nextToken(), minimum, maximum, modulus, nameMap));
			return new List(list);
		}

		// Handle step
		int pos = str.indexOf('/');
		if(pos!=-1) {
			return new Step(
				parseMatcher(
					str.substring(0, pos),
					minimum,
					maximum,
					modulus,
					nameMap
				),
				Integer.parseInt(str.substring(pos+1))
			);
		}

		// Handle wildcard
		if("*".equals(str)) return new Asterisk(minimum);

		// Handle ranges
		pos = str.indexOf('-');
		if(pos!=-1) {
			int begin = parseInt(str.substring(0, pos), nameMap);
			if(begin<minimum || begin>maximum) throw new IllegalArgumentException();
			int end = parseInt(str.substring(pos+1), nameMap);
			if(end<minimum || end>maximum) throw new IllegalArgumentException();
			return new Range(begin % modulus, end % modulus);
		}

		// Single value
		int value = parseInt(str, nameMap);
		if(value<minimum || value>maximum) throw new IllegalArgumentException();
		return new Value(value % modulus);
	}

	/**
	 * Matches any of a list.
	 */
	public static class List extends Matcher {

		private final Matcher[] list;

		public List(Collection<Matcher> list) {
			this.list = list.toArray(new Matcher[list.size()]);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for(int i=0;i<list.length;i++) {
				if(i>0) sb.append(',');
				sb.append(list[i]);
			}
			return sb.toString();
		}

		@Override
		int getStepOffset() {
			throw new AssertionError(); // Does not apply to list matcher, only the individual matchers
		}

		@Override
		public boolean matches(int value) {
			for(Matcher matcher : list) {
				if(matcher.matches(value)) return true;
			}
			return false;
		}
	}

	/**
	 * Matches a step.
	 */
	public static class Step extends Matcher {

		private final Matcher matcher;
		private final int step;

		public Step(Matcher matcher, int step) {
			this.matcher = matcher;
			this.step = step;
		}

		@Override
		public String toString() {
			return matcher+"/"+step;
		}

		@Override
		int getStepOffset() {
			throw new AssertionError(); // Does not apply to step matcher, only the specific matcher
		}

		@Override
		public boolean matches(int value) {
			return
				matcher.matches(value)
				&& ((value - matcher.getStepOffset()) % step)==0
			;
		}
	}

	/**
	 * Matches any value.
	 */
	public static class Asterisk extends Matcher {

		private final int stepOffset;

		public Asterisk(int stepOffset) {
			this.stepOffset = stepOffset;
		}

		@Override
		public String toString() {
			return "*";
		}

		@Override
		int getStepOffset() {
			return stepOffset;
		}

		@Override
		public boolean matches(int value) {
			return true;
		}
	}

	/**
	 * Matches a specific range.
	 */
	public static class Range extends Matcher {

		private final int begin;
		private final int end;

		public Range(int begin, int end) {
			this.begin = begin;
			this.end = end;
		}

		@Override
		public String toString() {
			return begin+"-"+end;
		}

		@Override
		int getStepOffset() {
			return begin;
		}

		@Override
		public boolean matches(int value) {
			if(begin<=end) {
				// No wrap-around
				return value>=begin && value<=end;
			} else {
				// Wrap-around
				return value>=begin || value<=end;
			}
		}
	}

	/**
	 * Matches a single value.
	 */
	public static class Value extends Matcher {

		private final int value;

		public Value(int value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return Integer.toString(value);
		}

		@Override
		int getStepOffset() {
			return value;
		}

		@Override
		public boolean matches(int value) {
			return this.value == value;
		}
	}
}
