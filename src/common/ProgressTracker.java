package common;

import java.util.Date;
import java.util.LinkedList;

import common.Util.Deferrable;

/**
 * A general pupose file-operation (or any operation who's progress and completion can be measured by a single number) progress tracker.
 * It can be used to calculate transer speeds, monitor progress of a file copy etc.
 * 
 * the defaults (sample lifetime and sample-clumping interval) are only sensible for file transfers order of 1000mbits/sec.
 * For slower transfers (such as people filling a hall) reduce the parameters accordingly.
 * 
 * Samples are clumped together and recorded only every 100ms by default at most.
 * Samples are only kept for 10 seconds at most, implying a maximum of 100 samples.
 * 
 * Trackers may also be formed into a heirachy with progress propagated to a tracker's parent.
 * 
 * @author gary
 */
public class ProgressTracker {

	private final class Sample {
		final long value;
		final long at;
		public Sample(long value, long when) {
			this.value = value;
			this.at = when;
		}
	}
	
	LinkedList<Sample> samples = new LinkedList<Sample>();
	long accumulator = 0; //the sum of all bytes transfered in the samples.
	
	long position = 0; //the current position in the transfer.
	volatile long expectedMaximum = 0; //the expected end of the file/transfer
	
	//Sensible defaults?:
	long sampleClumpInterval = 100; //milliseconds to clump samples.
	long maxSampleAge = 10*1000; //number of milliseconds to keep samples.
	
	ProgressTracker parent;
	
	public long getMaxSampleAge() {
		return maxSampleAge;
	}
	
	public void setMaxSampleAge(long maxSampleAge) {
		this.maxSampleAge = maxSampleAge;
	}
	
	public long getSampleClumpInterval() {
		return sampleClumpInterval;
	}
	
	/**
	 * ProgressTrackers may have parents that also recieve all the progress that this tracker recieves.
	 * In this way multiple simultanious chunks of a task may be aggregated into the whole task,
	 * likewise many tasks may be aggregated into a group/collection.
	 * @param parent
	 */
	public void setParent(ProgressTracker parent) {
		this.parent = parent;
	}
	
	public ProgressTracker getParent() {
		return parent;
	}
	
	/**
	 * Constructs and returns a new progress tracker that is a child of this one.
	 * @return
	 */
	public ProgressTracker getNewChild() {
		return new ProgressTracker(this);
	}
	
	/**
	 * Construct a progress tracker with no parent.
	 */
	public ProgressTracker() {
		//No-arg constructor is fine.
	}
	
	/**
	 * Constructs a new progress tracker with the specified parent.
	 * @param parent
	 */
	public ProgressTracker(ProgressTracker parent) {
		this.parent = parent;
	}
	
	/**
	 * Sets the minimum interval in milliseconds between contributions the samples list.
	 * Calls to progress() or progressTo() more quickly than this interval will be clumped together.
	 * @param sampleClumpInterval
	 */
	public void setSampleClumpInterval(long sampleClumpInterval) {
		this.sampleClumpInterval = sampleClumpInterval;
	}
	
	/**
	 * notify this tracker that some quantity of thing has occured.
	 * 
	 * @param amount the quantity of thing that has progressed.
	 */
	public synchronized void progress(long amount) {
		addSample(amount);
		if (parent!=null) parent.progress(amount);
	}
	
	/**
	 * Set the position of this task to the value of newPosition, the difference between the current and newPosision will count as progress.
	 * This is equivalent to: progress(newPosition-getPosition());
	 * @param newPosition
	 */
	public synchronized void progressTo(long newPosition) {
		progress(newPosition-position);
	}
	
	/**
	 * Gets the total amount of progress that has happened since the start
	 * @return
	 */
	public synchronized long getPosition() {
		return position;
	}
	
	/**
	 * Atomically returns the position of this tracker and zeros the position.
	 * @return
	 */
	public synchronized long getAndZeroPosition() {
		long ret = position;
		position = 0;
		return ret;
	}
	
	/**
	 * Sets the current absolute position towards completion.
	 * This does not count as task progress, so will not count as speed or change the estimated time to completion.
	 * See progressTo() to change the absolute position and count as speed.
	 * @param pos the new position of the task.
	 * @return
	 */
	public synchronized void setPosition(long pos) {
		this.position = pos;
	}
	
	/**
	 * Returns a running average number of units of progress occuring per second.
	 * 
	 * @return the number of units per second currently occuring.
	 */
	public synchronized double getSpeed() {
		addSample(0); //record that no units were added now, so that if this is polled when no progress is occuring it will decrease over time.
		
		long dur = getDuration();
		if (dur==0l) {
			return 0;  //speed is meaningless over an interval of zero time.
		} else {
			return (((double)accumulator)/(double)dur)*1000f;
		}
	}
	
	/**
	 * Gets the estimated time in seconds until the position is at least expectedMaximum.
	 * Returns zero if the position is already>=expectedMaximum
	 * 
	 * Returns MAX_VALUE if the speed is zero.
	 * 
	 * @return
	 */
	public synchronized long getEstimatedTimeRemaining() {
		if (position>=expectedMaximum) return 0;
		double speed = getSpeed();
		if (speed==0) {
			return Long.MAX_VALUE;
		} else {
			return (long) (((double)(expectedMaximum-position))/(speed)+0.5);
		}
	}
	
	/**
	 * Gets the estimated time of completion.
	 * @return
	 */
	public synchronized Date getETA() {
		return new Date(getEstimatedTimeRemaining()+System.currentTimeMillis());
	}
	
	/**
	 * Returns a string describing the time remaining. This very basic!
	 * 
	 * @return
	 */
	public synchronized String describeTimeRemaining() {
		return Util.describeInterval(getEstimatedTimeRemaining());
		
	}
	
	/**
	 * Returns either the expectedMaximum or the position, whichever is larger.
	 * @return
	 */
	public long getMaximum() {
		return (position>expectedMaximum ? position : expectedMaximum);
	}
	
	/**
	 * Describes the percent of this progress, or zero if there is no known expected maximum.
	 * This is never more than 100%
	 * 
	 * @return
	 */
	public synchronized float percentComplete() {
		if (expectedMaximum==0) return 0;
		return ((float)position/(float)getMaximum())*100;
	}
	
	/**
	 * Returns a string expressing the percentage this task is complete.
	 * @return
	 */
	public synchronized String percentCompleteString() {
		return Util.oneDecimalPlace(percentComplete())+"%";
	}
	
	/**
	 * Describes this object as it it were a file transfer, including an ETA if the expected maximum size is known.
	 * 
	 * If the expected maximum is known: 55.5MiB of 100MiB, 10m 2s remaining (55.5% at 10MiB/s)
	 * otherwise:						 55.5MiB of unknown at 10MiB/s
	 * 
	 */
	public synchronized String describe() {
		if (expectedMaximum==0) {
			return Util.niceSize(position)+" of unknown at "+Util.niceSize((long)(getSpeed()))+"/s";
		} else {
			String ret = Util.niceSize(position)+" of "+Util.niceSize(expectedMaximum)+", "+describeTimeRemaining()+" remaining ("+percentCompleteString()+" at "+Util.niceSize((long)(getSpeed()))+"/s)";
			return ret;
		}
	}
	
	/**
	 * Set the value that it is expected this progress will finish at.
	 * @param expectedMaximum
	 */
	public void setExpectedMaximum(long expectedMaximum) {
		this.expectedMaximum = expectedMaximum;
	}
	
	/**
	 * Sets the expectedMaximum of the task that this tracker follows to be larger by 'size' bytes.
	 * @param size
	 */
	public void expandTask(long size) {
		this.expectedMaximum+=size;
	}
	

	/**
	 * Returns the amount remaining to complete. This is maximum-progress or zero, whichever is larger.
	 * @return
	 */
	public long getRemaining() {
		return Math.max(0, expectedMaximum-position);
	}
	
	
	//Helpers:
	
	//Gets the duration between the oldest and newest sample in milliseconds.
	private long getDuration() {
		if (samples.isEmpty()) {
			return 0;
		} else {
			long ret = samples.getFirst().at - samples.getLast().at;
			return ret;
		}
	}
	
	private long clumpCount = 0;
	private void addSample(long amount) {
		clumpCount += amount;
		position += amount;
		Util.executeNeverFasterThan(sampleClumpInterval, adder);
	}

	private SampleAdder adder = new SampleAdder();
	private class SampleAdder implements Deferrable {
		@Override
		public void run() {
			long now = System.currentTimeMillis();
			accumulator += clumpCount;
			samples.addFirst(new Sample(clumpCount, now));
			clumpCount = 0;
			dropSamples();
		}
		
	}
	
	private void dropSamples() {
		//Remove elements from the samples if:
		//  the oldest is older than the maximum age.
		while (samples.getLast().at+maxSampleAge<System.currentTimeMillis()) {
			Sample s = samples.removeLast();
			accumulator -= s.value;
		}
	}
}
