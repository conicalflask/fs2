package client.shareserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Creates a stream from a file that only contains the first n and last n bytes of a file.
 * If the length of the file is less than 2n then this behaves as a normal fileinputstream.
 * 
 * It depends on being able to skip a fileinputstream reliably!
 * 
 * @author gary
 *
 */
public class FileCropperStream extends FileInputStream {
	long cropAt; //the start of the section to be discarded.
	long cropLength; //the length of the discarded section.
	long cropPosition = 0; //The real position in the file.
	
	/**
	 * Create a new cropped file stream.
	 * @param inFile the file object.
	 * @param cropSize the size of the header and the footer to crop out.
	 * @throws FileNotFoundException
	 */
	public FileCropperStream(File inFile, long cropSize) throws FileNotFoundException{
		super(inFile);
		if (inFile.length() < cropSize*2) {
			cropAt = Long.MAX_VALUE;
			cropLength = 0;
		} else {
			cropAt = cropSize;
			cropLength = inFile.length()-cropSize*2;
		}
	}
	
	@Override
	public long skip(long n) throws IOException {
		long rb = byteRequest(n);
		long skipped;
		if (rb==n) {
			skipped = super.skip(n);
			cropPosition+=skipped;
		} else {
			//The crop is approaching, so do a skip-by parts:
		    skipped = super.skip(rb);
		    cropPosition+=skipped;
		    if (skipped==rb) {
		    	//successfull first skip:
		    	doCrop(); //Move past the cropped section
		    	rb = n-rb; //Still have stuff remaining to skip
		    	long skipped2 = super.skip(rb);
		    	skipped+=skipped2;
		    	cropPosition+=skipped2;
		    }
		}

		return skipped;
	}
	
	@Override
	public int read() throws IOException {
		//If we cant read a byte now then we need to do the crop:
		long rb = byteRequest(1);
		if (rb==0) {
			doCrop();
		}
		cropPosition++;
		return super.read();
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b,0,b.length);
	}
	
	@Override
	//Follows the same idiom as skip:
	public int read(byte[] b, int off, int len) throws IOException {
		int rb = (int)byteRequest(len);
		int bytesRead;
		if (rb==len) {
			bytesRead = super.read(b,off,rb);
			cropPosition+=bytesRead;
		} else {
			//The crop is approaching, so do a skip-by parts:
			bytesRead = super.read(b,0,rb);
		    cropPosition+=bytesRead;
		    if (bytesRead==rb) {
		    	//successfull first skip:
		    	doCrop(); //Move past the cropped section
		    	rb = len-rb; //Still have stuff remaining to skip
		    	int bytesRead2 = super.read(b,0,rb);
		    	bytesRead+=bytesRead2;
		    	cropPosition+=bytesRead2;
		    }
		}

		return bytesRead;
	}
	
	
	//On skipping/reading this calculates how much may be done safely.
	// (after the crop has been done this is just the requested amount)
	private long byteRequest(long request) {
		if (cropPosition+request > cropAt && cropLength > 0) {
			return cropAt-cropPosition;
		} else {
			return request;
		}
	}
	
	private void doCrop() throws IOException {
		while (cropLength > 0) cropLength-=super.skip(cropLength);
		cropPosition+=cropLength;
	}
}
