package client.shareserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import common.FS2Constants;
import common.ProgressTracker;
import common.Util;

/**
 * Provides java with the ability to produce digests of files but at a throttled rate.
 * Provide null for the bandwidth sharer and then it will not throttle the digestion.
 * 
 * It will also append the extra bytes to the end of the file bytes before digesting.
 * This can be used as an additional seed.
 * 
 * This is usefull if a lot of files require digesting and the computer should not be monopolised.
 * (IONice and Nice should probably be used in preference to a stringent throttle though)
 * @author gary
 *
 */
public class ThrottledFileDigester {

	public static String digest(InputStream inStream, BandwidthSharer bs, String algorithm, byte[] extra, ProgressTracker tracker) throws NoSuchAlgorithmException, IOException {
		DigestInputStream md5er = null;
		try {
			if (bs != null) {
				md5er = new DigestInputStream(new ThrottledInputStream(inStream,bs),MessageDigest.getInstance(algorithm));
			} else {
				md5er = new DigestInputStream(inStream,MessageDigest.getInstance(algorithm));
			}
			byte[] buf = new byte[FS2Constants.ARBITRARY_BUFFER_SIZE];
			int read=0;
			while ((read=md5er.read(buf)) > 0) {
				if (tracker!=null) tracker.progress(read);
			}
			String retStr = Util.bytesToHexString(md5er.getMessageDigest().digest(extra));
			return retStr;
		} finally {
			if (md5er != null) md5er.close();
		}
	}

	/**
	 * Generates and returns the fs2 digest of a file.
	 * @param inFile
	 * @param bs The bandwidth sharer to throttle the digesting. Use null for unlimited.
	 * @return the fs2 digest of the file as a string
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 * @throws NoSuchAlgorithmException 
	 */
	public static String fs2DigestFile(File file, BandwidthSharer bs) throws NoSuchAlgorithmException, IOException {
		return ThrottledFileDigester.digest(new FileCropperStream(file, FS2Constants.FILE_DIGEST_HEAD_FOOT_LENGTH), bs, FS2Constants.FILE_DIGEST_ALGORITHM, Long.toString(file.length()).getBytes("UTF-8"), null);
	}
	
	public static String fs2TrackableDigestFile(File file, ProgressTracker tracker) throws NoSuchAlgorithmException, IOException {
		return ThrottledFileDigester.digest(new FileCropperStream(file, FS2Constants.FILE_DIGEST_HEAD_FOOT_LENGTH), null, FS2Constants.FILE_DIGEST_ALGORITHM, Long.toString(file.length()).getBytes("UTF-8"), tracker);
	}
}
