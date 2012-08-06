package client.shareserver;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;




/**
 * the symmetric opposite of ThrottledOutputStream.
 * 
 * Very, very simple now that this will cheerfully short-read.
 * 
 * @author gary
 */
public class ThrottledInputStream extends BufferedInputStream {

	private BandwidthSharer group;
	
	public ThrottledInputStream(InputStream s, BandwidthSharer inSharer) {
		super(s);
		group = inSharer;
	}
	
	public int read() throws IOException {
		try {
			group.getBytes(1);
		} catch (InterruptedException e) {
			throw new IOException("Interrupted", e);
		}

		return super.read();
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}
	
	/**
	 * This is very likely to short read!
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		try {
			return super.read(b,off,(int) Math.min(group.getBytes(len), Integer.MAX_VALUE));
		} catch (InterruptedException e) {
			throw new IOException("Interrupted", e);
		} //avoid naughty overflows
	}
	
}
