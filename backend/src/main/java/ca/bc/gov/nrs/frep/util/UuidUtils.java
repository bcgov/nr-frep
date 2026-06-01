package ca.bc.gov.nrs.frep.util;

import java.nio.ByteBuffer;
import java.util.UUID;

/*
 * Purpose of this class is to encase common utility processing for UUIDs.
 */
public class UuidUtils {

	/*
	 * Return a UUID representation for a provided byte array.
	 */
	public static UUID asUuid(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		long firstLong = bb.getLong();
		long secondLong = bb.getLong();
		return new UUID(firstLong, secondLong);
	}

	/*
	 * Return an array of bytes for a provided UUID.
	 */
	public static byte[] asBytes(UUID uuid) {
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(uuid.getMostSignificantBits());
		bb.putLong(uuid.getLeastSignificantBits());
		return bb.array();
	}

}
