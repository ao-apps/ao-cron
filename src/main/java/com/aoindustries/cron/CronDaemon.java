/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2015, 2016, 2018, 2019  AO Industries, Inc.
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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run cron jobs based on their scheduling requirements.  Once per minute
 * it polls each cron job and runs it if it is currently scheduled.
 *
 * If this main thread is delayed for more than a minute, schedules will be missed.
 * Some possible causes include the process was stopped (kill -STOP) or the machine
 * was suspended/hibernated.
 *
 * TODO: Use a user-provided ExecutorService, have aoserv-daemon use same executor.
 *           Also have aoserv-client allow user-provided executor service, use same executor.
 *
 * @see  CronJob
 *
 * @author  AO Industries, Inc.
 */
public final class CronDaemon {

	/**
	 * The cron daemon errors will be reported here (not the individual cron jobs).
	 */
	private static volatile Logger logger = Logger.getLogger(CronDaemon.class.getName());

	/**
	 * Sets the logger for the cron daemon errors (not the individual cron jobs).
	 */
	public static void setLogger(Logger logger) {
		CronDaemon.logger = logger;
	}

	/**
	 * Once started, this thread will run forever.
	 */
	private static CronDaemon runningDaemon;

	private static final List<CronJob> cronJobs=new ArrayList<>();
	private static final List<Logger> loggers=new ArrayList<>();
	private static final List<CronDaemonThread> runningJobs=new ArrayList<>();

	/**
	 * Adds a <code>CronJob</code> to the list of jobs.  If the job is already
	 * in the list, it will not be added again.
	 */
	public static void addCronJob(CronJob newJob, Logger logger) {
		synchronized(cronJobs) {
			boolean found = false;
			for(CronJob cronJob : cronJobs) {
				if(cronJob==newJob) {
					found = true;
					break;
				}
			}
			if(!found) {
				cronJobs.add(newJob);
				loggers.add(logger);
				if(runningDaemon==null) {
					runningDaemon=new CronDaemon();
					runningDaemon.start();
				}
			}
		}
	}

	/**
	 * Removes a <code>CronJob</code> from the list of jobs.
	 */
	public static void removeCronJob(CronJob job) {
		synchronized(cronJobs) {
			for(int i=0, len=cronJobs.size(); i<len; i++) {
				if(cronJobs.get(i)==job) {
					cronJobs.remove(i);
					loggers.remove(i);
					break;
				}
			}
			if(runningDaemon!=null && cronJobs.isEmpty()) {
				runningDaemon.stop();
				runningDaemon = null;
			}
		}
	}

	private Thread thread;

	private CronDaemon() {
	}

	/**
	 * Starts this daemon if it is not already running.
	 */
	private void start() {
		assert Thread.holdsLock(cronJobs);
		if(thread==null) {
			thread = new Thread(
				new Runnable() {
					private static final long MAX_SLEEP_TIME = 60L * 1000;

					@Override
					public void run() {
						final GregorianCalendar gcal = new GregorianCalendar();
						int lastMinute = Integer.MIN_VALUE;
						int lastHour = Integer.MIN_VALUE;
						int lastDayOfMonth = Integer.MIN_VALUE;
						int lastMonth = Integer.MIN_VALUE;
						int lastDayOfWeek = Integer.MIN_VALUE;
						int lastYear = Integer.MIN_VALUE;
						while(true) {
							synchronized(cronJobs) {
								if(runningDaemon!=CronDaemon.this) break;
							}
							try {
								// Get the new minute
								gcal.setTimeInMillis(System.currentTimeMillis());
								int minute=gcal.get(Calendar.MINUTE);
								int hour=gcal.get(Calendar.HOUR_OF_DAY);
								int dayOfMonth=gcal.get(Calendar.DAY_OF_MONTH);
								int month=gcal.get(Calendar.MONTH);
								int dayOfWeek=gcal.get(Calendar.DAY_OF_WEEK);
								int year = gcal.get(Calendar.YEAR);

								long sleepTime;
								// If the minute hasn't changed, then system sleep is not very precise, sleep another second
								if(
									minute==lastMinute
									&& hour==lastHour
									&& dayOfMonth==lastDayOfMonth
									&& month==lastMonth
									&& dayOfWeek==lastDayOfWeek
									&& year==lastYear
								) {
									sleepTime = 1000;
								} else {
									synchronized(cronJobs) {
										for(int i=0, size=cronJobs.size(); i<size; i++) {
											CronJob job=cronJobs.get(i);
											try {
												if(job.getCronJobSchedule().isCronJobScheduled(minute, hour, dayOfMonth, month, dayOfWeek, year)) {
													runJob(job, loggers.get(i), minute, hour, dayOfMonth, month, dayOfWeek, year);
												}
											} catch(ThreadDeath TD) {
												throw TD;
											} catch(Throwable T) {
												loggers.get(i).log(Level.SEVERE, "cron_job.name="+job.getCronJobName(), T);
											}
										}
									}
									// Record last minute to avoid possible repeat on imprecise timers.
									lastMinute = minute;
									lastHour = hour;
									lastDayOfMonth = dayOfMonth;
									lastMonth = month;
									lastDayOfWeek = dayOfWeek;
									lastYear = year;
									// Find the time until the next minute starts.
									gcal.add(Calendar.MINUTE, 1);
									gcal.set(Calendar.SECOND, 0);
									gcal.set(Calendar.MILLISECOND, 0);
									sleepTime = gcal.getTimeInMillis() - System.currentTimeMillis();
								}
								if(sleepTime>0) {
									// Be careful of system time changes
									if(sleepTime > MAX_SLEEP_TIME) sleepTime = MAX_SLEEP_TIME;
									try {
										Thread.sleep(sleepTime);
									} catch(InterruptedException err) {
										logger.log(Level.WARNING, null, err);
									}
								}
							} catch(ThreadDeath TD) {
								throw TD;
							} catch(Throwable T) {
								logger.log(Level.SEVERE, null, T);
								try {
									Thread.sleep(30000);
								} catch(InterruptedException err) {
									logger.log(Level.WARNING, null, err);
								}
							}
						}
					}
				},
				CronDaemon.class.getName()
			);
			thread.setPriority(Thread.MAX_PRIORITY);
			thread.setDaemon(true);
			thread.start();
		}
	}

	private void stop() {
		assert Thread.holdsLock(cronJobs);
		if(thread!=null) {
			thread.interrupt();
			thread = null;
		}
	}

	static void threadDone(CronDaemonThread thread) {
		synchronized(cronJobs) {
			for(int c=0;c<runningJobs.size();c++) {
				if(runningJobs.get(c)==thread) {
					runningJobs.remove(c);
					return;
				}
			}
		}
		thread.logger.log(Level.WARNING, "cron_job.name="+thread.cronJob.getCronJobName(), new Throwable("Warning: thread not found on threadDone(CronDaemonThread)"));
	}

	/**
	 * Shared implementation of running a job, whether scheduled or immediate.
	 */
	private static void runJob(CronJob job, Logger logger, int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year) {
		assert Thread.holdsLock(cronJobs);
		try {
			CronJobScheduleMode scheduleMode=job.getCronJobScheduleMode();
			boolean run;
			if(scheduleMode==CronJobScheduleMode.SKIP) {
				// Skip if already running
				run = true;
				for(CronDaemonThread runningJob : runningJobs) {
					if(runningJob.cronJob==job) {
						run = false;
						break;
					}
				}
			} else if(scheduleMode==CronJobScheduleMode.CONCURRENT) {
				run = true;
			} else {
				throw new RuntimeException("Unknown value from CronJob.getCronJobScheduleMode: "+scheduleMode);
			}
			if(run) {
				CronDaemonThread thread=new CronDaemonThread(job, logger, minute, hour, dayOfMonth, month, dayOfWeek, year);
				thread.setDaemon(false);
				thread.setPriority(job.getCronJobThreadPriority());
				runningJobs.add(thread);
				thread.start();
			}
		} catch(ThreadDeath TD) {
			throw TD;
		} catch(Throwable T) {
			logger.log(Level.SEVERE, "cron_job.name="+job.getCronJobName(), T);
		}
	}

	/**
	 * Runs a CronJob immediately if it is not already running or
	 * allows concurrent execution.
	 *
	 * @exception  IllegalStateException  If the job has not been added.
	 */
	public static void runImmediately(CronJob job) throws IllegalStateException {
		GregorianCalendar gcal = new GregorianCalendar();
		int minute = gcal.get(Calendar.MINUTE);
		int hour = gcal.get(Calendar.HOUR_OF_DAY);
		int dayOfMonth = gcal.get(Calendar.DAY_OF_MONTH);
		int month = gcal.get(Calendar.MONTH);
		int dayOfWeek = gcal.get(Calendar.DAY_OF_WEEK);
		int year = gcal.get(Calendar.YEAR);
		synchronized(cronJobs) {
			for(int i=0, size=cronJobs.size(); i<size; i++) {
				if(job==cronJobs.get(i)) {
					runJob(job, loggers.get(i), minute, hour, dayOfMonth, month, dayOfWeek, year);
					return;
				}
			}
		}
		throw new IllegalStateException("CronJob has not been added.");
	}
}
