Compile application:
After extract the files, use "make" command to compile the application.

Run application:
nEmulator: ./nEmulator-linux386
Example:
./nEmulator-linux386 9991 129.97.167.68 9994 9993 129.97.167.69 9986 1 0.2 1

Receiver: java receiver <host address> <port number to send ACK> <port number to receive data> <name of file to receive into>
	

Sender:   java sender <host address> <port number to send file> <port number to receiver ACK> <name of file to send>
	

The program is built on MAC OS 10.8.2 machine with software Eclipse Juno SP2.

The test environment is :
nEmulator on linux032:
./nEmulator-linux386 2221 129.97.167.54 2224 2223 129.97.167.51 2222 1 0.2 0
Receiver on linux024:
java receiver 129.97.167.53 2223 2224 output.txt
Sender on linux008:
java sender 129.97.167.53 2221 2222 <file you want to send>


version of make:
	GNU Make 3.81
version of compiler:
	javac 1.6.0_20


	
 
