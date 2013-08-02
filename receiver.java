import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class receiver {

	private InetAddress hostAddress;
	private int emulatorPort;
	private static FileOutputStream fos, arrival;
	private static DatagramSocket receiverSocket;
	// data array used to store the UDP data of the received packets.
	private byte[] packetBuffer = new byte[1024];

	// the real SeqNum the receiver expected, should modulo 32 to get SeqNum
	private int nextSeqNum = 0;

	// 0/1 represents the file writing has been finished or not
	private int endOfFile = 0;

	public receiver(InetAddress addr, Integer port1, Integer port2, File file)
			throws IOException {

		hostAddress = addr;
		emulatorPort = port1;

	}

	// main method to receive and send packets
	private void start() throws Exception {
		while (endOfFile != 1) {

			// use Datagram Packet to receive packets from sender through the
			// Datagram Socket
			DatagramPacket receiverPacket = new DatagramPacket(packetBuffer,
					packetBuffer.length);
			try {
				receiverSocket.receive(receiverPacket);
			} catch (IOException e) {
				System.out.println("Receiver Socket I/O exception!!!");
			}

			packet p = packet.parseUDPdata(packetBuffer);

			System.out.println("Debug:Arrival " + p.getSeqNum());
			arrival.write((Integer.toString(p.getSeqNum()) + '\n').getBytes());

			// 3 types of packets
			switch (p.getType()) {
			case 0:
				// ACK packets here should be invalid
				System.out.println("Invalid packet type received!!!");
				break;
			case 1:
				// length of the data packets should be larger than 0
				if (p.getLength() <= 0) {
					System.out
							.println("Invalid packet length for data received!!!");
					break;
				} else {
					// if it's the expected packet just write to the file and
					// update expected SeqNum
					if (p.getSeqNum() == nextSeqNum) {
						writeToFile(p);
						System.out.print("Debug:ACKED   ");
						sendACK(nextSeqNum % 32);
						nextSeqNum++;
						break;
					} else {
						// if it's not just send the ACK packet with the SeqNum
						// of the packet before the expected one
						System.out.print("Debug:RESEND   ");

						if (nextSeqNum == 0)
							break;
						else
							sendACK(nextSeqNum % 32 - 1);
						break;
					}
				}
			case 2:
				// length of the EOT packets should be 0
				if (p.getLength() != 0) {
					System.out
							.println("Invalid packet length for EOT received!!!");
					break;
				} else {
					// if it's the expected packet means it's the end of file
					// send EOT to the sender and close the file output stream
					// update endOfFile to break the while loop
					if (p.getSeqNum() == nextSeqNum % 32) {
						endOfFile = 1;
						sendEOT(p.getSeqNum());
						fos.close();
						break;
					} else {
						// if it's not just send the ACK packet with the SeqNum
						// of the packet before the expected one
						if (nextSeqNum == 0)
							break;
						else
							sendACK(nextSeqNum % 32 - 1);
						break;
					}

				}
			}

		}
		// close the Datagram Socket
		receiverSocket.close();
	}

	// send EOT to the sender using Datagram Packet
	private void sendEOT(int seqNum) throws Exception {
		packet eot = packet.createEOT(seqNum);
		byte[] eotBuffer = new byte[1024];
		eotBuffer = eot.getUDPdata();
		DatagramPacket eotPacket = new DatagramPacket(eotBuffer,
				eotBuffer.length, hostAddress, emulatorPort);
		receiverSocket.send(eotPacket);
		System.out.println("Debug:Send EOT " + seqNum);
	}

	// send the ACKs to the sender using Datagram Packet
	private void sendACK(int seqNum) throws Exception {
		packet ack = packet.createACK(seqNum);
		byte[] ackBuffer = new byte[1024];
		ackBuffer = ack.getUDPdata();
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer,
				ackBuffer.length, hostAddress, emulatorPort);
		receiverSocket.send(ackPacket);
		System.out.println("Debug:Send ACK " + seqNum);
	}

	// use output stream to write packets' data to the specified file
	private void writeToFile(packet p) {
		try {
			System.out.println("Debug:Write to file " + p.getSeqNum());
			fos.write(p.getData());
		} catch (IOException e) {
			System.out.println("I/O exception while writing to file!!!");
		}
	}

	public static void main(String args[]) throws IOException, Exception {

		// arguments checking
		if (args.length != 4) {
			System.out.println("Please enter valid arguments!!!");
			System.exit(1);
		}

		InetAddress hostAddress = InetAddress.getByName(args[0]);
		Integer emulatorPort = Integer.parseInt(args[1]);
		Integer receiverPort = Integer.parseInt(args[2]);
		File writtenFile = new File(args[3]);

		// check the availability of the file to write
		if (!writtenFile.exists()) {
			System.out.println("Given file not exist! New file created!");
			writtenFile.createNewFile();
		}

		if (!writtenFile.canWrite()) {
			System.out.println("Given file not writable!");
			System.exit(3);
		}

		// receiver constructor
		receiver newReceiver = new receiver(hostAddress, emulatorPort,
				receiverPort, writtenFile);

		receiverSocket = new DatagramSocket(receiverPort);
		fos = new FileOutputStream(writtenFile);
		arrival = new FileOutputStream("arrival.log");

		// start to recieve
		newReceiver.start();
		arrival.close();

	}

}
