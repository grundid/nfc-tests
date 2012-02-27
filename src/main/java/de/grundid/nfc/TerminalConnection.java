package de.grundid.nfc;

import java.io.IOException;
import java.util.logging.Logger;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;

import org.nfctools.llcp.InitTamaTargetReq;
import org.nfctools.llcp.LlcpConstants;
import org.nfctools.llcp.NfcUtils;
import org.nfctools.spi.asc.ApduReaderWriter;

public class TerminalConnection {

	private Logger log = Logger.getLogger(TerminalConnection.class.getName());

	private CardTerminal cardTerminal;

	public TerminalConnection(CardTerminal cardTerminal) {
		this.cardTerminal = cardTerminal;
	}

	public void connectViaStandardMode() throws IOException {
		while (!Thread.interrupted()) {
			log.info("Waiting...");
			try {
				if (cardTerminal.waitForCardPresent(500)) {
					Card card = null;
					try {
						card = cardTerminal.connect("*");
						byte[] historicalBytes = card.getATR().getHistoricalBytes();

						if (historicalBytes[9] == (byte)0xff && historicalBytes[10] == (byte)0x40) {
							handleP2pConnection(card);
						}
						else {
							handleMfCard(card);
						}

					}
					catch (Exception e) {
						e.printStackTrace();
					}
					finally {
						if (card != null) {
							card.disconnect(true);
						}
						try {
							while (cardTerminal.isCardPresent()) {
								try {
									Thread.sleep(500);
								}
								catch (InterruptedException e) {
									break;
								}
							}
							cardTerminal.waitForCardAbsent(1000);
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
			catch (CardException e) {
				throw new IOException(e);
			}
		}
	}

	public void connectViaTargetCommand() throws CardException {
		while (true) {
			Card card = cardTerminal.connect("direct");
			ApduReaderWriter apduReaderWriter = new ApduReaderWriter(card, false);
			try {
				log.info("Waiting...");
				connectAsTarget(apduReaderWriter);
			}
			catch (Exception e1) {
				e1.printStackTrace();
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e) {
					break;
				}
			}
			finally {
				card.disconnect(true);
			}
		}
	}

	private void connectAsTarget(ApduReaderWriter apduReaderWriter) throws IOException {

		byte[] outBuffer = tgInitTamaTarget(new InitTamaTargetReq(false, false, LlcpConstants.mifareParams,
				LlcpConstants.felicaParams, LlcpConstants.nfcId3t, LlcpConstants.generalBytes));

		apduReaderWriter.write(outBuffer, 0, 0);

		byte[] inBuffer = new byte[1024];
		int read = apduReaderWriter.read(inBuffer, 0, 0);

		System.out.println(NfcUtils.convertBinToASCII(inBuffer, 0, read));
	}

	private byte[] tgInitTamaTarget(InitTamaTargetReq request) {
		byte[] buffer = new byte[37 + 1 + 1 + (request.getGeneralBytes() == null ? 0 : request.getGeneralBytes().length)];

		buffer[0] = (byte)0xD4;
		buffer[1] = (byte)0x8C;
		buffer[2] = (byte)((request.isDepOnly() ? 0x02 : 0) | (request.isPassiveOnly() ? 0x01 : 0));
		int bufPos = 3;
		System.arraycopy(request.getMifareParams(), 0, buffer, bufPos, request.getMifareParams().length);
		bufPos += request.getMifareParams().length;
		System.arraycopy(request.getFelicaParams(), 0, buffer, bufPos, request.getFelicaParams().length);
		bufPos += request.getFelicaParams().length;
		System.arraycopy(request.getNfcId3t(), 0, buffer, bufPos, request.getNfcId3t().length);
		bufPos += request.getNfcId3t().length;
		if (request.getGeneralBytes() != null) {
			buffer[bufPos++] = (byte)request.getGeneralBytes().length;
			System.arraycopy(request.getGeneralBytes(), 0, buffer, bufPos, request.getGeneralBytes().length);
			bufPos += request.getGeneralBytes().length;
		}
		else
			buffer[bufPos++] = 0;
		buffer[bufPos] = 0; // No historical bytes
		return buffer;
	}

	private void handleMfCard(Card card) {
		log.info("Connected to a card");
	}

	private void handleP2pConnection(Card card) {
		log.info("Connected to a P2P device");
	}

	public static void main(String[] args) {

		try {
			TerminalConnection terminalConnection = null;
			CardTerminals cardTerminals = TerminalFactory.getDefault().terminals();
			for (CardTerminal cardTerminal : cardTerminals.list()) {
				if (cardTerminal.getName().contains("ACR122")) {
					terminalConnection = new TerminalConnection(cardTerminal);
				}
			}

			if (terminalConnection != null) {
				terminalConnection.connectViaTargetCommand();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
