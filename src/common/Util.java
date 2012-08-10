package common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;

import common.HttpUtil.SimpleDownloadProgress;

/**
 * All kinds of useful stuff that java should just have already.
 * @author gary
 *
 */
public abstract class Util {

	/**
	 * Describes an object that can filter items.
	 * @author gary
	 *
	 * @param <T> The type of object that can be filtered by this.
	 */
	public interface Filter<T> {
		/**
		 * Returns true if the item supplied passes through the filter.
		 * @param item
		 * @return
		 */
		boolean accept(T item);
	}
	
	/**
	 * Filters the collection supplied using a filter.
	 * @param collection The collection that will be filtered. It must support a modifyable iterator.
	 * @param filter The filter to use.
	 */
	public static <T> void filterList(Collection<T> collection, Filter<T> filter) throws UnsupportedOperationException {
		Iterator<T> itt = collection.iterator();
		while (itt.hasNext()) {
			T item = itt.next();
			if (!filter.accept(item)) itt.remove();
		}
	}
	
	/**
	 * Allows an interator to pretend to be an enumeration
	 * @author gary
	 *
	 */
	public static class EnumerationWrapper<T> implements Enumeration<T> {
		private Iterator<T> internal;
		public EnumerationWrapper(Iterable<T> input) {
			internal = input.iterator();
		}
		@Override
		public boolean hasMoreElements() {
			return internal.hasNext();
		}
		@Override
		public T nextElement() {
			return internal.next();
		}
	}
	
	/**
	 * Join the string representation of objects together with a delimiter between them.
	 * @param items The array of objects to join.
	 * @param delim The separator/delimiter.
	 * @return
	 */
	public static String join(Object[] items, String delim) {
	    if (items.length==0) return "";
	    StringBuffer buffer = new StringBuffer(items[0].toString());
	    int onItem = 1;
	    while (onItem != items.length) buffer.append(delim).append(items[onItem++].toString());
	    return buffer.toString();
	}
	
	/**
	 * Given a simple quoted string removes the quotes from the beginning and end, and unescapes the contents according to standard conventions:
	 * Examples:<br>
	 * "w00t\"" -> w00t"
	 * "w0\\\"t" -> w0\"t
	 * "\\" -> \
	 * @param toUnquote
	 * @return
	 */
	public static String unquote(String toUnquote) {
		if (toUnquote.length()<2) return "";
		return toUnquote.substring(1, toUnquote.length()-1).replaceAll("\\\\\"", Matcher.quoteReplacement("\"")).replaceAll("\\\\\\\\", Matcher.quoteReplacement("\\"));
	}
	
	public static String bytesToHexString(byte[] inBytes) {
		StringBuilder sb = new StringBuilder();
		
		char[] hexChars = "0123456789abcdef".toCharArray();
		for (byte b : inBytes) {
			sb.append(hexChars[(b & 0xF0)>>4]);
			sb.append(hexChars[(b & 0x0F)]);
		}
		
		return sb.toString();
	}
	
	public static void copyFile(File a, File b) throws IOException {
		InputStream sa = new BufferedInputStream(new FileInputStream(a));
		writeStreamToFile(sa, b);
	}
	
	/**
	 * Writes the given input stream to a file, then closes the file and stream.
	 * Uses a temporary file to avoid trashing an existing file if interrupted part-way through.
	 * 
	 * Always closes both the file and the stream.
	 * 
	 * @param is
	 * @param file
	 * @throws IOException 
	 */
	public static void writeStreamToFile(InputStream is, File saveAs) throws IOException {
		writeStreamToFile(is, saveAs, null);
	}
	
	public static void writeStreamToFile(InputStream is, File saveAs, SimpleDownloadProgress progress) throws IOException {
		File partial = new File(saveAs.getAbsoluteFile()+".working");
		OutputStream outs = new BufferedOutputStream(new FileOutputStream(partial));
		byte[] buf = new byte[FS2Constants.ARBITRARY_BUFFER_SIZE];
		long soFar = 0;
		int bytesRead = 0;
		try { 
			while ((bytesRead = is.read(buf)) > -1) {
				soFar+=bytesRead;
				outs.write(buf,0,bytesRead);
				if (progress!=null) progress.progress(soFar);
			}
		} finally {
			is.close();
			outs.close();
		}
		if (saveAs.exists()) saveAs.delete();
		if (!partial.renameTo(saveAs)) {
			throw new IOException("Partial file could not be saved.");
		}
	}
	
	public static final String[] sizeSuffix = {" B"," KiB"," MiB"," GiB"," TiB"," PiB"," EiB"};
	
	/**
	 * Returns a string describing the given size in bytes in nice human units.
	 * 
	 * One decimal place.
	 * 
	 * @param inSize the long representing the number of bytes.
	 * @return
	 */
	public static String niceSize(long inSize) {
		return niceSize(inSize, false);
	}
	
	public static String niceSize(long inSize, boolean concise) {
		double working = inSize;
		int order = 0;
		//Keep increasing the order until we run out of known orders of bytes.
		while (working > 1024 && order < (sizeSuffix.length-1)) {
			working /= 1024;
			order++;
		}
		return (order>=1 ? oneDecimalPlace(working) : (int)working)+(concise ? sizeOrders.substring(order, order+1).toUpperCase() : sizeSuffix[order]);
	}
	
	public static final Pattern sizeTokeniser = Pattern.compile("(\\d+\\.?\\d*)\\s*([bkmgtpe]?)i?[ob]?", Pattern.CASE_INSENSITIVE);
	public static final String sizeOrders = "bkmgtpe";
	
	/**
	 * Parses an expression of a filesize and returns a long representing the decimal value.
	 * Examples: 100kb, 50mo, 1b, 888, 10m, 5.5tb etc.
	 * 
	 * @return the size represented by nizeSize or -1 on failure.
	 */
	public static long parseNiceSize(String niceSize) {
		long ret = -1;
	
		Matcher m = sizeTokeniser.matcher(niceSize);
		if (m.matches()) {
			double sofar = Double.parseDouble(m.group(1));
			int order = sizeOrders.indexOf(m.group(2).toLowerCase());
			while (order>0) {
				order--;
				sofar*=1024;
			}
			ret=(long)sofar;
		}
		
		return ret;
	}
	
	public static String oneDecimalPlace(double val) {
		return Float.toString(((float)Math.round(val*10)/(float)10.0));
	}

	
	public static class FileSize implements Comparable<FileSize> {
		private final Long size;
		private final boolean speed;
		
		public synchronized long getSize() {
			return size;
		}

		public FileSize(long size) {
			this.size = size;
			this.speed = false;
		}
		
		/**
		 * Set speed to true and the toString() of this class with append a "/s"
		 * @param size
		 * @param speed
		 */
		public FileSize(long size, boolean speed) {
			this.size = size;
			this.speed = speed;
		}
		
		@Override
		public int compareTo(FileSize o) {
			return size.compareTo(o.size);
		}
		
		@Override
		public String toString() {
			return niceSize(size)+(speed ? "/s" : "");
		}
	}
	
	/**
	 * A class for printing magnitudes with Si-prefixed suffixes.
	 * 
	 * This importantly differs from NiceSize in that it uses Si orders of magnitude and does not say "b" for x10^0
	 * 
	 * @author gp
	 *
	 */
	public static class NiceMagnitude implements Comparable<NiceMagnitude> {

		private final Long magnitude;
		private final String suffix;
		
		public NiceMagnitude(Long magnitude, String suffix) {
			this.magnitude = magnitude;
			this.suffix = suffix;
		}
		
		@Override
		public String toString() {
			double working = magnitude;
			int order = 0;
			//Keep increasing the order until we run out of known orders of bytes.
			while (working > 1000 && order < (sizeSuffix.length-1)) {
				working /= 1000;
				order++;
			}
			return (order>=1 ? oneDecimalPlace(working) : (int)working)+ (order>=1 ? "_KMGTPE".substring(order, order+1) : "") + suffix;
		}
		
		@Override
		public int compareTo(NiceMagnitude o) {
			return magnitude.compareTo(o.magnitude);
		}
		
	}
	
	private static WeakHashMap<Deferrable, Long> executeNeverFasterTable = new WeakHashMap<Deferrable, Long>();
	/**
	 * Given a Runnable this will execute it now if it hasn't been executed by this method in the last minInterval milliseconds.
	 * The interval is measured between the end of one 
	 * 
	 * Obviously this method keeps track of all previous tasks invoked using it, but it uses weak references so there it does not cause a memory leak.
	 * 
	 * Also note that this is not a scheduler, if this function returns false the Runnable is neither executed now nor asynchronously in the future.
	 * 
	 * @param minInterval The minimum duration in milliseconds between invocations of the task supplied.
	 * @param task The runnable to execute not more often than specified. This MUST be the same object (by .equals) each time or it will be executed without regard to the time.
	 * @return true iff the task was executed now, false if the task was not executed.
	 */
	public static boolean executeNeverFasterThan(long minInterval, Deferrable task) {
		Long lastRun;
		synchronized (executeNeverFasterTable) {
			lastRun = executeNeverFasterTable.get(task);
		}
		if ((lastRun!=null && lastRun+minInterval<System.currentTimeMillis()) || lastRun==null) {
			synchronized (executeNeverFasterTable) {
				executeNeverFasterTable.put(task, System.currentTimeMillis());
			}
			task.run();
			return true;
		}
		return false;
	}
	
	private static Timer scheduleTimer = new Timer("Util scheduleExecuteNeverFasterThan timer", true);
	private static HashSet<Deferrable> scheduleItems = new HashSet<Deferrable>();
	
	/**
	 * Similar to executeNeverFasterThan but differs:
	 * 1) They are executed never when the method is called, but only ever after minInterval
	 * 1b) they are executed in another thread.
	 * 2) A task when submitted will certainly execute while the program is active:
	 * 2a) although not more often than min interval
	 * 2b) and is not guaranteed to execute if the program shuts down before the interval has elapsed.
	 * 
	 * Also all scheduled tasks are executed in the same (timer) thread, so they shouldn't take too long!
	 * @param minInterval
	 * @param task
	 */
	public static void scheduleExecuteNeverFasterThan(long minInterval, final Deferrable task) {
		synchronized (scheduleItems) {
			if (!scheduleItems.contains(task)) {
				scheduleItems.add(task);
				scheduleTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						task.run();
						synchronized (scheduleItems) {
							scheduleItems.remove(task);
						}
					}
				}, minInterval);
			}
		}
	}
	
	
	/**
	 * Used to represent tasks that can be deferred but does not imply they will be executed in another thread.
	 * @author gary
	 */
	public interface Deferrable {
		public void run();
	}
	

	private static final String[] timeOrders = { "s", "m", "h", "d", "w", "y", "c" };
	private static final int[] orderValues =   { 60, 60, 24, 7, 52, 100 };
	private static final int[] remainderRounds = { 5, 5, 1, 1, 1, 1 };
	
	/**
	 * Describes the interval supplied in simple english.
	 * @param seconds the interval in seconds
	 * @return
	 */
	public static String describeInterval(long seconds) {
		long tr = seconds;
		if (tr==Long.MAX_VALUE) return "forever";
		
		StringBuilder sb = new StringBuilder();
		int remainder = 0;
		int order = 0;
		while ((order+1<orderValues.length) && tr>orderValues[order]) {
			remainder = (int) (tr % orderValues[order]);
			tr /= orderValues[order];
			order++;
		}
		
		sb.append(tr);
		sb.append(timeOrders[order]);
		
		int roundRem;
		
		//woof:
		if (order>0 && (roundRem = roundTo(remainder, remainderRounds[order-1], true))>0) {
			sb.append(" ");
			sb.append(roundRem);
			sb.append(timeOrders[order-1]);
		}
		
		return sb.toString();
	}
	
	public static int roundTo(int value, float roundTo, boolean trunc) {
		return (int) ((Math.round( value+(!trunc ? roundTo/2f : 0f ) )/(int)roundTo)*(int)roundTo);
	}
	
	/**
	 * Checks that the file specified is contained by the container specified.
	 * --that all of the container's path is the leftmost section of the item's path.
	 * So, a file contains itself.
	 * and /home/gary/Desktop/test.jpg isWithin: /, /home, /home/gary, /home/gary/Desktop, and /home/gary/Desktop/test.jpg
	 */
	public static boolean isWithin(File item, File container) throws IOException {
		return (item.getCanonicalPath().indexOf(container.getCanonicalPath()) == 0);
	}
	
	/**
	 * Tests to determine if the file object given represents a valid filename for a potential file on this computer.
	 * It requires that the filesystem is read-write.
	 * @return
	 */
	public static boolean isValidFileName(File toTest) {
		if (toTest.exists()) return true;
		try {
			if (toTest.createNewFile()) {
				toTest.delete();
				return true;
			}
		} catch (IOException e) {
			Logger.warn(e);
			Logger.log(e);
			return false;
		}
		return false;
	}
	
	/**
	 * Returns the next line from the inputstream. This is all the bytes up until and including the next LF byte (0x0A or '\n')
	 * @param is An inputstream to read from. This is read byte-at-a-time so this should certainly be a buffered implementation!
	 * @return The line, as a byte array.
	 * @throws IOException if the underlying inputstream does.
	 */
	public static byte[] readLine(InputStream is) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		byte next;
		do {
			next = (byte) is.read();
			if (next>=0) {
				bos.write(next);
			} else {
				return bos.toByteArray();
			}
			
		} while (next!='\n');
		
		
		return bos.toByteArray();
	}
	
	/**
	 * Enables the TLS_DH_anon_WITH_AES_128_CBC_SHA cipher suite to be used in HttpsURLConnections.
	 * This is necessary because FS2 currently only uses non-authenticated crypto.
	 * 
	 * This is because the only reasonable way to do a decently secure, authenticated, standard crypto is with a PKI.
	 * Future FS2 protocols may use a PKI or clever auth scheme to prevent MITM attacks..
	 * @throws NoSuchAlgorithmException If crypto is unsupported on this system.
	 * @throws KeyManagementException 
	 */
	public static void enableDHanonCipherSuite() throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext dsslc = SSLContext.getInstance("TLS");
		dsslc.init(null, null, null);
		String[] defaults = dsslc.getServerSocketFactory().getDefaultCipherSuites();
		String[] plusone = Arrays.copyOf(defaults, defaults.length+1);
		plusone[defaults.length] = FS2Constants.DH_ANON_CIPHER_SUITE_USED;
		System.setProperty("https.cipherSuites", join(plusone,","));
	}
	
	/**
	 * Calculates the md5 of the string in the default encoding, returning the result as a hex string.
	 * @param input
	 * @return
	 */
	public static String md5(String input) {
		try {
			return Util.bytesToHexString(MessageDigest.getInstance("MD5").digest(input.getBytes()));
		} catch (NoSuchAlgorithmException e) {
			Logger.severe("Platform does not support MD5: "+e);
			Logger.log(e);
			return null;
		}
	}
	
	public static enum ImageResizeType {INNER, OUTER, NORATIO, NONE};
	
	/**
	 * Encodes a supplied image (in an inputstream) into a specified format and size.
	 * It can resize the image if the source is not equal to the destination.
	 * @param inImage The input stream containing any image format supported by default java.
	 * @param outFormat The format to serialise the image into. Can be any of: BMP,bmp,jpg,JPG,jpeg,wbmp,png,JPEG,PNG,WBMP,GIF,gif
	 * @param outWidth The intended height of the output.
	 * @param outHeight The intended height of the output.
	 * @param resizeMode The mode of resizing. Inner never crops the original but centers the original inside bounds of the out-dimensions, Outer will crop and will always fill all pixels of the output buffer, NORATIO will just resize to the new dimensions without ratio preservation and None will not resize.
	 * @return a buffer containing the new encoded image.
	 * @throws IOException 
	 */
	public static byte[] processImage(InputStream inImage, String outFormat, int outWidth, int outHeight, ImageResizeType resizeMode) throws IOException {
		BufferedImage im = processImageInternal(inImage, outWidth, outHeight, resizeMode);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(im, outFormat, os);
		return os.toByteArray();
	}
	
	public static BufferedImage processImageInternal(InputStream inImage, int outWidth, int outHeight, ImageResizeType resizeMode) throws IOException {
		BufferedImage im = ImageIO.read(inImage);
		
		if (resizeMode!=ImageResizeType.NONE && !(im.getWidth()==outWidth && im.getHeight()==outHeight)) {
			//image needs resizing:
			double useWidthRatio = (double)outWidth/(double)im.getWidth();
			double useHeightRatio = (double)outHeight/(double)im.getHeight();
			double chosenRatio = 0;
			if (resizeMode==ImageResizeType.INNER) {
				chosenRatio = Math.min(useWidthRatio, useHeightRatio);
			} else if (resizeMode==ImageResizeType.OUTER) {
				chosenRatio = Math.max(useWidthRatio, useHeightRatio);
			}
			
			int mWidth = 0; //modified (adjusted) output width and height.
			int mHeight = 0;
			int oX; //offset from edge in X
			int oY; //offset from edge in Y
			if (resizeMode==ImageResizeType.NORATIO) {
				mWidth = outWidth;
				mHeight = outHeight;
			} else {
				mWidth = (int) ((double)im.getWidth()*chosenRatio);
				mHeight = (int) ((double)im.getHeight()*chosenRatio);
			}
			oX = (outWidth-mWidth)/2;
			oY = (outHeight-mHeight)/2;
			
			BufferedImage out = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = out.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); //good quality interpolation
			
			graphics.setColor(new Color(0,0,0,255));
			graphics.fillRect(0,0,outWidth,outHeight); //fill with transparency.
			graphics.drawImage(im, oX, oY, mWidth, mHeight, null);
			
			im = out;
		}
		return im;
	}
 }
