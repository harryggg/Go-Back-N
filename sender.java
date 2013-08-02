import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;

public class sender {

	private static int base = 0;
	private static int nextSeqNum = 0;

	private final static int windowSize = 10;
	private final static int TIME_OUT = 3000;

	// use a list to cache the packets sent but unACKed
	private static List<packet> cache = new LinkedList<packet>();

	// lock used to keep mutual exclusive
	private static Object lock = new Object();

	private static FileOutputStream seqnum, ack;

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.out.println("Please enter valid arguments!!!");
			System.exit(1);
		}

		InetAddress hostAddress = InetAddress.getByName(args[0]);
		Integer emulatorPort = Integer.parseInt(args[1]);
		Integer senderPort = Integer.parseInt(args[2]);
		File transmitFile = new File(args[3]);

		// fileinputstream used to input the specified file
		FileInputStream fis = new FileInputStream(transmitFile);
		seqnum = new FileOutputStream("seqnum.log");
		ack = new FileOutputStream("ack.log");

		DatagramSocket senderSocket = new DatagramSocket(senderPort);

		// create and start the two threads to send and receive
		threadsCreating(hostAddress, emulatorPort, fis, senderSocket);

		// close the log files
		seqnum.close();
		ack.close();
	}

	private static void threadsCreating(final InetAddress addr, final int port,
			final FileInputStream fis, final DatagramSocket socket)
			throws InterruptedException {

		// thread used to send packets to senders
		Thread sendPacketsThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					sendPackets(fis, addr, port, socket);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// thread used to receive ACKs sent by receiver and process them
		Thread monitorACKsThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					monitorACKs(addr, port, socket);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// start the threads
		sendPacketsThread.start();
		monitorACKsThread.start();

		// wait for threads finish
		sendPacketsThread.join();
		monitorACKsThread.join();

		// close the sender socket
		socket.close();
	}

	private static void sendPackets(FileInputStream fis, InetAddress target,
			int port, DatagramSocket socket) throws Exception {

		try {

			packet p;
			byte[] buffer = new byte[500];

			while (true) {

				// if the window is full, just wait
				if (nextSeqNum >= base + windowSize)
					continue;

				// here should be mutex
				synchronized (lock) {
					
					// input file packet by packet
					int ret = fis.read(buffer);

					// if nothing left, just send a EOT packet
					if (ret < 0) {
						p = packet.createEOT(nextSeqNum);
						DatagramPacket senderPacket = new DatagramPacket(
								p.getUDPdata(), p.getUDPdata().length, target,
								port);
						socket.send(senderPacket);
						System.out.println("Debug:EOT " + p.getSeqNum());

						// add the sent packet to cache as unACKed
						cache.add(p);

						// write to log file
						seqnum.write((Integer.toString(nextSeqNum) + '\n')
								.getBytes());
						;

						// close fileinputstream
						fis.close();
						break;
					} else {
						// else just send data packets
						p = packet.createPacket(nextSeqNum, new String(buffer,
								0, ret));
						DatagramPacket senderPacket = new DatagramPacket(
								p.getUDPdata(), p.getUDPdata().length, target,
								port);
						socket.send(senderPacket);
						System.out.println("Debug:Send " + p.getSeqNum());

						// add the sent packet to cache as unACKed
						cache.add(p);

						// write to log file
						seqnum.write((Integer.toString(nextSeqNum) + '\n')
								.getBytes());

						// update nextSeqNum
						nextSeqNum++;

					}
				}
			}

		} catch (SocketException e) {
		} catch (IOException e) {
		}
	}

	private static void monitorACKs(InetAddress target, int port,
			DatagramSocket socket) throws Exception {

		while (true) {

			try {
				byte[] buffer = new byte[512];

				// set the socket to receive ACKs
				// set the timer on the socket
				socket.setSoTimeout(TIME_OUT);
				DatagramPacket ACKs = new DatagramPacket(buffer, buffer.length,
						target, port);
				socket.receive(ACKs);

				packet ACKPackets = packet.parseUDPdata(ACKs.getData());
				
				// if the packet is EOT just write to log file and exit
				if (ACKPackets.getType() == 2) {
					ack.write((Integer.toString(ACKPackets.getSeqNum()) + '\n')
							.getBytes());
					break;
				}

				synchronized (lock) {
					// difference between the seqNum of ACK and the base
					int diff = ACKPackets.getSeqNum() - (base % 32) + 1;
					if (diff > 0) { // ignore duplicate ACKs
						
						// write to log file
						ack.write((Integer.toString(ACKPackets.getSeqNum()) + '\n')
								.getBytes());

						base = base + diff; // update the base

						// remove the first diff elements in the cache
						for (int i = 0; i < diff; i++) {
							System.out.println("Debug:Remove "
									+ cache.get(i).getSeqNum());
							if (cache.isEmpty()) {
								break;
							}
							cache.remove(i);
						}
					}
				}
			} catch (SocketTimeoutException ex) {
				// while timeout resend all the packets in the cache
				//should be mutual exclusive
				synchronized (lock) {

					for (int i = 0; i < cache.size(); i++) {
						DatagramPacket dp = new DatagramPacket(cache.get(i)
								.getUDPdata(),
								cache.get(i).getUDPdata().length, target, port);
						socket.send(dp);
						
						//write to log file
						seqnum.write((Integer
								.toString(cache.get(i).getSeqNum()) + '\n')
								.getBytes());
						
						//For Debug
						 System.out.println("Debug:Timeout resend "
								+ cache.get(i).getSeqNum());

					}
				}
			}

		}
	}

}