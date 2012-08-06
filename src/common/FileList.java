package common;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The new FS2 filelist format. Now without XML... ooOOOoo.
 * This should not be modified lightly!
 * 
 * The XML equivalent of this has stood the test of time, so should this.
 * 
 * This class should not be directly serialised as it will define its own stream format.
 * (to allow less developer flexibility between client and indexnode)
 * 
 * The name of a filelist is defined by the name of the root item.
 * 
 * @author gary
 */
public class FileList implements Serializable {
	private static final long serialVersionUID = -1863607797744548924L;
	
	// Items below this line may never change. (except in emergencies)
	
	/**
	 * Represents a filesystem item. If the children field is non-null then it is a directory. Otherwise a file.
	 * Only the name and children field should be used for directories.
	 */
	public static class Item implements Serializable {
		private static final long serialVersionUID = 5527044711593501827L;
		public HashMap<String, Item> children;  //Maps child-names onto their representative item.
		public String name;
		public String hash;
		public int hashVersion;
		public long lastModified; //used for the last-refreshed date for the file-list.
		public long size; //The size of a file or recursive size of a directory.
		public long fileCount; //for directories the recursive count of files within. This is obviously 1 for files, as they are one file.
		
		public boolean isDirectory() {
			return children!=null;
		}
	}
	
	public int revision;
	public Item root;
	
	//Items below this line MUST NOT be non-transient!
	
	//Prevent direct instantiation, creates an empty file list.
	private FileList(String name) {
		root = new Item();
		root.name = name;
		root.children = new HashMap<String, Item>();
		revision = 0;
	} 
	
	public static FileList newFileList(String name) {
		return new FileList(name);
	}
	
	public String getName() {
		return root.name;
	}
	
	public long getLastRefreshed() {
		return root.lastModified;
	}
	
	public void setRefreshedNow() {
		root.lastModified = System.currentTimeMillis();
	}
	
	/**
	 * Takes an inputstream and constructs a new filelist.
	 * 
	 * DOES NOT close the stream after reading.
	 * 
	 * @param is The stream containing a filelist as packed by the 'deconstruct' method given here.
	 * @return The filelist if one could be contstructed from the stream, null otherwise.
	 */
	public static FileList reconstruct(InputStream is) {
		try {
			//Decompress the input stream, as when deconstructing a filelist they are compressed.
			is = new InflaterInputStream(is);
			ObjectInputStream ois = new ObjectInputStream(is);
			FileList out = (FileList) ois.readObject();
			return out;
		} catch (Exception e) {
			Logger.warn("Couldn't reconstruct filelist: "+e);
			//e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Deconstructs this filelist and dumps it onto the output stream in the format that it so chooses.
	 * @param os The output stream to recieve a compressed filelist.
	 * @throws IOException if the serialisation doesn't work for some reason.
	 */
	public void deconstruct(OutputStream os) throws IOException {
		
		//Compress the serialised object to make them more friendly. Filelists can be huge!
		DeflaterOutputStream dos = new DeflaterOutputStream(os, new Deflater(Deflater.BEST_COMPRESSION));
		
		ObjectOutputStream oos = new ObjectOutputStream(dos);
		oos.writeObject(this);
		oos.flush();
		dos.finish();
	}
	
}
