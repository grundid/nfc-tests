package org.nfctools.spi.asc;

import java.io.IOException;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import org.nfctools.llcp.NfcUtils;

public class ApduReaderWriter {

	public static final int FILE_DEVICE_SMARTCARD = 0x310000;
	public static final int IOCTL_SMARTCARD_ACR122_ESCAPE_COMMAND = FILE_DEVICE_SMARTCARD + 3500 * 4;

	private Card card;
	private CardChannel cardChannel;
	private byte[] responseData;

	public ApduReaderWriter(Card card, boolean useBasicChannel) {
		this.card = card;
		if (useBasicChannel)
			cardChannel = card.getBasicChannel();
	}

	public void write(byte[] data, int offset, int length) throws IOException {
		try {
			CommandAPDU commandAPDU = new CommandAPDU(0xff, 0, 0, 0, data, offset, length);
			byte[] commandBytes = commandAPDU.getBytes();

			ResponseAPDU responseAPDU = null;
			if (cardChannel != null) {
				responseAPDU = cardChannel.transmit(commandAPDU);
			}
			else {
				byte[] transmitControlResponse = card.transmitControlCommand(IOCTL_SMARTCARD_ACR122_ESCAPE_COMMAND,
						commandBytes);
				responseAPDU = new ResponseAPDU(transmitControlResponse);
			}

			responseData = responseAPDU.getData();

			if (!isSuccess(responseAPDU))
				throw new IOException("Error sending message [" + NfcUtils.convertBinToASCII(data) + "] (" + offset
						+ "," + length + ") => [" + NfcUtils.convertBinToASCII(responseData) + "] SW1: "
						+ responseAPDU.getSW1() + " SW2: " + responseAPDU.getSW2());
		}
		catch (CardException e) {
			throw new IOException(e);
		}
	}

	public static boolean isSuccess(ResponseAPDU responseAPDU) {
		return responseAPDU.getSW1() == 0x90 && responseAPDU.getSW2() == 0x00;
	}

	public int read(byte[] data, int offset, int length) throws IOException {
		if (responseData.length > length - offset)
			throw new IllegalArgumentException("buffer too small for response, needed " + responseData.length
					+ " bytes");

		System.arraycopy(responseData, 0, data, offset, responseData.length);
		return responseData.length;
	}

}
