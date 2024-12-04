import java.lang.*;
import java.net.*;
import java.io.*;

public class wclient {

    public static void main(String[] args) {
        int srcport;
        int destport = wumppkt.SERVERPORT;
        String filename = "vanilla";
        String desthost = "ulam.cs.luc.edu";
        int winsize = 1;
        short THEPROTO = wumppkt.HUMPPROTO;
        wumppkt.setproto(THEPROTO);

        if (args.length > 0)
            filename = args[0];
        if (args.length > 1)
            winsize = Integer.parseInt(args[1]);
        if (args.length > 2)
            desthost = args[2];

        DatagramSocket s = null;
        boolean fileTransferComplete = false; // Flag to track transfer status

        try {
            s = new DatagramSocket();
            s.setSoTimeout(wumppkt.INITTIMEOUT); // Set socket timeout

            // DNS lookup
            InetAddress dest;
            System.err.print("Looking up address of " + desthost + "... ");
            try {
                dest = InetAddress.getByName(desthost);
            } catch (UnknownHostException uhe) {
                System.err.println("Unknown host: " + desthost);
                return;
            }
            System.err.println("Got it!");

            // Build and send REQ
            wumppkt.REQ req = new wumppkt.REQ(winsize, filename);
            DatagramPacket reqDG = new DatagramPacket(req.write(), req.size(), dest, destport);
            try {
                s.send(reqDG);
            } catch (IOException ioe) {
                System.err.println("Send() failed");
                return;
            }

            DatagramPacket replyDG = new DatagramPacket(new byte[wumppkt.MAXSIZE], wumppkt.MAXSIZE);
            DatagramPacket ackDG = new DatagramPacket(new byte[0], 0);
            ackDG.setAddress(dest);
            ackDG.setPort(destport);

            int expected_block = 1;
            long starttime = System.currentTimeMillis();
            wumppkt.DATA data = null;
            wumppkt.ERROR error = null;
            wumppkt.ACK ack = null;

            int proto, opcode, length, blocknum;

            // ====== HANDLE HANDOFF AND SEND ACK[0] ======
            // ====== HANDLE HANDOFF AND SEND ACK[0] ======
            boolean handoffReceived = false;
            for (int retries = 0; retries < 5; retries++) { // Retry up to 5 times
                try {
                    s.receive(replyDG);
                    handoffReceived = true;
                    break; // Exit loop on successful receipt
                } catch (SocketTimeoutException ste) {
                    System.err.println("Timeout waiting for HANDOFF. Retrying...");
                    try {
                        s.send(reqDG); // Resend REQ
                    } catch (IOException ioe) {
                        System.err.println("Failed to retransmit REQ.");
                        return;
                    }
                } catch (IOException ioe) {
                    System.err.println("Receive() failed while waiting for HANDOFF.");
                    return;
                }
            }

            if (!handoffReceived) {
                System.err.println("Hard timeout waiting for HANDOFF");
                return;
            }

            byte[] replybuf = replyDG.getData();
            proto = wumppkt.proto(replybuf);
            opcode = wumppkt.opcode(replybuf);
            length = replyDG.getLength();
            srcport = replyDG.getPort();

            if (proto != THEPROTO || opcode != wumppkt.HANDOFFop || srcport != destport) {
                System.err.println("Invalid HANDOFF packet");
                return;
            }

            wumppkt.HANDOFF handoff = new wumppkt.HANDOFF(replybuf);
            int newport = handoff.newport();
            System.err.println("Handoff received; new port is " + newport);

            ack = new wumppkt.ACK(0);
            ackDG.setData(ack.write());
            ackDG.setLength(ack.size());
            ackDG.setPort(newport);

            try {
                s.send(ackDG);
            } catch (IOException ioe) {
                System.err.println("Send() failed for ACK[0]");
                return;
            }

            // ====== MAIN LOOP ======
            long lastValidPacketTime = System.currentTimeMillis(); // Track time of last valid packet

            while (!fileTransferComplete) {
                try {
                    s.receive(replyDG);
                } catch (IOException ioe) {
                    System.err.println("Receive() failed");
                    return;
                }

                replybuf = replyDG.getData();
                proto = wumppkt.proto(replybuf);
                opcode = wumppkt.opcode(replybuf);
                length = replyDG.getLength();
                srcport = replyDG.getPort();

                data = null;
                error = null;
                blocknum = 0;

                if (proto == THEPROTO && opcode == wumppkt.DATAop && length >= wumppkt.DHEADERSIZE) {
                    data = new wumppkt.DATA(replybuf, length);
                    blocknum = data.blocknum();
                } else if (proto == THEPROTO && opcode == wumppkt.ERRORop && length >= wumppkt.EHEADERSIZE) {
                    error = new wumppkt.ERROR(replybuf);
                    System.err.println("Error received: Code " + error.errcode());
                    continue;
                }

                // Handle invalid packets
                if (data == null || srcport != newport) {
                    if (srcport != newport) {
                        System.err.println("Packet from incorrect port: " + srcport);
                        wumppkt.ERROR errorPkt = new wumppkt.ERROR(wumppkt.EBADPORT);
                        DatagramPacket errorDG = new DatagramPacket(
                                errorPkt.write(), errorPkt.size(), replyDG.getAddress(), srcport);
                        try {
                            s.send(errorDG);
                        } catch (IOException ioe) {
                            System.err.println("Send() failed for ERROR packet");
                        }
                    }
                    continue;
                }

                // Handle valid DATA packet
                if (blocknum == expected_block) {
                    System.err.println("Processing DATA packet with blocknum = " + blocknum);
                    System.out.write(data.bytes(), 0, data.size() - wumppkt.DHEADERSIZE);

                    // Send ACK for the current block
                    ack = new wumppkt.ACK(blocknum);
                    ackDG.setData(ack.write());
                    ackDG.setLength(ack.size());
                    ackDG.setPort(newport);

                    try {
                        s.send(ackDG);
                        System.err.println("Sent ACK for blocknum = " + blocknum);
                    } catch (IOException ioe) {
                        System.err.println("Send() failed for ACK[" + blocknum + "]");
                        return;
                    }

                    // Update the last valid packet time
                    lastValidPacketTime = System.currentTimeMillis();

                    // If this is the last block (size < MAXDATASIZE), mark transfer as complete
                    if (data.size() < wumppkt.MAXDATASIZE) {
                        System.err.println("End of file reached after blocknum = " + blocknum);
                        fileTransferComplete = true;
                        break; // Exit the main loop
                    }

                    // Increment expected_block for the next packet
                    expected_block++;
                } else {
                    // Ignore duplicate or out-of-sequence DATA packets
                    System.err.println("Ignoring duplicate or out-of-sequence DATA packet with blocknum = " + blocknum);

                    // Check elapsed time to avoid indefinite processing
                    if (System.currentTimeMillis() - lastValidPacketTime > 10000) { // 10-second threshold
                        System.err.println("No progress for 10 seconds. Exiting.");
                        break;
                    }
                }
            }

            // ====== DALLYING ======
            if (fileTransferComplete) {
                System.err.println("Entering dallying phase to handle duplicate final DATA packets...");
                long dallyStart = System.currentTimeMillis();
                boolean duplicatesHandled = false;

                while (System.currentTimeMillis() - dallyStart < 10000) { // 10-second dallying
                    try {
                        s.receive(replyDG);
                        replybuf = replyDG.getData();
                        srcport = replyDG.getPort();
                        proto = wumppkt.proto(replybuf);
                        opcode = wumppkt.opcode(replybuf);

                        if (srcport == newport && proto == THEPROTO && opcode == wumppkt.DATAop) {
                            System.err.println("Duplicate final DATA received. Resending final ACK.");
                            s.send(ackDG); // Resend the final ACK
                            duplicatesHandled = true;
                        }
                    } catch (SocketTimeoutException e) {
                        // Timeout during dallying is fine; just continue waiting
                        if (!duplicatesHandled) {
                            System.err.println("No duplicates received. Dallying period ending.");
                            break;
                        }
                    } catch (IOException ioe) {
                        System.err.println("Receive() failed during dallying");
                        break;
                    }
                }
                System.err.println("Dallying phase complete. Closing connection.");
            }
        } catch (SocketException se) {
            System.err.println("No socket available");
        } finally {
            if (s != null && !s.isClosed()) {
                s.close();
            }
        }
    }
}

// version handels spray and dupdata2 test case

