package common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.logging.Level;

/**
 * Logger is logging class for FS2.  It supports output to text file, output to
 * a standard output (stdout/stderr), logging levels and logging on/off.
 * 
 * By default, logging is disabled, no log file is specified, the minimum logging
 * level is {@link Level.INFO} and the minimum file logging level is {@link Level.OFF}.
 * @author Andy Raines & Gary Plumbridge
 */
public abstract class Logger {
	private static boolean loggingEnabled = false;
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	private static File logFile;
	private static PrintStream logFileStream;
	private static Level minLoggingLevel = Level.INFO;
	private static Level minFileLoggingLevel = Level.FINE;
	private static HashSet<LogListener> registeredModules = new HashSet<LogListener>();
	private static Object lastMessage = "";
	private static Level lastLevel = Level.ALL;
	private static int suppressCount = 0;
	
	/**
	 * Sets the log file name to write log messages to.
	 * @param logFileName - the text file name to write log messages to
	 * @throws IOException if operation was unsuccessful
	 */
	public synchronized static void setLogFileName (String logFileName) {
		File file = new File(logFileName);
		if (file.exists()) {
			if (file.canWrite() == false) {
				Logger.warn("Log file "+logFileName
						+" exists, but cannot be written to.");
			} else {
				logFile = file;
			}
		} else {
			try {
				if (file.createNewFile()) {
					logFile = file;
				}
			} catch (IOException e) {
				Logger.warn("A fresh log file could not be created: "+e);
				e.printStackTrace();
			}
		}
	}
	
	public static void fine (Object message) {
		log(Level.FINE, message);
	}
	
	/**
	 * Logs the message.
	 * @param message The message to log. This will be toString()'d for most objects or for throwables the stacktrace will be used.
	 */
	public static void log (Object message) {
		log(Level.INFO, message);
	}
	
	public static void warn (Object message) {
		log(Level.WARNING, message);
	}
	
	public static void severe (Object message) {
		log(Level.SEVERE, message);
	}
	
	/**
	 * This function has been deprecated (it will be made private soon!)
	 * The shortcut functions are preferred!
	 * 
	 * Log a message.  If the logger is currently enabled, the message is
	 * appended to all the outputs where the given level is higher than
	 * that output's level threshold.
	 * @param level - One of the message level identifiers, e.g. SEVERE
	 * @param message - The string message. If this message is throwable then the stacktrace will be written instead.
	 */
	private synchronized static void log (Level level, Object message) {
		if (loggingEnabled) {
			
			if (level == lastLevel && message.equals(lastMessage)) {
				//Duplicate message, so supress it:
				
				if (suppressCount == 0) {
					//This is the first of the suppressed messages, so output a line:
					logOut(level, message+" (message is being repeated, suppressed)");
				}
				suppressCount+=1;
				
				return;
			} else {
				if (suppressCount > 1) {
					logOut(Level.INFO, "Last message repeated "+suppressCount+" times");
				}
				suppressCount = 0;
			}
			lastLevel = level;
			lastMessage = message;
			logOut(level, message);
		}
	}
	
	private static void logOut(Level level, Object message) {
		
		//If it's a throwable as a message, print the stack trace instead
		if (message instanceof Throwable) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(bos);
			((Throwable) message).printStackTrace(ps);
			ps.flush();
			message = bos.toString();
			ps.close();
		}
		
		if (level.intValue() >= minLoggingLevel.intValue()) {
			appendLog(level, message);
		}
		
		if (level.intValue() >= minFileLoggingLevel.intValue() && logFile!=null) {
			appendFileLog(level, message);
		}
		
		// Send log message to every registered log listener
		for (LogListener listener : registeredModules) {
			listener.messageLogged(level, message);
		}
	}
	
	private static void appendLog (Level level, Object logMessage) {
		PrintStream out = System.out;
		if (level.intValue()>=Level.SEVERE.intValue()) {
			out = System.err;	//Use STDERR if the message indicates something catastrophic.
		}
		out.println(dateFormat.format(new Date()) + " "
				+ level.getName() + ": " + logMessage);
	}
	
	private static void appendFileLog (Level level, Object logMessage) {
		try {
			if (logFileStream==null) {
				logFileStream = new PrintStream(new FileOutputStream(logFile, true),true);
			}

			logFileStream.println(dateFormat.format(new Date()) + " "
									+ level.getName() + ": " + logMessage);

		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public interface LogListener {
		void messageLogged (Level level, Object logMessage);
	}
	
	/**
	 * Adds a log listener which will be called every time the log is
	 * appended with an appropriate level.
	 * @param listener - the listener to be called
	 * @return true if the listener had not already been added
	 */
	public synchronized static boolean addLogListener (LogListener listener) {
		return registeredModules.add(listener);
	}
	
	public synchronized static boolean isLoggingEnabled() {
		return loggingEnabled;
	}
	public static File getLogFile() {
		return logFile;
	}
	public static Level getMinLoggingLevel() {
		return minLoggingLevel;
	}
	public static Level getMinFileLoggingLevel() {
		return minFileLoggingLevel;
	}
	
	public static void setLoggingEnabled(boolean loggingEnabled) {
		Logger.loggingEnabled = loggingEnabled;
	}
	public static void setMinLoggingLevel(Level minLoggingLevel) {
		Logger.minLoggingLevel = minLoggingLevel;
	}
	public static void setMinFileLoggingLevel(Level minFileLoggingLevel) {
		Logger.minFileLoggingLevel = minFileLoggingLevel;
	}
	
	public synchronized static void shutdown() {
		if (logFileStream!=null) {
			logFileStream.close();
			logFile=null;
		}
	}
}
