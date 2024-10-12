/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2011, 2013, 2015, 2016, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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
 * along with ao-cron.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.cron;

import com.aoapps.collections.AoCollections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Matches individual values within a Schedule.
 *
 * @see  Schedule
 *
 * @author  AO Industries, Inc.
 */
public abstract class Matcher {

  abstract int getStepOffset();

  /**
   * Checks if matches the given value.
   */
  public abstract boolean matches(int value);

  /**
   * Parses a minute matcher.
   */
  public static Matcher parseMinute(String str) throws IllegalArgumentException {
    Map<String, Integer> nameMap = Collections.emptyMap();
    return parseMatcher(str, 0, 59, 60, nameMap);
  }

  /**
   * Parses a hour matcher.
   */
  public static Matcher parseHour(String str) throws IllegalArgumentException {
    Map<String, Integer> nameMap = Collections.emptyMap();
    return parseMatcher(str, 0, 23, 24, nameMap);
  }

  /**
   * Parses a dayOfMonth matcher.
   */
  public static Matcher parseDayOfMonth(String str) throws IllegalArgumentException {
    Map<String, Integer> nameMap = Collections.emptyMap();
    return parseMatcher(str, 1, 31, 32, nameMap);
  }

  private static final Map<String, Integer> monthNameMap = AoCollections.newHashMap(23);

  static {
    monthNameMap.put("jan", 1);
    monthNameMap.put("january", 1);
    monthNameMap.put("feb", 2);
    monthNameMap.put("february", 2);
    monthNameMap.put("mar", 3);
    monthNameMap.put("march", 3);
    monthNameMap.put("apr", 4);
    monthNameMap.put("april", 4);
    monthNameMap.put("may", 5);
    monthNameMap.put("jun", 6);
    monthNameMap.put("june", 6);
    monthNameMap.put("jul", 7);
    monthNameMap.put("july", 7);
    monthNameMap.put("aug", 8);
    monthNameMap.put("august", 8);
    monthNameMap.put("sep", 9);
    monthNameMap.put("september", 9);
    monthNameMap.put("oct", 10);
    monthNameMap.put("october", 10);
    monthNameMap.put("nov", 11);
    monthNameMap.put("november", 11);
    monthNameMap.put("dec", 12);
    monthNameMap.put("december", 12);
  }

  /**
   * Parses a month matcher.
   * Note: months are 1-12 like cron, not 0-11 like Calendar.
   */
  public static Matcher parseMonth(String str) throws IllegalArgumentException {
    return parseMatcher(str, 1, 12, 13, monthNameMap);
  }

  private static final Map<String, Integer> dayOfWeekNameMap = AoCollections.newHashMap(14);

  static {
    dayOfWeekNameMap.put("sun", 0);
    dayOfWeekNameMap.put("sunday", 0);
    dayOfWeekNameMap.put("mon", 1);
    dayOfWeekNameMap.put("monday", 1);
    dayOfWeekNameMap.put("tue", 2);
    dayOfWeekNameMap.put("tuesday", 2);
    dayOfWeekNameMap.put("wed", 3);
    dayOfWeekNameMap.put("wednesday", 3);
    dayOfWeekNameMap.put("thu", 4);
    dayOfWeekNameMap.put("thursday", 4);
    dayOfWeekNameMap.put("fri", 5);
    dayOfWeekNameMap.put("friday", 5);
    dayOfWeekNameMap.put("sat", 6);
    dayOfWeekNameMap.put("saturday", 6);
  }

  /**
   * Parses a dayOfWeek matcher.
   * Note: Monday is 1, not 2 like Calendar.
   */
  public static Matcher parseDayOfWeek(String str) throws IllegalArgumentException {
    return parseMatcher(str, 0, 7, 7, dayOfWeekNameMap);
  }

  /**
   * Confirms all keys are lower-case, for assertions.
   */
  private static boolean assertAllKeysLowerCase(Map<String, ?> map) {
    for (String key : map.keySet()) {
      assert key.equals(key.toLowerCase(Locale.ROOT));
    }
    return true;
  }

  private static int parseInt(String str, Map<String, Integer> nameMap) throws NumberFormatException {
    assert assertAllKeysLowerCase(nameMap);
    Integer namedValue = nameMap.get(str.toLowerCase(Locale.ROOT));
    return (namedValue != null) ? namedValue : Integer.parseInt(str);
  }

  /**
   * Parses a cron value, supporting lists, asterisk, and ranges, and steps.
   */
  public static Matcher parseMatcher(String str, int minimum, int maximum, int modulus, Map<String, Integer> nameMap) throws IllegalArgumentException {
    // Handle list
    if (str.indexOf(',') != -1) {
      Collection<Matcher> list = new ArrayList<>();
      StringTokenizer st = new StringTokenizer(str, ",");
      while (st.hasMoreTokens()) {
        list.add(parseMatcher(st.nextToken(), minimum, maximum, modulus, nameMap));
      }
      return new List(list);
    }

    // Handle step
    int pos = str.indexOf('/');
    if (pos != -1) {
      return new Step(
          parseMatcher(
              str.substring(0, pos),
              minimum,
              maximum,
              modulus,
              nameMap
          ),
          Integer.parseInt(str.substring(pos + 1))
      );
    }

    // Handle wildcard
    if ("*".equals(str)) {
      return new Asterisk(minimum);
    }

    // Handle ranges
    pos = str.indexOf('-');
    if (pos != -1) {
      int begin = parseInt(str.substring(0, pos), nameMap);
      if (begin < minimum || begin > maximum) {
        throw new IllegalArgumentException();
      }
      int end = parseInt(str.substring(pos + 1), nameMap);
      if (end < minimum || end > maximum) {
        throw new IllegalArgumentException();
      }
      return new Range(begin % modulus, end % modulus);
    }

    // Single value
    int value = parseInt(str, nameMap);
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException();
    }
    return new Value(value % modulus);
  }

  /**
   * Matches any of a list.
   */
  public static class List extends Matcher {

    private final Matcher[] list;

    /**
     * Creates a new {@link List}.
     */
    public List(Collection<Matcher> list) {
      this.list = list.toArray(new Matcher[list.size()]);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < list.length; i++) {
        if (i > 0) {
          sb.append(',');
        }
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
      for (Matcher matcher : list) {
        if (matcher.matches(value)) {
          return true;
        }
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

    /**
     * Creates a new {@link Step}.
     */
    public Step(Matcher matcher, int step) {
      this.matcher = matcher;
      this.step = step;
    }

    @Override
    public String toString() {
      return matcher + "/" + step;
    }

    @Override
    int getStepOffset() {
      throw new AssertionError(); // Does not apply to step matcher, only the specific matcher
    }

    @Override
    public boolean matches(int value) {
      return
          matcher.matches(value)
              && ((value - matcher.getStepOffset()) % step) == 0;
    }
  }

  /**
   * Matches any value.
   */
  public static class Asterisk extends Matcher {

    private final int stepOffset;

    /**
     * Creates a new {@link Asterisk}.
     */
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

    /**
     * Creates a new {@link Range}.
     */
    public Range(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }

    @Override
    public String toString() {
      return begin + "-" + end;
    }

    @Override
    int getStepOffset() {
      return begin;
    }

    @Override
    public boolean matches(int value) {
      if (begin <= end) {
        // No wrap-around
        return value >= begin && value <= end;
      } else {
        // Wrap-around
        return value >= begin || value <= end;
      }
    }
  }

  /**
   * Matches a single value.
   */
  public static class Value extends Matcher {

    private final int value;

    /**
     * Creates a new {@link Value}.
     */
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
