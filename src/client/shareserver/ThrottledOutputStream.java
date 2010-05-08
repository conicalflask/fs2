package client.shareserver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;




public class ThrottledOutputStream extends BufferedOutputStream {
	
	private BandwidthSharer group;
	
	ThrottledOutputStream(OutputStream s, BandwidthSharer inSharer) {
		super(s);
		group = inSharer;
	}
	
	public void write(int b) throws IOException {
		//Wait for a byte to be available: (getBytes can't return less than 1)
		try {
			group.getBytes(1);
		} catch (InterruptedException e) {
			throw new IOException("Interrupted", e);
		}
		super.write(b);
	}
	
	public void write(byte[] b, int off, int len) throws IOException {
		int remaining = len;
		int offset = off;
		long allocation;
		while (remaining > 0) {
			try {
				allocation = group.getBytes(remaining);
			} catch (InterruptedException e) {
				throw new IOException("Interrupted", e);
			}
			super.write(b,offset,(int)allocation);
			offset += allocation;
			remaining -= allocation;
		}
	}
	
	public void write(byte[] b) throws IOException {
		write(b,0,b.length);
	}
}
