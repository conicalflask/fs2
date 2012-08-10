package client.gui;

import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import client.ClientExecutor;

import common.Logger;

public class Utilities {
	
	HashMap<String, ImageIcon> cachedImages = new HashMap<String, ImageIcon>();
	
	/**
	 * Gets an {@link Image} of an icon, preferably from the filesystem, but if it doesn't exist then this
	 * tries to locate it within this jar file.
	 * @param iconName
	 * @return the image for the icon, or null if it couldn't be loaded.
	 */
	public ImageIcon getImage(String iconName) {
		return getImageFullname(iconName+".png");
	}
	
	public ImageIcon getImageFullname(String filename) {
		try {
			if (cachedImages.containsKey(filename)) {
				return cachedImages.get(filename);
			} else {
				File fsFile = new File("icons"+File.separator+filename);
				if (fsFile.exists()) {
					return new ImageIcon(Toolkit.getDefaultToolkit().createImage(fsFile.getPath()));
				} else {
					return new ImageIcon(Toolkit.getDefaultToolkit().createImage(Gui.class.getClassLoader().getResource("icons/"+filename)));
				}
			}
		} catch (Exception e) {
			Logger.warn("Image '"+filename+"' couldn't be loaded: "+e);
			return null; //The caller should handle this well.
		}
	}
	
	/**
	 * This gets a buffered image from the jar or filesystem.
	 * This does not cache so call infrequently!
	 * @param filename
	 * @return
	 */
	public BufferedImage getBufferedImageFullname(String filename) {
		try {
			File fsFile = new File("icons"+File.separator+filename);
			InputStream is = null;
			try {
				if (fsFile.exists()) {
					is = new FileInputStream(fsFile);
				} else {
					is = Gui.class.getClassLoader().getResource("icons/"+filename).openStream();
				}
				return ImageIO.read(is);
			} finally {
				if (is!=null) is.close();
			}
		} catch (Exception e) {
			Logger.warn("Image '"+filename+"' couldn't be loaded: "+e);
			return null; //The caller should handle this well.
		}
	}
	
	public enum FileType {UNKNOWN, APPLICATION, AUDIO, VIDEO, IMAGE, PDF, HTML, JAVA, CODE, TEXT, DOCUMENT, ARCHIVE, DISKIMAGE, TORRENT, CAKE};
	
	/**
	 * This will attempt to guess the mime type of a file based on its extention.
	 * This does not know many extensions!
	 * @param filename
	 * @return The type of the file (in FS2 types)
	 */
	public FileType guessType(String filename) {
		if (filename!=null) {
			String[] items = filename.split("\\.");
			if (items.length>1) {
				String ext = items[items.length-1].toLowerCase();
				if (typeMap.containsKey(ext)) {
					return typeMap.get(ext);
				}
			}
		}
		return FileType.UNKNOWN;
	}
	
	HashMap<String, FileType> typeMap = new HashMap<String, FileType>();
	
	public Utilities() {
		//Applications
		typeMap.put("exe", FileType.APPLICATION);
		typeMap.put("app", FileType.APPLICATION);
		
		//Audio
		typeMap.put("wav", FileType.AUDIO);
		typeMap.put("mp3", FileType.AUDIO);
		typeMap.put("wma", FileType.AUDIO);
		typeMap.put("m4a", FileType.AUDIO);
		typeMap.put("aac", FileType.AUDIO);
		typeMap.put("caf", FileType.AUDIO);
		typeMap.put("aif", FileType.AUDIO);
		
		//Video
		typeMap.put("avi", FileType.VIDEO);
		typeMap.put("mpg", FileType.VIDEO);
		typeMap.put("mpeg", FileType.VIDEO);
		typeMap.put("dvi", FileType.VIDEO);
		typeMap.put("divx", FileType.VIDEO);
		typeMap.put("mp4", FileType.VIDEO);
		typeMap.put("xvid", FileType.VIDEO);
		typeMap.put("wmv", FileType.VIDEO);
		typeMap.put("mkv", FileType.VIDEO);
		typeMap.put("asx", FileType.VIDEO);
		typeMap.put("asf", FileType.VIDEO);
		typeMap.put("3gp", FileType.VIDEO);
		typeMap.put("mjpg", FileType.VIDEO);
		typeMap.put("m4v", FileType.VIDEO);
		
		
		//Image
		typeMap.put("bmp", FileType.IMAGE);
		typeMap.put("jpg", FileType.IMAGE);
		typeMap.put("jpeg", FileType.IMAGE);
		typeMap.put("png", FileType.IMAGE);
		typeMap.put("gif", FileType.IMAGE);
		typeMap.put("raw", FileType.IMAGE);
		typeMap.put("dng", FileType.IMAGE);
		typeMap.put("svg", FileType.IMAGE);
		
		
		//pdf
		typeMap.put("pdf", FileType.PDF);
		
		//HTML
		typeMap.put("htm", FileType.HTML);
		typeMap.put("html", FileType.HTML);
		typeMap.put("xml", FileType.HTML);
		typeMap.put("xhtml", FileType.HTML);
		
		//Java
		typeMap.put("java", FileType.JAVA);
		typeMap.put("jar", FileType.JAVA);
		typeMap.put("class", FileType.JAVA);
		
		//Code
		typeMap.put("c", FileType.CODE);
		typeMap.put("cpp", FileType.CODE);
		typeMap.put("h", FileType.CODE);
		typeMap.put("rb", FileType.CODE);
		typeMap.put("py", FileType.CODE);
		typeMap.put("cpp", FileType.CODE);
		typeMap.put("pas", FileType.CODE);
		typeMap.put("sh", FileType.CODE);
		typeMap.put("pl", FileType.CODE);
		typeMap.put("asp", FileType.CODE);
		typeMap.put("php", FileType.CODE);
		typeMap.put("vb", FileType.CODE);

		//Text
		typeMap.put("txt", FileType.TEXT);
		typeMap.put("text", FileType.TEXT);
		
		//Document
		typeMap.put("doc", FileType.DOCUMENT);
		typeMap.put("odf", FileType.DOCUMENT);
		typeMap.put("xls", FileType.DOCUMENT);
		typeMap.put("dot", FileType.DOCUMENT);
		typeMap.put("xlt", FileType.DOCUMENT);
		typeMap.put("sda", FileType.DOCUMENT);
		typeMap.put("sdb", FileType.DOCUMENT);
		typeMap.put("sdc", FileType.DOCUMENT);
		typeMap.put("sdd", FileType.DOCUMENT);
		typeMap.put("sdw", FileType.DOCUMENT);
		typeMap.put("rtf", FileType.DOCUMENT);
		
		//Archive
		typeMap.put("zip", FileType.ARCHIVE);
		typeMap.put("rar", FileType.ARCHIVE);
		typeMap.put("tar", FileType.ARCHIVE);
		typeMap.put("gz", FileType.ARCHIVE);
		typeMap.put("bz2", FileType.ARCHIVE);
		typeMap.put("tbz", FileType.ARCHIVE);
		typeMap.put("bz", FileType.ARCHIVE);
		typeMap.put("tgz", FileType.ARCHIVE);

		//Disk images
		typeMap.put("iso", FileType.DISKIMAGE);
		typeMap.put("mdf", FileType.DISKIMAGE);
		typeMap.put("mds", FileType.DISKIMAGE);
		typeMap.put("cue", FileType.DISKIMAGE);
		typeMap.put("bin", FileType.DISKIMAGE);
		typeMap.put("dmg", FileType.DISKIMAGE);
		typeMap.put("img", FileType.DISKIMAGE);
		typeMap.put("nrg", FileType.DISKIMAGE);

		//Torrent
		typeMap.put("torrent", FileType.TORRENT);
		
		//Cake
		typeMap.put("gcf", FileType.CAKE);
	}
	
	public ImageIcon getIconForType(FileType type) {
		return getImage("type-"+type.toString().toLowerCase());
	}
	
	/**
	 * Executes the given task in the swing thread or in the current thread if this is a headless instance.
	 * 
	 * This should only be used for event notification for gui related items, as it is not guaranteed that dispatched items will ever be called!
	 * (they may not be during a shutdown process)
	 * 
	 * Calls to this function will be ignored if an externally triggered shutdown is in progress (shutdown triggered by shutdown hook)
	 * So, don't dispatch important things, it's only intended to be used to update gui elements.
	 * Additionally, while calls may be dispatched onto the AWT queue before a shutdown happens they will only be executed if there is NOT a shutdown of any type in progress.
	 * 
	 * 
	 * @param run
	 * @throws InvocationTargetException 
	 * @throws InterruptedException 
	 */
	public static void dispatch(final Runnable run, boolean synchronous) throws InterruptedException, InvocationTargetException {
		if (System.getProperty("headless")==null) {
			if (!(ClientExecutor.endGame && ClientExecutor.shuttingDown)) {
				Runnable safeRun = new Runnable() {
					@Override
					public void run() {
						if (!ClientExecutor.shuttingDown) run.run();
					}
				};
				
				if (EventQueue.isDispatchThread()) {
					safeRun.run();
				} else {
					if (synchronous) SwingUtilities.invokeAndWait(safeRun); else SwingUtilities.invokeLater(safeRun);
				}
			}
		} else {
			run.run();
		}
	}
	
	/**
	 * Executes the given task in the swing thread or in the current thread if this is a headless instance.
	 * 
	 * This should only be used for event notification for gui related items, as it is not guaranteed that dispatched items will ever be called!
	 * (they may not be during a shutdown process)
	 * 
	 * Calls to this function will be ignored if an externally triggered shutdown is in progress (shutdown triggered by shutdown hook)
	 * So, don't dispatch important things, it's only intended to be used to update gui elements.
	 * Additionally, while calls may be dispatched onto the AWT queue before a shutdown happens they will only be executed if there is NOT a shutdown of any type in progress.
	 * 
	 * 
	 * @param run
	 * @throws InvocationTargetException 
	 * @throws InterruptedException 
	 */
	public static void dispatch(Runnable run) throws InterruptedException, InvocationTargetException {
		dispatch(run, true);
	}
	
	/**
	 * An easier dispatch as it does not need error handling. This is helpful when exceptions are entirely unexpected from the dispatch.
	 * It contains a default exception handler of logging the exception and printing the stack trace.
	 * @param run
	 */
	public static void edispatch(Runnable run) {
		try {
			dispatch(run);
		} catch (Exception e) {
			Logger.warn("During dispatch to swing: "+e);
			Logger.log(e);
		}
	}
}
