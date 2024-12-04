/*
    WUMP (specifically HUMP) in Java
 */
import java.lang.*;
import java.net.*;
import java.io.*;

public class wclient {

    static public void main(String args[]) {
        int srcport;
        int destport = wumppkt.SERVERPORT;
        String filename = "vanilla";
        String desthost = "ulam.cs.luc.edu";
        int winsize = 1;
        short THEPROTO = wumppkt.HUMPPROTO;
        wumppkt.setproto(THEPROTO);

        if (args.length > 0) filename = args[0];
        if (args.length > 1) winsize = Integer.parseInt(args[1]);
        if (args.length > 2) desthost = args[2];

        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            try {
                s.setSoTimeout(wumppkt.INITTIMEOUT); // timeout in milliseconds
            } catch (SocketException se) {
                System.err.println("Socket exception: timeout not set!");
            }

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
            try {
                s.receive(replyDG);
            } catch (SocketTimeoutException ste) {
                System.err.println("Hard timeout waiting for HANDOFF");
                return;
            } catch (IOException ioe) {
                System.err.println("Receive() failed");
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
            while (true) {
                try {
                    s.receive(replyDG);
                } catch (SocketTimeoutException ste) {
                    System.err.println("Timeout occurred. Retransmitting last ACK[" + (expected_block - 1) + "]");
                    try {
                        s.send(ackDG);
                    } catch (IOException ioe) {
                        System.err.println("Retransmission of ACK failed.");
                        return;
                    }
                    continue;
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

                printInfo(replyDG, data, starttime);

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

                System.out.write(data.bytes(), 0, data.size() - wumppkt.DHEADERSIZE);

                if (data.size() < wumppkt.MAXDATASIZE) {
                    System.err.println("End of file reached.");
                    break;
                }

                ack = new wumppkt.ACK(expected_block);
                ackDG.setData(ack.write());
                ackDG.setLength(ack.size());
                ackDG.setPort(newport);

                try {
                    s.send(ackDG);
                } catch (IOException ioe) {
                    System.err.println("Send() failed for ACK[" + expected_block + "]");
                    return;
                }

                expected_block++;
            }
        } catch (SocketException se) {
            System.err.println("No socket available");
        } finally {
            if (s != null && !s.isClosed()) {
                s.close();
            }
        }
    }

    static public void printInfo(DatagramPacket pkt, wumppkt.DATA data, long starttime) {
        byte[] replybuf = pkt.getData();
        int proto = wumppkt.proto(replybuf);
        int opcode = wumppkt.opcode(replybuf);
        int length = replybuf.length;

        System.err.print("Received packet: len=" + length);
        System.err.print("; proto=" + proto);
        System.err.print("; opcode=" + opcode);
        System.err.print("; src=(" + pkt.getAddress().getHostAddress() + "/" + pkt.getPort() + ")");
        System.err.print("; time=" + (System.currentTimeMillis() - starttime));
        System.err.println();

        if (data == null)
            System.err.println("         Packet does not seem to be a data packet");
        else
            System.err.println("         DATA packet blocknum = " + data.blocknum());
    }
}
